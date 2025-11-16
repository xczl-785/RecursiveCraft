// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/core/TransactionCalculator.java
// (已应用 "V8 - 统一 BFS/DFS 修复" 逻辑)
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
 * 流程二：事务计算器。(已应用 "V8 - 统一 BFS/DFS 修复")
 */
public class TransactionCalculator {

    // (IngredientNeed, 构造函数, 日志辅助, calculate (公共), calculate (包装器) ... 全部不变)
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
    private CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory) {
        return calculateRecursive(target, amount, isFinalTarget, virtualInventory, 1);
    }

    // ==================================================================
    // <<< [V8 修复] 辅助类：用于存储 Tag 选项的评分 >>>
    // ==================================================================
    /**
     * 辅助类，用于 V8 决策
     */
    private static class ItemOptionScore {
        public final Item item;
        public final int score; // 0=库存, 1=浅层, 2=理论
        public final double cost; // 理论成本

        public ItemOptionScore(Item item, int score, double cost) {
            this.item = item;
            this.score = score;
            this.cost = cost;
        }
        public int getScore() { return score; }
        public double getCost() { return cost; }
    }

    /**
     * V8 辅助函数：检查浅层合成
     */
    private boolean checkShallowRecipe(CraftingRecipe recipe, Map<Item, Integer> virtualInventory) {
        if (recipe == null) return false;

        for (Ingredient subIngredient : recipe.getIngredients()) {
            if (subIngredient.isEmpty()) continue;
            boolean hasSubMat = false;
            // 检查虚拟库存中 *任何* 物品是否匹配
            for (Map.Entry<Item, Integer> entry : virtualInventory.entrySet()) {
                if (entry.getValue() > 0 && subIngredient.test(new ItemStack(entry.getKey()))) {
                    hasSubMat = true;
                    break;
                }
            }
            if (!hasSubMat) {
                return false; // 缺少一个原料
            }
        }
        return true; // 所有原料都至少有一个匹配项
    }


    // ==================================================================
    // <<< [V8 修复] 简化 calculateRecursive >>>
    // ==================================================================
    /**
     * (流程二：私有递归核心 - calculateRecursive)
     * (V8: 移除了 V6 的 "顶层 BFS 消耗" 逻辑，以修复 "木栅栏 Bug")
     */
    private CraftingTransaction calculateRecursive(Item target, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth) {

        String indent = getIndent(debugDepth);
        RecursiveCraft.LOGGER.info("{}[L{}] === calculate(Target: {}, Amount: {}) ===", indent, debugDepth, target.getDescription().getString(), amount);
        RecursiveCraft.LOGGER.info("{}[L{}]   (isFinal: {}, 虚拟库存: {})", indent, debugDepth, isFinalTarget, logMap(virtualInventory));

        CraftingTransaction currentTransaction = new CraftingTransaction();
        int amountInInventory = 0;

        // --- 1. 检查虚拟库存 (消耗已有物品) (不变) ---
        if (!isFinalTarget) {
            amountInInventory = virtualInventory.getOrDefault(target, 0);
            RecursiveCraft.LOGGER.info("{}[L{}]   1. 检查库存 (非最终目标): 找到 {} / {}", indent, debugDepth, amountInInventory, amount);
            if (amountInInventory >= amount) {
                RecursiveCraft.LOGGER.info("{}[L{}]   1a. 库存足够。从虚拟库存消耗 {}。", indent, debugDepth, amount);
                currentTransaction.addNeed(target, amount);
                virtualInventory.put(target, amountInInventory - amount);
                RecursiveCraft.LOGGER.info("{}[L{}]   === calculate 返回 (Needs: {}, Provides: {}) ===", indent, debugDepth, logMap(currentTransaction.getNeeds()), logMap(currentTransaction.getProvides()));
                return currentTransaction;
            }
        } else {
            RecursiveCraft.LOGGER.info("{}[L{}]   1. 检查库存 (是最终目标): 跳过库存消耗。", indent, debugDepth);
        }

        // --- 2. 计算还需要合成多少 (不变) ---
        int amountToCraft = amount - amountInInventory;
        if (amountInInventory > 0) {
            RecursiveCraft.LOGGER.info("{}[L{}]   2. 库存不足。消耗所有 {}，仍需合成 {}。", indent, debugDepth, amountInInventory, amountToCraft);
            currentTransaction.addNeed(target, amountInInventory);
            virtualInventory.put(target, 0);
        } else {
            RecursiveCraft.LOGGER.info("{}[L{}]   2. 无库存。需合成 {}。", indent, debugDepth, amountToCraft);
        }

        // --- 3. 查找配方 (不变) ---
        CraftingRecipe optimalRecipe = pathMemo.get(target);
        if (optimalRecipe == null) {
            RecursiveCraft.LOGGER.info("{}[L{}]   3. 查找配方: 未找到 (基础材料)。", indent, debugDepth);
            // (注意：这里我们只 "Need" 我们需要合成的数量, inventory 消耗已在 步骤 2 处理)
            currentTransaction.addNeed(target, amountToCraft);
            RecursiveCraft.LOGGER.info("{}[L{}]   === calculate 返回 (Needs: {}, Provides: {}) ===", indent, debugDepth, logMap(currentTransaction.getNeeds()), logMap(currentTransaction.getProvides()));
            return currentTransaction;
        }

        // --- 5. 递归合成 (V8 简化版) ---
        int outputCount = optimalRecipe.getResultItem(null).getCount();
        int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);
        RecursiveCraft.LOGGER.info("{}[L{}]   3. 查找配方: 找到! (产出 {}). 需执行 {} 次.", indent, debugDepth, outputCount, recipeRuns);

        // 5.1. 聚合相同的原料 (不变)
        Map<String, IngredientNeed> aggregatedNeeds = new HashMap<>();
        for (Ingredient ingredient : optimalRecipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            String key = ingredient.toString();
            IngredientNeed entry = aggregatedNeeds.getOrDefault(key, new IngredientNeed(ingredient, 0));
            entry.amount += 1;
            aggregatedNeeds.put(key, entry);
        }
        RecursiveCraft.LOGGER.info("{}[L{}]   4. 原料聚合: {}", indent, debugDepth,
                aggregatedNeeds.values().stream()
                        .map(e -> e.amount + "x[" + e.ingredient.toString() + "]")
                        .collect(Collectors.joining(", ")));

        // 5.2. 排序 (不变, 尽管在 V8 中不那么重要了)
        List<IngredientNeed> sortedNeeds = new ArrayList<>(aggregatedNeeds.values());
        sortedNeeds.sort(Comparator.comparingInt(p -> p.ingredient.getItems().length));

        // <<< [V8 修复] 移除 V6 的 "Phase 1: 顶层 BFS 消耗" 逻辑 >>>
        // (这修复了 "木栅栏 Bug")

        // <<< [V8 修复] 简化 "Phase 2: DFS 递归" >>>
        // (现在我们遍历 *所有* 聚合后的原料)
        RecursiveCraft.LOGGER.info("{}[L{}]   5. (V8) 开始 DFS 递归...", indent, debugDepth);
        for (IngredientNeed entry : sortedNeeds) {
            Ingredient ingredient = entry.ingredient;
            // **重要**: entry.amount 是 *单次运行* 的需求, 我们需要总需求
            int totalInputAmount = entry.amount * recipeRuns;

            RecursiveCraft.LOGGER.info("{}[L{}]   5g. (递归调用) -> resolveIngredient(Ing: {}, Amount: {})", indent, debugDepth, ingredient.toString(), totalInputAmount);

            // (V8: 现在将调用 *新* 的 resolveIngredientRecursive)
            CraftingTransaction subTransaction = resolveIngredientRecursive(ingredient, totalInputAmount, false, virtualInventory, debugDepth + 1);

            RecursiveCraft.LOGGER.info("{}[L{}]   5h. (递归返回) <- resolveIngredient (Needs: {}, Provides: {})", indent, debugDepth, logMap(subTransaction.getNeeds()), logMap(subTransaction.getProvides()));
            currentTransaction.merge(subTransaction);
        }
        RecursiveCraft.LOGGER.info("{}[L{}]   5i. (V8) DFS 递归结束。", indent, debugDepth);
        // <<< [V8 修复 结束] >>>


        // --- 6. 处理副产品 (不变) ---
        int totalProvided = recipeRuns * outputCount;
        int leftover = totalProvided - amountToCraft;
        RecursiveCraft.LOGGER.info("{}[L{}]   6. 处理副产品: 总产出 {}, 本层需求 {}, 剩余 {}", indent, debugDepth, totalProvided, amountToCraft, leftover);
        if (leftover > 0) {
            RecursiveCraft.LOGGER.info("{}[L{}]   6a. 添加 {}x{} 到 Provides 和 虚拟库存。", indent, debugDepth, leftover, target.getDescription().getString());
            currentTransaction.addProvide(target, leftover);
            virtualInventory.put(target, virtualInventory.getOrDefault(target, 0) + leftover);
        }
        RecursiveCraft.LOGGER.info("{}[L{}]   === calculate 返回 (Needs: {}, Provides: {}) ===", indent, debugDepth, logMap(currentTransaction.getNeeds()), logMap(currentTransaction.getProvides()));
        return currentTransaction;
    }


    // ==================================================================
    // <<< [V8 修复] 重写 resolveIngredientRecursive >>>
    // ==================================================================

    /**
     * (resolveIngredient 包装器 不变)
     */
    private CraftingTransaction resolveIngredient(Ingredient ingredient, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory) {
        return resolveIngredientRecursive(ingredient, amount, isFinalTarget, virtualInventory, 1);
    }

    /**
     * (流程二：私有 Tag 解析器 - 真正的 Worker)
     * (V8: "按数量拆分" 的 BFS 消耗 + DFS 合成)
     * (这修复了 "木斧 Bug")
     */
    private CraftingTransaction resolveIngredientRecursive(Ingredient ingredient, int amountNeeded, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth) {

        String indent = getIndent(debugDepth);
        RecursiveCraft.LOGGER.info("{}[L{}] === (V8) resolveIngredient(Ing: {}, Amount: {}) ===", indent, debugDepth, ingredient.toString(), amountNeeded);
        RecursiveCraft.LOGGER.info("{}[L{}]   (isFinal: {}, 虚拟库存: {})", indent, debugDepth, isFinalTarget, logMap(virtualInventory));

        ItemStack[] matchingStacks = ingredient.getItems();
        if (matchingStacks.length == 0) {
            RecursiveCraft.LOGGER.info("{}[L{}]   (空原料, 提前返回)", indent, debugDepth);
            return new CraftingTransaction();
        }

        // --- 优化：非 Tag ---
        // (如果只有一个选项，V8 逻辑会自然处理，但我们可以跳过评分)
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

            // 默认 2 (理论)
            int currentScore = 2;
            double currentCost = Double.MAX_VALUE;

            // 决策 2：理论成本 (总要计算)
            CostMap optionCostMap = this.costMemo.get(itemOption);
            // (确保基础材料也有成本)
            if (optionCostMap == null) optionCostMap = new CostMap(itemOption, 1.0);
            currentCost = optionCostMap.getTotalItemCost();

            if (optionCostMap.isInfinite()) {
                RecursiveCraft.LOGGER.info("{}[L{}]   -- 检查选项 {}: 成本无限, 跳过.", indent, debugDepth, itemOption.getDescription().getString());
                continue; // 跳过无限成本的选项
            }

            // 决策 0：库存优先
            if (virtualInventory.getOrDefault(itemOption, 0) > 0) {
                currentScore = 0; // 升级为 0 (库存优先)
            }
            // 决策 1：浅层检查
            else {
                // (只有在 "库存" 中没有时，才考虑 "浅层")
                if (checkShallowRecipe(this.pathMemo.get(itemOption), virtualInventory)) {
                    currentScore = 1; // 升级为 1 (浅层检查)
                }
            }
            RecursiveCraft.LOGGER.info("{}[L{}]   -- 检查选项 {}: (Score={}, Cost={})", indent, debugDepth, itemOption.getDescription().getString(), currentScore, currentCost);
            scoredOptions.add(new ItemOptionScore(itemOption, currentScore, currentCost));
        }

        // 排序: Score 优先 (0 -> 2)，然后 Cost 优先 (低 -> 高)
        scoredOptions.sort(Comparator.comparingInt(ItemOptionScore::getScore)
                .thenComparingDouble(ItemOptionScore::getCost));


        // --- 2. (BFS) 阶段一: 贪心消耗库存 (所有 Score 0) ---
        RecursiveCraft.LOGGER.info("{}[L{}]   2. (V8 Phase 1) 开始 BFS 库存消耗...", indent, debugDepth);
        int remainingAmount = amountNeeded;
        CraftingTransaction currentTransaction = new CraftingTransaction();

        for (ItemOptionScore option : scoredOptions) {
            if (option.score > 0) continue; // 只处理 Score 0
            if (remainingAmount == 0) break; // 需求已满足

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
            RecursiveCraft.LOGGER.info("{}[L{}]   2b. (V8 Phase 1) 库存满足。 === resolveIngredient 返回 ===", indent, debugDepth);
            return currentTransaction; // 库存已满足所有需求
        }

        // --- 3. (DFS) 阶段二: 合成剩余部分 (选择最佳的 Score 1 或 2) ---
        RecursiveCraft.LOGGER.info("{}[L{}]   3. (V8 Phase 2) 开始 DFS 合成... 仍需 {}", indent, debugDepth, remainingAmount);

        Item bestItemToCraft = Items.AIR;
        String decisionType = "N/A";

        // 3a. 寻找最佳的 *非库存* 选项 (Score 1 或 2)
        for (ItemOptionScore option : scoredOptions) {
            if (option.score > 0) {
                bestItemToCraft = option.item;
                decisionType = (option.score == 1) ? "P1 - 浅层检查" : "P2 - 理论成本";
                break;
            }
        }

        // 3b. 回退 (如果没有非库存选项，但库存已空，则尝试使用最佳的 Score 0 物品)
        if (bestItemToCraft == Items.AIR && !scoredOptions.isEmpty()) {
            bestItemToCraft = scoredOptions.get(0).item;
            decisionType = "P0 - 回退 (库存已空)";
        }

        if (bestItemToCraft == Items.AIR) {
            RecursiveCraft.LOGGER.warn("{}[L{}]   3c. (V8) 无法找到合成目标 (Tag 为空?)。 === resolveIngredient 返回 (空) ===", indent, debugDepth);
            // (我们仍返回 currentTransaction，因为它可能已消耗了部分库存)
            return currentTransaction;
        }

        RecursiveCraft.LOGGER.info("{}[L{}]   3c. (V8) 最终决策 ({}): {}", indent, debugDepth, decisionType, bestItemToCraft.getDescription().getString());

        // --- 4. 递归调用 (委托) ---
        // 委托给 calculateRecursive 来合成 *剩余* 的数量
        CraftingTransaction subTransaction = calculateRecursive(
                bestItemToCraft,
                remainingAmount, // *不是* amountNeeded
                false, // 永远不是 isFinalTarget
                virtualInventory,
                debugDepth + 1
        );

        currentTransaction.merge(subTransaction);
        RecursiveCraft.LOGGER.info("{}[L{}]   === resolveIngredient 返回 (合并) ===", indent, debugDepth);
        return currentTransaction;
    }
}