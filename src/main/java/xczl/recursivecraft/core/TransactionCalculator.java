// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/core/TransactionCalculator.java
// (V11 Final: 智能双重扫描 - 完美解决资源争抢与混合材质死锁)
// ======================================================================
package xczl.recursivecraft.core;

import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.data.CostMap;
import xczl.recursivecraft.data.CraftingTransaction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;
import java.util.stream.Collectors;

public class TransactionCalculator {

    private static class IngredientNeed {
        public Ingredient ingredient;
        public int amount;
        public IngredientNeed(Ingredient ing, int amt) { this.ingredient = ing; this.amount = amt; }
    }

    private final Map<Item, CraftingRecipe> pathMemo;
    private final Map<Item, CostMap> costMemo;
    private final Inventory playerInventory;

    public TransactionCalculator(Inventory playerInventory) {
        this.playerInventory = playerInventory;
        this.pathMemo = CraftingPlanner.getInstance().getPathMemo();
        this.costMemo = CraftingPlanner.getInstance().getCostMemo();
    }

    private String getIndent(int depth) { return "  ".repeat(depth); }

    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget) {
        Map<Item, Integer> virtualInventory = new HashMap<>();
        // 初始化虚拟库存
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (!stack.isEmpty()) {
                virtualInventory.put(stack.getItem(), virtualInventory.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
        }

        RecursiveCraft.LOGGER.info("--- [CALCULATION START] ---");
        RecursiveCraft.LOGGER.info("Target: {} x{}", target.getDescription().getString(), amount);

        Set<Item> recursionStack = new HashSet<>();
        CraftingTransaction result = calculateRecursive(target, amount, isFinalTarget, virtualInventory, 1, recursionStack);

        RecursiveCraft.LOGGER.info("--- [CALCULATION END] ---");
        return result;
    }

    // --- 辅助类 ---
    private static class ItemOptionScore {
        public final Item item;
        public final int score; // 0=库存, 1=浅层可做, 2=仅理论可做
        public final double cost;

        public ItemOptionScore(Item item, int score, double cost) {
            this.item = item;
            this.score = score;
            this.cost = cost;
        }
    }

    // --- 核心检测逻辑：检查当前虚拟库存是否满足配方的直接原料 ---
    private boolean checkShallowRecipe(CraftingRecipe recipe, Map<Item, Integer> virtualInventory) {
        if (recipe == null) return false;
        for (Ingredient subIngredient : recipe.getIngredients()) {
            if (subIngredient.isEmpty()) continue;
            boolean hasSubMat = false;
            for (Map.Entry<Item, Integer> entry : virtualInventory.entrySet()) {
                // 只要库存里有 > 0 且匹配原料的物品，就算满足
                if (entry.getValue() > 0 && subIngredient.test(new ItemStack(entry.getKey()))) {
                    hasSubMat = true;
                    break;
                }
            }
            if (!hasSubMat) return false;
        }
        return true;
    }

    // --- 递归计算 ---
    private CraftingTransaction calculateRecursive(Item target, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth, Set<Item> recursionStack) {
        String indent = getIndent(debugDepth);
        RecursiveCraft.LOGGER.info("{}Request: {} x{}", indent, target.getDescription().getString(), amount);

        CraftingTransaction currentTransaction = new CraftingTransaction();

        // [递归守卫]
        if (recursionStack.contains(target)) {
            RecursiveCraft.LOGGER.warn("{}!! Cycle Detected for {} !! Breaking.", indent, target.getDescription().getString());
            currentTransaction.addNeed(target, amount);
            return currentTransaction;
        }
        recursionStack.add(target);

        try {
            int amountInInventory = 0;

            // 1. 检查并消耗库存
            if (!isFinalTarget) {
                amountInInventory = virtualInventory.getOrDefault(target, 0);
                if (amountInInventory >= amount) {
                    RecursiveCraft.LOGGER.info("{}-> Stock: Using {} (Full)", indent, amount);
                    currentTransaction.addNeed(target, amount);
                    virtualInventory.put(target, amountInInventory - amount);
                    return currentTransaction;
                }
            }

            // 2. 计算仍需合成数量
            int amountToCraft = amount - amountInInventory;
            if (amountInInventory > 0) {
                RecursiveCraft.LOGGER.info("{}-> Stock: Using {} (Partial), Need Craft: {}", indent, amountInInventory, amountToCraft);
                currentTransaction.addNeed(target, amountInInventory);
                virtualInventory.put(target, 0);
            }

            // 3. 查找最优配方
            CraftingRecipe optimalRecipe = pathMemo.get(target);
            if (optimalRecipe == null) {
                RecursiveCraft.LOGGER.info("{}-> Base Item / No Recipe. Missing: {}", indent, amountToCraft);
                currentTransaction.addNeed(target, amountToCraft);
                return currentTransaction;
            }

            // 4. 解析配方
            int outputCount = optimalRecipe.getResultItem(null).getCount();
            int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);

            Map<String, IngredientNeed> aggregatedNeeds = new HashMap<>();
            for (Ingredient ingredient : optimalRecipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                String key = ingredient.toString();
                IngredientNeed entry = aggregatedNeeds.getOrDefault(key, new IngredientNeed(ingredient, 0));
                entry.amount += 1;
                aggregatedNeeds.put(key, entry);
            }

            List<IngredientNeed> sortedNeeds = new ArrayList<>(aggregatedNeeds.values());
            sortedNeeds.sort(Comparator.comparingInt(p -> p.ingredient.getItems().length));

            // 5. 递归解决原料
            for (IngredientNeed entry : sortedNeeds) {
                int totalInputAmount = entry.amount * recipeRuns;
                CraftingTransaction subTransaction = resolveIngredientRecursive(entry.ingredient, totalInputAmount, false, virtualInventory, debugDepth + 1, recursionStack);
                currentTransaction.merge(subTransaction);
            }

            // 6. 处理副产物
            int totalProvided = recipeRuns * outputCount;
            int leftover = totalProvided - amountToCraft;
            if (leftover > 0) {
                currentTransaction.addProvide(target, leftover);
                virtualInventory.put(target, virtualInventory.getOrDefault(target, 0) + leftover);
            }

            return currentTransaction;

        } finally {
            recursionStack.remove(target);
        }
    }

    // --- 原料解析 (Tag 处理核心) ---
    private CraftingTransaction resolveIngredientRecursive(Ingredient ingredient, int amountNeeded, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth, Set<Item> recursionStack) {
        String indent = getIndent(debugDepth);
        ItemStack[] matchingStacks = ingredient.getItems();
        if (matchingStacks.length == 0) return new CraftingTransaction();

        if (matchingStacks.length == 1) {
            return calculateRecursive(matchingStacks[0].getItem(), amountNeeded, isFinalTarget, virtualInventory, debugDepth + 1, recursionStack);
        }

        // 1. 初始化评分
        List<ItemOptionScore> scoredOptions = new ArrayList<>();
        for (ItemStack stackOption : matchingStacks) {
            Item itemOption = stackOption.getItem();
            if (itemOption == Items.AIR) continue;

            // 基础评分逻辑 (仅用于初始排序)
            int currentScore = 2;
            CostMap optionCostMap = this.costMemo.get(itemOption);
            if (optionCostMap == null) optionCostMap = new CostMap(itemOption, 1.0);

            if (optionCostMap.isInfinite()) continue; // 排除死循环配方

            if (virtualInventory.getOrDefault(itemOption, 0) > 0) {
                currentScore = 0; // 库存优先
            } else if (checkShallowRecipe(this.pathMemo.get(itemOption), virtualInventory)) {
                currentScore = 1; // 浅层可做
            }
            scoredOptions.add(new ItemOptionScore(itemOption, currentScore, optionCostMap.getTotalItemCost()));
        }

        // 初始排序: 库存 > 浅层 > 理论成本
        scoredOptions.sort(Comparator.comparingInt((ItemOptionScore o) -> o.score).thenComparingDouble(o -> o.cost));

        CraftingTransaction currentTransaction = new CraftingTransaction();
        int remainingAmount = amountNeeded;

        // 2. Phase 1: 贪心消耗库存 (只处理 score=0)
        for (ItemOptionScore option : scoredOptions) {
            if (option.score > 0) continue;
            if (remainingAmount == 0) break;

            int amountInStock = virtualInventory.getOrDefault(option.item, 0);
            int amountToConsume = Math.min(remainingAmount, amountInStock);

            if (amountToConsume > 0) {
                RecursiveCraft.LOGGER.info("{}-> Tag Choice (Stock): {} x{}", indent, option.item.getDescription().getString(), amountToConsume);
                currentTransaction.addNeed(option.item, amountToConsume);
                virtualInventory.put(option.item, amountInStock - amountToConsume);
                remainingAmount -= amountToConsume;
            }
        }

        if (remainingAmount == 0) return currentTransaction;

        // 3. Phase 2: 智能合成剩余部分 (Smart Craft)
        Item bestItemToCraft = Items.AIR;

        // [关键策略 A]: 优先寻找“即时可合成”的物品
        // 无论它之前是 Score 0 还是 1，只要现在背包里有原料，它就是最好的选择。
        // 这解决了木斧问题 (优先用有的橡木原木) 和 死锁问题 (橡木原木没了就用云杉原木)。
        for (ItemOptionScore option : scoredOptions) {
            if (checkShallowRecipe(this.pathMemo.get(option.item), virtualInventory)) {
                bestItemToCraft = option.item;
                RecursiveCraft.LOGGER.info("{}   [Smart Select] Found ready-to-craft candidate: {}", indent, bestItemToCraft.getDescription().getString());
                break;
            }
        }

        // [关键策略 B]: 保底选择 (Fallback)
        // 如果所有选项都缺原材料 (比如都没原木)，则退而求其次，找一个“理论上能做”的 (pathMemo有记录)。
        // 这样 calculateRecursive 进去后会报“缺少材料”，而不是无声失败。
        if (bestItemToCraft == Items.AIR) {
            for (ItemOptionScore option : scoredOptions) {
                if (this.pathMemo.containsKey(option.item)) {
                    bestItemToCraft = option.item;
                    RecursiveCraft.LOGGER.info("{}   [Smart Select] Fallback to theoretical candidate: {}", indent, bestItemToCraft.getDescription().getString());
                    break;
                }
            }
        }

        // [关键策略 C]: 终极保底
        // 连配方都没有？那就选列表第一个，让系统报“缺少基础材料”。
        if (bestItemToCraft == Items.AIR && !scoredOptions.isEmpty()) {
            bestItemToCraft = scoredOptions.get(0).item;
        }

        if (bestItemToCraft != Items.AIR) {
            RecursiveCraft.LOGGER.info("{}-> Tag Choice (Craft): {} x{}", indent, bestItemToCraft.getDescription().getString(), remainingAmount);
            CraftingTransaction subTransaction = calculateRecursive(bestItemToCraft, remainingAmount, false, virtualInventory, debugDepth + 1, recursionStack);
            currentTransaction.merge(subTransaction);
        }

        return currentTransaction;
    }
}