package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.core.CraftingTaskExecutor;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;

import java.util.function.Supplier;

public class C2SExecuteCraftPacket {

    private final Item targetItem;
    private final int amount;
    private final @Nullable ResourceLocation forcedRecipeId;
    private final @Nullable TargetOutputSpec targetOutputSpec;

    public C2SExecuteCraftPacket(Item targetItem, int amount) {
        this(targetItem, amount, null, null);
    }

    public C2SExecuteCraftPacket(Item targetItem, int amount, @Nullable ResourceLocation forcedRecipeId) {
        this(targetItem, amount, forcedRecipeId, null);
    }

    public C2SExecuteCraftPacket(Item targetItem, int amount, @Nullable ResourceLocation forcedRecipeId,
                                 @Nullable TargetOutputSpec targetOutputSpec) {
        this.targetItem = targetItem == null ? Items.AIR : targetItem;
        this.amount = this.targetItem == Items.AIR ? 0 : amount;
        this.forcedRecipeId = forcedRecipeId;
        this.targetOutputSpec = targetOutputSpec;
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

    public void encode(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, targetItem);
        buf.writeInt(amount);
        buf.writeBoolean(forcedRecipeId != null);
        if (forcedRecipeId != null) {
            buf.writeResourceLocation(forcedRecipeId);
        }
        buf.writeBoolean(targetOutputSpec != null);
        if (targetOutputSpec != null) {
            buf.writeId(BuiltInRegistries.ITEM, targetOutputSpec.item());
            CompoundTag tag = targetOutputSpec.tag();
            buf.writeBoolean(tag != null);
            if (tag != null) {
                buf.writeNbt(tag);
            }
        }
    }

    public static C2SExecuteCraftPacket decode(FriendlyByteBuf buf) {
        Item item = buf.readById(BuiltInRegistries.ITEM);
        int amount = buf.readInt();
        ResourceLocation recipeId = null;
        if (buf.readBoolean()) {
            recipeId = buf.readResourceLocation();
        }

        TargetOutputSpec targetOutputSpec = null;
        if (buf.isReadable() && buf.readBoolean()) {
            Item targetOutputItem = buf.readById(BuiltInRegistries.ITEM);
            CompoundTag tag = null;
            if (buf.readBoolean()) {
                tag = buf.readNbt();
            }
            targetOutputSpec = new TargetOutputSpec(targetOutputItem, tag);
        }

        return new C2SExecuteCraftPacket(item, amount, recipeId, targetOutputSpec);
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
                    player::sendSystemMessage
            );
        });
    }

    private static boolean hasConflictingTargetItem(C2SExecuteCraftPacket pkt) {
        return pkt.targetOutputSpec != null && pkt.targetItem != pkt.targetOutputSpec.item();
    }
}
