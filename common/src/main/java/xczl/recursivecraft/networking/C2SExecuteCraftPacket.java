package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import xczl.recursivecraft.core.CraftingTaskExecutor;

import java.util.function.Supplier;

public class C2SExecuteCraftPacket {

    private final Item targetItem;
    private final int amount;
    private final ResourceLocation forcedRecipeId; // [新增] 指定配方ID

    // 默认构造器 (给 GUI 使用，保持自动)
    public C2SExecuteCraftPacket(Item targetItem, int amount) {
        this(targetItem, amount, null);
    }

    // 全参构造器 (给 JEI 使用，指定配方)
    public C2SExecuteCraftPacket(Item targetItem, int amount, ResourceLocation forcedRecipeId) {
        this.targetItem = (targetItem == null) ? Items.AIR : targetItem;
        this.amount = (this.targetItem == Items.AIR) ? 0 : amount;
        this.forcedRecipeId = forcedRecipeId;
    }

    // 编码
    public void encode(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, targetItem);
        buf.writeInt(amount);
        // 写入 Optional 的 ResourceLocation
        buf.writeBoolean(forcedRecipeId != null);
        if (forcedRecipeId != null) {
            buf.writeResourceLocation(forcedRecipeId);
        }
    }

    // 解码
    public static C2SExecuteCraftPacket decode(FriendlyByteBuf buf) {
        Item item = buf.readById(BuiltInRegistries.ITEM);
        int amount = buf.readInt();
        ResourceLocation recipeId = null;
        if (buf.readBoolean()) {
            recipeId = buf.readResourceLocation();
        }
        return new C2SExecuteCraftPacket(item, amount, recipeId);
    }

    // 处理
    public static void handle(C2SExecuteCraftPacket pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext ctx = ctxSupplier.get();

        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;

            // 调用核心执行器，传入配方ID
            CraftingTaskExecutor.tryExecute(
                    player,
                    pkt.targetItem,
                    pkt.amount,
                    pkt.forcedRecipeId,
                    player::sendSystemMessage // 回调给玩家
            );
        });
    }
}