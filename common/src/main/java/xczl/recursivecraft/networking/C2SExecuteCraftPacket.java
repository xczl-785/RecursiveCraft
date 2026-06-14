package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.core.CraftingTaskExecutor;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SExecuteCraftPacket {

    private final Item targetItem;
    private final int amount;
    private final @Nullable ResourceLocation forcedRecipeId;
    private final @Nullable TargetOutputSpec targetOutputSpec;
    private final @Nullable List<ItemStack> displayedIngredients;

    public C2SExecuteCraftPacket(Item targetItem, int amount) {
        this(targetItem, amount, null, null, null);
    }

    public C2SExecuteCraftPacket(Item targetItem, int amount, @Nullable ResourceLocation forcedRecipeId) {
        this(targetItem, amount, forcedRecipeId, null, null);
    }

    public C2SExecuteCraftPacket(Item targetItem, int amount, @Nullable ResourceLocation forcedRecipeId,
                                 @Nullable TargetOutputSpec targetOutputSpec) {
        this(targetItem, amount, forcedRecipeId, targetOutputSpec, null);
    }

    public C2SExecuteCraftPacket(Item targetItem, int amount, @Nullable ResourceLocation forcedRecipeId,
                                 @Nullable TargetOutputSpec targetOutputSpec,
                                 @Nullable List<ItemStack> displayedIngredients) {
        this.targetItem = targetItem == null ? Items.AIR : targetItem;
        this.amount = this.targetItem == Items.AIR ? 0 : amount;
        this.forcedRecipeId = forcedRecipeId;
        this.targetOutputSpec = targetOutputSpec;
        this.displayedIngredients = sanitizeDisplayedIngredients(displayedIngredients);
    }

    public Item targetItem() {
        return targetItem;
    }

    public int amount() {
        return amount;
    }

    public @Nullable ResourceLocation forcedRecipeId() {
        return forcedRecipeId;
    }

    public @Nullable TargetOutputSpec targetOutputSpec() {
        return targetOutputSpec;
    }

    public @Nullable List<ItemStack> displayedIngredients() {
        if (displayedIngredients == null) {
            return null;
        }
        List<ItemStack> copies = new ArrayList<>(displayedIngredients.size());
        for (ItemStack stack : displayedIngredients) {
            copies.add(stack.copy());
        }
        return List.copyOf(copies);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeById(BuiltInRegistries.ITEM::getId, targetItem);
        buf.writeInt(amount);
        buf.writeBoolean(forcedRecipeId != null);
        if (forcedRecipeId != null) {
            buf.writeResourceLocation(forcedRecipeId);
        }
        buf.writeBoolean(targetOutputSpec != null);
        if (targetOutputSpec != null) {
            ItemStack.STREAM_CODEC.encode(requireRegistryFriendlyByteBuf(buf), targetOutputSpec.toTemplateStack());
        }
        buf.writeBoolean(displayedIngredients != null);
        if (displayedIngredients != null) {
            buf.writeVarInt(displayedIngredients.size());
            RegistryFriendlyByteBuf registryBuf = requireRegistryFriendlyByteBuf(buf);
            for (ItemStack stack : displayedIngredients) {
                ItemStack.STREAM_CODEC.encode(registryBuf, stack);
            }
        }
    }

    public static C2SExecuteCraftPacket decode(FriendlyByteBuf buf) {
        Item item = buf.readById(BuiltInRegistries.ITEM::byId);
        int amount = buf.readInt();
        ResourceLocation recipeId = null;
        if (buf.readBoolean()) {
            recipeId = buf.readResourceLocation();
        }

        TargetOutputSpec targetOutputSpec = null;
        if (buf.isReadable() && buf.readBoolean()) {
            targetOutputSpec = TargetOutputSpec.fromStack(ItemStack.STREAM_CODEC.decode(requireRegistryFriendlyByteBuf(buf)));
        }

        List<ItemStack> displayedIngredients = null;
        if (buf.isReadable() && buf.readBoolean()) {
            int ingredientCount = buf.readVarInt();
            displayedIngredients = new ArrayList<>(ingredientCount);
            RegistryFriendlyByteBuf registryBuf = requireRegistryFriendlyByteBuf(buf);
            for (int i = 0; i < ingredientCount; i++) {
                displayedIngredients.add(ItemStack.STREAM_CODEC.decode(registryBuf));
            }
        }

        return new C2SExecuteCraftPacket(item, amount, recipeId, targetOutputSpec, displayedIngredients);
    }

    public static void handle(C2SExecuteCraftPacket pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext ctx = ctxSupplier.get();

        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) {
                return;
            }
            if (hasConflictingTargetItem(pkt)) {
                player.sendSystemMessage(Component.translatable("recursivecraft.msg.invalid_request"));
                return;
            }

            CraftingTaskExecutor.tryExecute(
                    player,
                    pkt.targetItem,
                    pkt.amount,
                    pkt.forcedRecipeId,
                    pkt.targetOutputSpec,
                    pkt.displayedIngredients,
                    player::sendSystemMessage
            );
        });
    }

    private static boolean hasConflictingTargetItem(C2SExecuteCraftPacket pkt) {
        return pkt.targetOutputSpec != null && pkt.targetItem != pkt.targetOutputSpec.item();
    }

    private static RegistryFriendlyByteBuf requireRegistryFriendlyByteBuf(FriendlyByteBuf buf) {
        if (buf instanceof RegistryFriendlyByteBuf registryBuf) {
            return registryBuf;
        }
        throw new IllegalStateException("C2SExecuteCraftPacket requires RegistryFriendlyByteBuf for component-aware serialization");
    }

    private static @Nullable List<ItemStack> sanitizeDisplayedIngredients(@Nullable List<ItemStack> displayedIngredients) {
        if (displayedIngredients == null || displayedIngredients.isEmpty()) {
            return null;
        }
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : displayedIngredients) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            copies.add(stack.copy());
        }
        return copies.isEmpty() ? null : List.copyOf(copies);
    }
}
