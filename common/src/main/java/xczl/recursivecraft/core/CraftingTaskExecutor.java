package xczl.recursivecraft.core;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import xczl.recursivecraft.data.CraftingTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 核心合成任务执行器
 * 统一管理 Command 和 Packet 的合成逻辑。
 */
public class CraftingTaskExecutor {

    /**
     * 尝试执行递归合成任务
     *
     * @param player         执行合成的玩家
     * @param targetItem     目标物品
     * @param amount         目标数量
     * @param forcedRecipeId (可选) 强制使用的配方ID，如果为 null 则自动寻路
     * @param msgSender      消息回调接口 (用于发送成功/失败/日志消息)
     * @return 是否成功执行
     */
    public static boolean tryExecute(ServerPlayer player, Item targetItem, int amount, ResourceLocation forcedRecipeId, Consumer<Component> msgSender) {
        // 1. 基础校验
        if (targetItem == Items.AIR || amount <= 0) {
            msgSender.accept(Component.literal("§c合成请求无效。"));
            return false;
        }

        if (!CraftingPlanner.isReady) {
            msgSender.accept(Component.literal("§c合成系统正在初始化，请稍候..."));
            return false;
        }

        // 2. 解析强制配方 (如果存在)
        CraftingRecipe forcedRecipe = null;
        if (forcedRecipeId != null) {
            Optional<? extends Recipe<?>> recipeOpt = player.level().getRecipeManager().byKey(forcedRecipeId);
            // 校验：必须存在，且必须是 CraftingRecipe，且产物必须匹配目标物品
            if (recipeOpt.isPresent() && recipeOpt.get() instanceof CraftingRecipe cr) {
                // 这里加一个产物校验，防止客户端发来不匹配的配方ID
                if (cr.getResultItem(player.level().registryAccess()).getItem() == targetItem) {
                    forcedRecipe = cr;
                } else {
                    msgSender.accept(Component.literal("§c配方产物不匹配：" + forcedRecipeId));
                    return false;
                }
            } else {
                msgSender.accept(Component.literal("§c指定的配方无效或不存在：" + forcedRecipeId));
                return false;
            }
        }

        // 3. 计算事务
        TransactionCalculator calculator = new TransactionCalculator(player.getInventory());
        // 传入 forcedRecipe (可能为 null)
        CraftingTransaction transaction = calculator.calculate(targetItem, amount, true, forcedRecipe);

        // 加上最终产物 (Calculator 默认只计算消耗)
        transaction.addProvide(targetItem, amount);

        // 4. 获取净变化 (Net Deltas)
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

        // 5. 打印调试日志
        printDebugLog(msgSender, netNeeds, netProvides);

        // 6. [死循环防御补丁]
        // 检查：目标物品是否真的在“净产出”里？
        int actualProvide = netProvides.getOrDefault(targetItem, 0);
        if (actualProvide < amount) {
            msgSender.accept(Component.literal("§c合成失败：缺乏基础原料或配方存在死循环。"));
            msgSender.accept(Component.literal("§7(系统检测到净产出无效，请检查是否拥有该物品的最基础原料)"));
            return false;
        }

        // 7. 检查背包材料是否足够
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
            StringBuilder message = new StringBuilder("§c缺少材料: ");
            for (Map.Entry<Item, Integer> missing : missingMaterials.entrySet()) {
                message.append(missing.getValue())
                        .append("x ")
                        .append(missing.getKey().getDescription().getString())
                        .append(", ");
            }
            msgSender.accept(Component.literal(message.substring(0, message.length() - 2)));
            return false;
        }

        // 8. 执行合成 (扣除材料，发放物品)
        try {
            transaction.execute(player);
            msgSender.accept(Component.literal("§a合成成功: " + amount + "x " + targetItem.getDescription().getString()));
            return true;
        } catch (Exception e) {
            msgSender.accept(Component.literal("§c合成失败: " + e.getMessage()));
            return false;
        }
    }

    private static void printDebugLog(Consumer<Component> msgSender, Map<Item, Integer> netNeeds, Map<Item, Integer> netProvides) {
        msgSender.accept(Component.literal("§8--- [RecursiveCraft DEBUG] ---"));

        msgSender.accept(Component.literal("§7【净消耗 (Net Needs)】"));
        if (netNeeds.isEmpty()) {
            msgSender.accept(Component.literal("  (无)"));
        } else {
            netNeeds.forEach((item, itemAmount) ->
                    msgSender.accept(Component.literal("  - " + itemAmount + "x " + item.getDescription().getString()))
            );
        }

        msgSender.accept(Component.literal("§7【净产出 (Net Provides)】"));
        if (netProvides.isEmpty()) {
            msgSender.accept(Component.literal("  (无)"));
        } else {
            netProvides.forEach((item, itemAmount) ->
                    msgSender.accept(Component.literal("  - " + itemAmount + "x " + item.getDescription().getString()))
            );
        }
        msgSender.accept(Component.literal("§8---------------------------------"));
    }
}