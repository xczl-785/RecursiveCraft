// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/network/C2SExecuteCraftPacket.java
// (无需修改, 日志已移至 TransactionCalculator.calculate)
// ======================================================================
package xczl.recursivecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import xczl.recursivecraft.RecursiveCraft; // <<< *** 确保导入 ***
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.core.TransactionCalculator;
import xczl.recursivecraft.data.CraftingTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class C2SExecuteCraftPacket {

    private final Item targetItem;
    private final int amount;

    // (构造函数, encode, decode 不变)
    public C2SExecuteCraftPacket(Item targetItem, int amount) {
        this.targetItem = (targetItem == null || targetItem == Items.AIR) ? Items.AIR : targetItem;
        this.amount = (this.targetItem == Items.AIR) ? 0 : amount;
    }

    public void encode(FriendlyByteBuf buf) {
        if (this.targetItem == Items.AIR || this.amount == 0) {
            buf.writeId(BuiltInRegistries.ITEM, Items.AIR);
            buf.writeInt(0);
        } else {
            buf.writeId(BuiltInRegistries.ITEM, targetItem);
            buf.writeInt(amount);
        }
    }

    public static C2SExecuteCraftPacket decode(FriendlyByteBuf buf) {
        Item item = buf.readById(BuiltInRegistries.ITEM);
        int amount = buf.readInt();
        return new C2SExecuteCraftPacket(item, amount);
    }

    // 处理 (在服务器端执行)
    public static void handle(C2SExecuteCraftPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            Item targetItem = pkt.targetItem;
            int amount = pkt.amount;

            if (targetItem == Items.AIR || amount <= 0) {
                player.sendSystemMessage(Component.literal("合成请求无效 (未选择物品或数量为0)。"));
                return;
            }

            if (!CraftingPlanner.isReady) {
                player.sendSystemMessage(Component.literal(
                        "[RecursiveCraft] 合成规划器仍在启动中，请稍后几秒再试..."
                ));
                return;
            }

            // <<< [关键] START/END 日志现在在 calculate() 内部触发 >>>
            TransactionCalculator calculator = new TransactionCalculator(player.getInventory());
            CraftingTransaction transaction = calculator.calculate(targetItem, amount, true);

            transaction.addProvide(targetItem, amount);

            // [净日志] (这部分不变)
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
                netNeeds.forEach((item, itemAmount) -> {
                    player.sendSystemMessage(Component.literal(String.format("  - %dx %s", itemAmount, item.getDescription().getString())));
                });
            }
            player.sendSystemMessage(Component.literal("【净产出 (Net Provides)】"));
            if (netProvides.isEmpty()) {
                player.sendSystemMessage(Component.literal("  (无)"));
            } else {
                netProvides.forEach((item, itemAmount) -> {
                    player.sendSystemMessage(Component.literal(String.format("  - %dx %s", itemAmount, item.getDescription().getString())));
                });
            }
            player.sendSystemMessage(Component.literal("---------------------------------"));

            // (检查 和 执行 逻辑不变)
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
            try {
                transaction.execute(player);
                player.sendSystemMessage(
                        Component.literal(String.format("合成成功: %dx %s", amount, targetItem.getDescription().getString()))
                );
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("合成失败，发生未知错误: " + e.getMessage()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}