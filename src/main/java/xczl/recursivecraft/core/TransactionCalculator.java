// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/core/TransactionCalculator.java
// (V8.2: 修复资源争抢 - 优先处理库存中已有的原料)
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 流程二：事务计算器。
 * (V8.1: 修复库存耗尽切换问题)
 * (V8.2: 修复资源争抢问题 - 优化处理顺序)
 */
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
    private String logMap(Map<Item, ? extends Number> map) {
        if (map == null || map.isEmpty()) { return "{}"; }
        return "{" + map.entrySet().stream()
                .map(e -> e.getKey().getDescription().getString() + ": " + e.getValue())
                .collect(Collectors.joining(", ")) + "}";
    }

    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget) {
        Map<Item, Integer> virtualInventory = new HashMap<>();
        // 初始化虚拟库存
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (!stack.isEmpty()) {
                virtualInventory.put(stack.getItem(), virtualInventory.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
        }
        RecursiveCraft.LOGGER.info("--- [RECURSIVE CRAFT DEBUG START] ---");
        RecursiveCraft.LOGGER.info("公共入口: calculate(Target: {}, Amount: {}, isFinal: {})", target.getDescription().getString(), amount, isFinalTarget);
        RecursiveCraft.LOGGER.info("初始虚拟库存: {}", logMap(virtualInventory));

        CraftingTransaction result = calculateRecursive(target, amount, isFinalTarget, virtualInventory, 1);

        RecursiveCraft.LOGGER.info("--- [RECURSIVE CRAFT DEBUG END] ---");
        return result;
    }

    // 内部递归包装
    private CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory) {
        return calculateRecursive(target, amount, isFinalTarget, virtualInventory, 1);
    }

    // ==================================================================
    // 辅助类
    // ==================================================================
    private static class ItemOptionScore {
        public final Item item;
        public final int score;
        public final double cost;

        public ItemOptionScore(Item item, int score, double cost) {
            this.item = item;
            this.score = score;
            this.cost = cost;
        }
        public int getScore() { return score; }
        public double getCost() { return cost; }
    }

    private boolean checkShallowRecipe(CraftingRecipe recipe, Map<Item, Integer> virtualInventory) {
        if (recipe == null) return false;
        for (Ingredient subIngredient : recipe.getIngredients()) {
            if (subIngredient.isEmpty()) continue;
            boolean hasSubMat = false;
            for (Map.Entry<Item, Integer> entry : virtualInventory.entrySet()) {
                if (entry.getValue() > 0 && subIngredient.test(new ItemStack(entry.getKey()))) {
                    hasSubMat = true;
                    break;
                }
            }
            if (!hasSubMat) return false;
        }
        return true;
    }


    // ==================================================================
    // calculateRecursive (核心递归)
    // ==================================================================
    private CraftingTransaction calculateRecursive(Item target, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth) {

        String indent = getIndent(debugDepth);
        RecursiveCraft.LOGGER.info("{}[L{}] === calculate(Target: {}, Amount: {}) ===", indent, debugDepth, target.getDescription().getString(), amount);
        RecursiveCraft.LOGGER.info("{}[L{}]   (isFinal: {}, 虚拟库存: {})", indent, debugDepth, isFinalTarget, logMap(virtualInventory));

        CraftingTransaction currentTransaction = new CraftingTransaction();
        int amountInInventory = 0;

        // --- 1. 检查虚拟库存 (消耗已有物品) ---
        if (!isFinalTarget) {
            amountInInventory = virtualInventory.getOrDefault(target, 0);
            if (amountInInventory >= amount) {
                RecursiveCraft.LOGGER.info("{}[L{}]   1a. 库存足够。从虚拟库存消耗 {}。", indent, debugDepth, amount);
                currentTransaction.addNeed(target, amount);
                virtualInventory.put(target, amountInInventory - amount);
                return currentTransaction;
            }
        } else {
            RecursiveCraft.LOGGER.info("{}[L{}]   1. 检查库存 (是最终目标): 跳过库存消耗。", indent, debugDepth);
        }

        // --- 2. 计算还需要合成多少 ---
        int amountToCraft = amount - amountInInventory;
        if (amountInInventory > 0) {
            RecursiveCraft.LOGGER.info("{}[L{}]   2. 库存不足。消耗所有 {}，仍需合成 {}。", indent, debugDepth, amountInInventory, amountToCraft);
            currentTransaction.addNeed(target, amountInInventory);
            virtualInventory.put(target, 0);
        } else {
            RecursiveCraft.LOGGER.info("{}[L{}]   2. 无库存。需合成 {}。", indent, debugDepth, amountToCraft);
        }

        // --- 3. 查找配方 ---
        CraftingRecipe optimalRecipe = pathMemo.get(target);
        if (optimalRecipe == null) {
            RecursiveCraft.LOGGER.info("{}[L{}]   3. 查找配方: 未找到 (基础材料)。", indent, debugDepth);
            currentTransaction.addNeed(target, amountToCraft);
            return currentTransaction;
        }

        // --- 5. 递归合成 ---
        int outputCount = optimalRecipe.getResultItem(null).getCount();
        int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);
        RecursiveCraft.LOGGER.info("{}[L{}]   3. 查找配方: 找到! (产出 {}). 需执行 {} 次.", indent, debugDepth, outputCount, recipeRuns);

        // 5.1. 聚合相同的原料
        Map<String, IngredientNeed> aggregatedNeeds = new HashMap<>();
        for (Ingredient ingredient : optimalRecipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            String key = ingredient.toString();
            IngredientNeed entry = aggregatedNeeds.getOrDefault(key, new IngredientNeed(ingredient, 0));
            entry.amount += 1;
            aggregatedNeeds.put(key, entry);
        }

        // 5.2. 排序 (V8.2 优化: 优先处理库存中已有的原料)
        List<IngredientNeed> sortedNeeds = new ArrayList<>(aggregatedNeeds.values());
        sortedNeeds.sort(Comparator
                // 规则 1: 特异性 (Specific) 优先。选项越少的越先处理 (Tag 选项多，排后面)
                .comparingInt((IngredientNeed p) -> p.ingredient.getItems().length)
                // 规则 2 (V8.2 新增): 库存优先。如果库存里有这个原料，优先处理它。
                // 这可以防止 "木棍" (可以乱用木板) 抢走 "深色橡木栅栏" (必须用深色木板) 的库存。
                .thenComparingInt(p -> {
                    for (ItemStack stack : p.ingredient.getItems()) {
                        if (virtualInventory.getOrDefault(stack.getItem(), 0) > 0) {
                            return -1; // 有库存，优先处理
                        }
                    }
                    return 1; // 无库存，后处理
                })
        );

        // 5.3. DFS 递归处理每个原料
        RecursiveCraft.LOGGER.info("{}[L{}]   5. (V8) 开始 DFS 递归...", indent, debugDepth);
        for (IngredientNeed entry : sortedNeeds) {
            Ingredient ingredient = entry.ingredient;
            int totalInputAmount = entry.amount * recipeRuns;

            RecursiveCraft.LOGGER.info("{}[L{}]   5g. (递归调用) -> resolveIngredient(Ing: {}, Amount: {})", indent, debugDepth, ingredient.toString(), totalInputAmount);

            CraftingTransaction subTransaction = resolveIngredientRecursive(ingredient, totalInputAmount, false, virtualInventory, debugDepth + 1);

            RecursiveCraft.LOGGER.info("{}[L{}]   5h. (递归返回) <- resolveIngredient", indent, debugDepth);
            currentTransaction.merge(subTransaction);
        }
        RecursiveCraft.LOGGER.info("{}[L{}]   5i. (V8) DFS 递归结束。", indent, debugDepth);

        // --- 6. 处理副产品 ---
        int totalProvided = recipeRuns * outputCount;
        int leftover = totalProvided - amountToCraft;
        if (leftover > 0) {
            RecursiveCraft.LOGGER.info("{}[L{}]   6a. 添加 {}x{} 到 Provides 和 虚拟库存。", indent, debugDepth, leftover, target.getDescription().getString());
            currentTransaction.addProvide(target, leftover);
            virtualInventory.put(target, virtualInventory.getOrDefault(target, 0) + leftover);
        }

        RecursiveCraft.LOGGER.info("{}[L{}]   === calculate 返回 (Needs: {}, Provides: {}) ===", indent, debugDepth, logMap(currentTransaction.getNeeds()), logMap(currentTransaction.getProvides()));
        return currentTransaction;
    }


    // ==================================================================
    // resolveIngredientRecursive (Tag 解析器)
    // ==================================================================

    private CraftingTransaction resolveIngredient(Ingredient ingredient, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory) {
        return resolveIngredientRecursive(ingredient, amount, isFinalTarget, virtualInventory, 1);
    }

    private CraftingTransaction resolveIngredientRecursive(Ingredient ingredient, int amountNeeded, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth) {

        String indent = getIndent(debugDepth);
        RecursiveCraft.LOGGER.info("{}[L{}] === (V8) resolveIngredient(Ing: {}, Amount: {}) ===", indent, debugDepth, ingredient.toString(), amountNeeded);
        RecursiveCraft.LOGGER.info("{}[L{}]   (isFinal: {}, 虚拟库存: {})", indent, debugDepth, isFinalTarget, logMap(virtualInventory));

        ItemStack[] matchingStacks = ingredient.getItems();
        if (matchingStacks.length == 0) {
            RecursiveCraft.LOGGER.info("{}[L{}]   (空原料, 提前返回)", indent, debugDepth);
            return new CraftingTransaction();
        }

        // --- 优化：非 Tag (简单物品) ---
        if (matchingStacks.length == 1) {
            Item simpleItem = matchingStacks[0].getItem();
            if (simpleItem == Items.AIR) return new CraftingTransaction();
            RecursiveCraft.LOGGER.info("{}[L{}]   1. (非Tag) 简单物品: {}. 直接转交 calculate", indent, debugDepth, simpleItem.getDescription().getString());
            return calculateRecursive(simpleItem, amountNeeded, isFinalTarget, virtualInventory, debugDepth + 1);
        }

        // --- V8 核心逻辑：Tag (或复杂) 原料 ---

        // --- 1. (BFS) 评分和排序所有选项 ---
        List<ItemOptionScore> scoredOptions = new ArrayList<>();
        for (ItemStack stackOption : matchingStacks) {
            Item itemOption = stackOption.getItem();
            if (itemOption == Items.AIR) continue;

            int currentScore = 2;
            double currentCost = Double.MAX_VALUE;

            CostMap optionCostMap = this.costMemo.get(itemOption);
            if (optionCostMap == null) optionCostMap = new CostMap(itemOption, 1.0);
            currentCost = optionCostMap.getTotalItemCost();

            if (optionCostMap.isInfinite()) {
                continue;
            }

            // 决策 0：库存优先
            if (virtualInventory.getOrDefault(itemOption, 0) > 0) {
                currentScore = 0;
            }
            // 决策 1：浅层检查
            else {
                if (checkShallowRecipe(this.pathMemo.get(itemOption), virtualInventory)) {
                    currentScore = 1;
                }
            }

            scoredOptions.add(new ItemOptionScore(itemOption, currentScore, currentCost));
        }

        // 排序
        scoredOptions.sort(Comparator.comparingInt(ItemOptionScore::getScore)
                .thenComparingDouble(ItemOptionScore::getCost));

        if (!scoredOptions.isEmpty()) {
            RecursiveCraft.LOGGER.info("{}[L{}]   -- 最佳选项: {} (Score={})", indent, debugDepth, scoredOptions.get(0).item.getDescription().getString(), scoredOptions.get(0).score);
        }


        // --- 2. (BFS) 阶段一: 贪心消耗库存 (所有 Score 0) ---
        RecursiveCraft.LOGGER.info("{}[L{}]   2. (V8 Phase 1) 开始 BFS 库存消耗...", indent, debugDepth);
        int remainingAmount = amountNeeded;
        CraftingTransaction currentTransaction = new CraftingTransaction();

        for (ItemOptionScore option : scoredOptions) {
            if (option.score > 0) continue; // 只处理 Score 0
            if (remainingAmount == 0) break;

            Item itemOption = option.item;
            int amountInStock = virtualInventory.getOrDefault(itemOption, 0);
            int amountToConsume = Math.min(remainingAmount, amountInStock);

            if (amountToConsume > 0) {
                RecursiveCraft.LOGGER.info("{}[L{}]   2a. -- (Score 0) 消耗 {}/{}x {}", indent, debugDepth, amountToConsume, amountInStock, itemOption.getDescription().getString());
                currentTransaction.addNeed(itemOption, amountToConsume);
                virtualInventory.put(itemOption, amountInStock - amountToConsume);
                remainingAmount -= amountToConsume;
            }
        }

        if (remainingAmount == 0) {
            RecursiveCraft.LOGGER.info("{}[L{}]   2b. (V8 Phase 1) 库存满足。", indent, debugDepth);
            return currentTransaction;
        }

        // --- 3. (DFS) 阶段二: 合成剩余部分 ---
        RecursiveCraft.LOGGER.info("{}[L{}]   3. (V8 Phase 2) 开始 DFS 合成... 仍需 {}", indent, debugDepth, remainingAmount);

        Item bestItemToCraft = Items.AIR;
        String decisionType = "N/A";

        // (V8.1 逻辑)
        if (!scoredOptions.isEmpty()) {
            ItemOptionScore bestOption = scoredOptions.get(0);
            bestItemToCraft = bestOption.item;
            decisionType = "P" + bestOption.score + " (最优解)";
        }

        if (bestItemToCraft == Items.AIR) {
            RecursiveCraft.LOGGER.warn("{}[L{}]   3c. (V8) 无法找到合成目标 (Tag 为空?)。", indent, debugDepth);
            return currentTransaction;
        }

        RecursiveCraft.LOGGER.info("{}[L{}]   3c. (V8) 最终决策 ({}): {}", indent, debugDepth, decisionType, bestItemToCraft.getDescription().getString());

        // --- 4. 递归调用 (委托) ---
        CraftingTransaction subTransaction = calculateRecursive(
                bestItemToCraft,
                remainingAmount,
                false,
                virtualInventory,
                debugDepth + 1
        );

        currentTransaction.merge(subTransaction);
        RecursiveCraft.LOGGER.info("{}[L{}]   === resolveIngredient 返回 (合并) ===", indent, debugDepth);
        return currentTransaction;
    }
}