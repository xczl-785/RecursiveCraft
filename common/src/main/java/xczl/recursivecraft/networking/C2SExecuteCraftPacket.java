package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.core.TransactionCalculator;
import xczl.recursivecraft.data.CraftingTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class C2SExecuteCraftPacket {

    private final Item targetItem;
    private final int amount;

    public C2SExecuteCraftPacket(Item targetItem, int amount) {
        this.targetItem = (targetItem == null) ? Items.AIR : targetItem;
        this.amount = (this.targetItem == Items.AIR) ? 0 : amount;
    }

    // 编码
    public void encode(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, targetItem);
        buf.writeInt(amount);
    }

    // 解码
    public static C2SExecuteCraftPacket decode(FriendlyByteBuf buf) {
        Item item = buf.readById(BuiltInRegistries.ITEM);
        int amount = buf.readInt();
        return new C2SExecuteCraftPacket(item, amount);
    }

    // *** 关键修复：参数类型改为 Supplier<NetworkManager.PacketContext> ***
    public static void handle(C2SExecuteCraftPacket pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext ctx = ctxSupplier.get();

        // 将操作放入主线程队列 (对应 Forge 的 enqueueWork)
        ctx.queue(() -> {
            // Architectury 获取发送者玩家
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;

            Item targetItem = pkt.targetItem;
            int amount = pkt.amount;

            // === 原始业务逻辑 ===

            if (targetItem == Items.AIR || amount <= 0) {
                player.sendSystemMessage(Component.literal("合成请求无效 (未选择物品或数量为0)。"));
                return;
            }

            if (!CraftingPlanner.isReady) {
                player.sendSystemMessage(Component.literal("[RecursiveCraft] 合成规划器仍在启动中，请稍后几秒再试..."));
                return;
            }

            // 计算事务
            TransactionCalculator calculator = new TransactionCalculator(player.getInventory());
            CraftingTransaction transaction = calculator.calculate(targetItem, amount, true);

            transaction.addProvide(targetItem, amount);

            // [DEBUG 日志]
            player.sendSystemMessage(Component.literal("--- [RecursiveCraft DEBUG] ---"));
            Map<Item, Integer> netDeltas = transaction.getNetDeltas();
            Map<Item, Integer> netNeeds = new HashMap<>();
            Map<Item, Integer> netProvides = new HashMap<>();

            for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
                if (entry.getValue() < 0) {
                    netNeeds.put(entry.getKey(), -entry.getValue());
                } else if (entry.getValue() > 0) {
                    netProvides.put(entry.getKey(), entry.getValue());
                }
            }

            player.sendSystemMessage(Component.literal("【净消耗 (Net Needs)】"));
            if (netNeeds.isEmpty()) {
                player.sendSystemMessage(Component.literal("  (无)"));
            } else {
                netNeeds.forEach((item, itemAmount) ->
                        player.sendSystemMessage(Component.literal("  - " + itemAmount + "x " + item.getDescription().getString()))
                );
            }

            player.sendSystemMessage(Component.literal("【净产出 (Net Provides)】"));
            if (netProvides.isEmpty()) {
                player.sendSystemMessage(Component.literal("  (无)"));
            } else {
                netProvides.forEach((item, itemAmount) ->
                        player.sendSystemMessage(Component.literal("  - " + itemAmount + "x " + item.getDescription().getString()))
                );
            }
            player.sendSystemMessage(Component.literal("---------------------------------"));

            // 检查材料是否足够
            Map<Item, Integer> missingMaterials = new HashMap<>();
            for (Map.Entry<Item, Integer> entry : netNeeds.entrySet()) {
                Item neededItem = entry.getKey();
                int neededAmount = entry.getValue();
                int amountInInventory = player.getInventory().countItem(neededItem);
                if (amountInInventory < neededAmount) {
                    missingMaterials.put(neededItem, neededAmount - amountInInventory);
                }
            }

            if (!missingMaterials.isEmpty()) {
                StringBuilder message = new StringBuilder("缺少材料: ");
                for (Map.Entry<Item, Integer> missing : missingMaterials.entrySet()) {
                    message.append(missing.getValue())
                            .append("x ")
                            .append(missing.getKey().getDescription().getString())
                            .append(", ");
                }
                player.sendSystemMessage(Component.literal(message.substring(0, message.length() - 2)));
                return;
            }

            // 执行合成
            try {
                transaction.execute(player);
                player.sendSystemMessage(Component.literal("合成成功: " + amount + "x " + targetItem.getDescription().getString()));
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("合成失败，发生未知错误: " + e.getMessage()));
            }
        });
    }
}