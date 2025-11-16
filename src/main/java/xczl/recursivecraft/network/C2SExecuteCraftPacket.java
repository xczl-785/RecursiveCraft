// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/network/C2SExecuteCraftPacket.java
// (已应用 "净变化" 修复 + "净日志" 修复)
// ======================================================================
package xczl.recursivecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
// import net.minecraftforge.registries.ForgeRegistries; // <<< (不再需要这个)
import net.minecraft.core.registries.BuiltInRegistries; // <<< *** 导入正确的注册表 ***
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.core.TransactionCalculator;
import xczl.recursivecraft.data.CraftingTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * C2S (客户端到服务器) 数据包
 * (已为 1.20.1 Forge 修正)
 * 当玩家在 GUI 中点击 "执行合成" 按钮时发送。
 */
public class C2SExecuteCraftPacket {

    private final Item targetItem;
    private final int amount;

    // *** 构造函数 修复 ***
    public C2SExecuteCraftPacket(Item targetItem, int amount) {
        // 1. 修复 Item 赋值
        this.targetItem = (targetItem == null || targetItem == Items.AIR) ? Items.AIR : targetItem;
        // 2. 修复 amount 赋值
        this.amount = (this.targetItem == Items.AIR) ? 0 : amount;
    }

    // 编码 (写入 ByteBuf)
    public void encode(FriendlyByteBuf buf) {
        if (this.targetItem == Items.AIR || this.amount == 0) {
            // *** 修复 ***
            // 使用 BuiltInRegistries.ITEM, 这是一个 IdMap
            buf.writeId(BuiltInRegistries.ITEM, Items.AIR);
            buf.writeInt(0);
        } else {
            // *** 修复 ***
            buf.writeId(BuiltInRegistries.ITEM, targetItem);
            buf.writeInt(amount);
        }
    }

    // 解码 (读回 ByteBuf)
    public static C2SExecuteCraftPacket decode(FriendlyByteBuf buf) {
        // *** 修复 ***
        Item item = buf.readById(BuiltInRegistries.ITEM);
        int amount = buf.readInt();
        return new C2SExecuteCraftPacket(item, amount);
    }

    // 处理 (在服务器端执行)
    public static void handle(C2SExecuteCraftPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        // 确保在服务器线程上执行
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            Item targetItem = pkt.targetItem;
            int amount = pkt.amount;

            // 检查无效数据包
            if (targetItem == Items.AIR || amount <= 0) {
                player.sendSystemMessage(Component.literal("合成请求无效 (未选择物品或数量为0)。"));
                return;
            }

            // === [移植自 RecursiveCraftCommand.run] ===

            if (!CraftingPlanner.isReady) { //
                player.sendSystemMessage(Component.literal(
                        "[RecursiveCraft] 合成规划器仍在启动中，请稍后几秒再试..."
                ));
                return;
            }

            TransactionCalculator calculator = new TransactionCalculator(player.getInventory()); //
            CraftingTransaction transaction = calculator.calculate(targetItem, amount, true); //
            transaction.addProvide(targetItem, amount); //


            // <<< [修复] [DEBUG 日志] (更新为显示“净”变化) >>>
            player.sendSystemMessage(Component.literal("--- [RecursiveCraft DEBUG] ---"));

            // 1. 获取净变化
            Map<Item, Integer> netDeltas = transaction.getNetDeltas();

            // 2. 分离 净消耗 和 净产出
            Map<Item, Integer> netNeeds = new HashMap<>();
            Map<Item, Integer> netProvides = new HashMap<>();
            for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
                if (entry.getValue() < 0) { // 负数 = 净消耗
                    netNeeds.put(entry.getKey(), -entry.getValue()); // 存为正数
                } else if (entry.getValue() > 0) { // 正数 = 净产出
                    netProvides.put(entry.getKey(), entry.getValue());
                }
            }

            // 3. 打印净消耗
            player.sendSystemMessage(Component.literal("【净消耗 (Net Needs)】"));
            if (netNeeds.isEmpty()) {
                player.sendSystemMessage(Component.literal("  (无)"));
            } else {
                netNeeds.forEach((item, itemAmount) -> { // (变量名 'amount' 避免冲突)
                    player.sendSystemMessage(Component.literal(String.format("  - %dx %s", itemAmount, item.getDescription().getString())));
                });
            }

            // 4. 打印净产出
            player.sendSystemMessage(Component.literal("【净产出 (Net Provides)】"));
            if (netProvides.isEmpty()) {
                player.sendSystemMessage(Component.literal("  (无)"));
            } else {
                netProvides.forEach((item, itemAmount) -> { // (变量名 'amount' 避免冲突)
                    player.sendSystemMessage(Component.literal(String.format("  - %dx %s", itemAmount, item.getDescription().getString())));
                });
            }
            player.sendSystemMessage(Component.literal("---------------------------------"));
            // <<< [DEBUG 结束] >>>


            // (5. 检查“净需求” - 逻辑不变, 使用 netNeeds)
            Map<Item, Integer> missingMaterials = new HashMap<>();
            // (我们复用上面日志计算出的 netNeeds)
            for (Map.Entry<Item, Integer> entry : netNeeds.entrySet()) {
                Item neededItem = entry.getKey();
                int neededAmount = entry.getValue();
                int amountInInventory = player.getInventory().countItem(neededItem);
                if (amountInInventory < neededAmount) { //
                    missingMaterials.put(neededItem, neededAmount - amountInInventory);
                }
            }

            if (!missingMaterials.isEmpty()) { //
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
                transaction.execute(player); //
                player.sendSystemMessage(
                        Component.literal(String.format("合成成功: %dx %s", amount, targetItem.getDescription().getString()))
                );
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("合成失败，发生未知错误: " + e.getMessage()));
            }
            // === [移植结束] ===
        });

        // 告诉 Forge 我们已经处理了这个数据包
        ctx.get().setPacketHandled(true);
    }
}