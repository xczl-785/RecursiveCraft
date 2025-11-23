package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import xczl.recursivecraft.RecursiveCraft;
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

    // 处理
    public static void handle(C2SExecuteCraftPacket pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext ctx = ctxSupplier.get();

        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;

            Item targetItem = pkt.targetItem;
            int amount = pkt.amount;

            // 1. 基础校验
            if (targetItem == Items.AIR || amount <= 0) {
                player.sendSystemMessage(Component.literal("合成请求无效。"));
                return;
            }

            if (!CraftingPlanner.isReady) {
                player.sendSystemMessage(Component.literal("合成系统正在初始化，请稍候..."));
                return;
            }

            // 2. 计算事务
            TransactionCalculator calculator = new TransactionCalculator(player.getInventory());
            CraftingTransaction transaction = calculator.calculate(targetItem, amount, true);

            // 加上最终产物
            transaction.addProvide(targetItem, amount);

            // 3. 获取净变化 (用于后续校验和日志)
            Map<Item, Integer> netDeltas = transaction.getNetDeltas();
            Map<Item, Integer> netNeeds = new HashMap<>();
            Map<Item, Integer> netProvides = new HashMap<>();

            // 分离需求和产出
            for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
                if (entry.getValue() < 0) {
                    netNeeds.put(entry.getKey(), -entry.getValue());
                } else if (entry.getValue() > 0) {
                    netProvides.put(entry.getKey(), entry.getValue());
                }
            }

            // ================== [日志区域 START] ==================
            // 还原了之前的详细日志，方便玩家和调试
            player.sendSystemMessage(Component.literal("--- [RecursiveCraft DEBUG] ---"));

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
            // ================== [日志区域 END] ==================


            // ================== [死循环防御补丁] ==================
            // 检查：经过一系列计算后，目标物品是否真的“产出”了？
            // 如果发生死循环（如铁锭->铁块->铁锭），计算器会请求1个铁锭并产出1个铁锭，导致净产出为0。
            // 这意味着玩家没有付出基础原料，却想获得成品。
            int actualProvide = netProvides.getOrDefault(targetItem, 0);
            if (actualProvide < amount) {
                player.sendSystemMessage(Component.literal("§c合成失败：缺乏基础原料或配方存在死循环。"));
                player.sendSystemMessage(Component.literal("§7(系统检测到净产出无效，请检查是否拥有该物品的最基础原料)"));
                return;
            }
            // ====================================================


            // 4. 检查背包材料是否足够
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

            // 5. 执行合成 (扣除材料，发放物品)
            try {
                transaction.execute(player);
                player.sendSystemMessage(Component.literal("合成成功: " + amount + "x " + targetItem.getDescription().getString()));
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("合成失败: " + e.getMessage()));
            }
        });
    }
}