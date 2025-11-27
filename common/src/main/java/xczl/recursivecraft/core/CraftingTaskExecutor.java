package xczl.recursivecraft.core;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import xczl.recursivecraft.data.CraftingTransaction;

import java.util.*;
import java.util.function.Consumer;

/**
 * 核心合成任务执行器
 * 统一管理 Command 和 Packet 的合成逻辑。
 */
public class CraftingTaskExecutor {

    /**
     * 尝试执行递归合成任务
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

        // 2. 解析配方
        CraftingRecipe usedRecipe = null;
        if (forcedRecipeId != null) {
            Optional<? extends Recipe<?>> opt = player.level().getRecipeManager().byKey(forcedRecipeId);
            if (opt.isPresent() && opt.get() instanceof CraftingRecipe cr) {
                if (cr.getResultItem(player.level().registryAccess()).getItem() == targetItem) {
                    usedRecipe = cr;
                }
            }
        }
        if (usedRecipe == null) {
            usedRecipe = CraftingPlanner.getInstance().getPathMemo().get(targetItem);
        }

        // 3. 计算事务
        TransactionCalculator calculator = new TransactionCalculator(player.getInventory());
        CraftingRecipe recipeForCalc = (forcedRecipeId != null) ? usedRecipe : null;

        CraftingTransaction transaction = calculator.calculate(targetItem, amount, true, recipeForCalc);
        transaction.addProvide(targetItem, amount);

        // 4. 获取净变化 (Net Deltas)
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

        // === [优化] 先进行逻辑校验，通过后再打印日志 ===
        // 这样做是为了防止打印“废案”的日志误导玩家。如果失败，我们只看诊断结果。

        // 5. [检查一] 死循环防御：净产出是否达标？
        int actualProvide = netProvides.getOrDefault(targetItem, 0);
        if (actualProvide < amount) {
            // 失败：进入智能诊断 (此时不打印 Transaction Log)
            reportMissingMaterials(player, amount, usedRecipe, msgSender);
            return false;
        }

        // 6. [检查二] 基础材料是否充足？
        boolean isMaterialsEnough = true;
        for (Map.Entry<Item, Integer> entry : netNeeds.entrySet()) {
            if (player.getInventory().countItem(entry.getKey()) < entry.getValue()) {
                isMaterialsEnough = false;
                break;
            }
        }

        if (!isMaterialsEnough) {
            // 失败：进入智能诊断 (此时不打印 Transaction Log)
            reportMissingMaterials(player, amount, usedRecipe, msgSender);
            return false;
        }

        // === 只有成功时，才打印详细的事务日志 ===
        printDebugLog(msgSender, netNeeds, netProvides);

        // 7. 执行合成
        try {
            transaction.execute(player);
            msgSender.accept(Component.literal("§a合成成功: " + amount + "x " + targetItem.getDescription().getString()));
            return true;
        } catch (Exception e) {
            msgSender.accept(Component.literal("§c合成失败: " + e.getMessage()));
            return false;
        }
    }

    /**
     * [简化版] 智能缺失材料报告
     * 逻辑：仅进行"贪婪匹配"，不尝试递归合成。
     * 直接对比 [配方需求] 和 [当前背包]，报出第一层缺口。
     */
    private static void reportMissingMaterials(ServerPlayer player, int amount, CraftingRecipe recipe, Consumer<Component> msgSender) {
        if (recipe == null) {
            msgSender.accept(Component.literal("§c无法合成：未找到有效配方。"));
            return;
        }

        msgSender.accept(Component.literal("§e[分析合成失败原因...]"));

        // 1. 模拟背包快照
        Map<Item, Integer> virtualInv = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty()) {
                virtualInv.put(s.getItem(), virtualInv.getOrDefault(s.getItem(), 0) + s.getCount());
            }
        }

        // 2. 展开所有需求
        int outputCount = recipe.getResultItem(player.level().registryAccess()).getCount();
        if (outputCount < 1) outputCount = 1;
        int crafts = (int) Math.ceil((double) amount / outputCount);

        List<Ingredient> allIngredients = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) {
                for (int i = 0; i < crafts; i++) {
                    allIngredients.add(ing);
                }
            }
        }

        // 3. 贪婪匹配
        Map<String, Integer> missingCounts = new HashMap<>();

        for (Ingredient ing : allIngredients) {
            boolean satisfied = false;
            ItemStack[] options = ing.getItems();

            // 尝试在背包里找现货
            for (ItemStack option : options) {
                Item item = option.getItem();
                int has = virtualInv.getOrDefault(item, 0);
                if (has > 0) {
                    virtualInv.put(item, has - 1);
                    satisfied = true;
                    break;
                }
            }

            if (!satisfied) {
                // 取个名字 (通常取第一个匹配项)
                String name = (options.length > 0) ? options[0].getHoverName().getString() : "未知材料";
                missingCounts.put(name, missingCounts.getOrDefault(name, 0) + 1);
            }
        }

        // 4. 输出报告
        if (missingCounts.isEmpty()) {
            msgSender.accept(Component.literal("§c合成结构异常 (可能是配方死循环)。"));
        } else {
            StringBuilder sb = new StringBuilder("§c缺少材料: ");
            missingCounts.forEach((name, count) -> {
                sb.append(count).append("x ").append(name).append(", ");
            });
            msgSender.accept(Component.literal(sb.substring(0, sb.length() - 2)));
        }
    }

    private static void printDebugLog(Consumer<Component> msgSender, Map<Item, Integer> netNeeds, Map<Item, Integer> netProvides) {
        msgSender.accept(Component.literal("§8--- [RecursiveCraft Transaction] ---"));

        // 打印消耗
        if (!netNeeds.isEmpty()) {
            msgSender.accept(Component.literal("§7消耗 (Consumes):"));
            netNeeds.forEach((item, itemAmount) ->
                    msgSender.accept(Component.literal("  - " + itemAmount + "x " + item.getDescription().getString()))
            );
        }

        // 打印产出
        if (!netProvides.isEmpty()) {
            msgSender.accept(Component.literal("§7产出 (Produces):"));
            netProvides.forEach((item, itemAmount) ->
                    msgSender.accept(Component.literal("  - " + itemAmount + "x " + item.getDescription().getString()))
            );
        }
        msgSender.accept(Component.literal("§8---------------------------------"));
    }
}