package xczl.recursivecraft.core;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.data.CostMap;
import xczl.recursivecraft.data.CraftingTransaction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 合成事务计算器 (战术执行者 - DFS 修复版).
 * <p>
 * 修复日志:
 * 1. 修正了配方成功判定逻辑 (isTransactionSatisfied)。
 * 之前错误地要求 netNeeds 为空，导致消耗基础材料(如原木)的配方被误判为失败。
 * 现在只要库存消耗量 >= 需求量，即视为成功。
 */
public class TransactionCalculator {

    private static class IngredientNeed {
        public final Ingredient ingredient;
        public int amount;

        public IngredientNeed(Ingredient ing, int amt) {
            this.ingredient = ing;
            this.amount = amt;
        }
    }

    private final Map<Item, CraftingRecipe> pathMemo;
    private final Map<Item, CostMap> costMemo;
    private final Inventory playerInventory;

    private final Set<Item> uncraftableCache = new HashSet<>();
    private static final int MAX_DEPTH = 30;

    public TransactionCalculator(Inventory playerInventory) {
        this.playerInventory = playerInventory;
        this.pathMemo = CraftingPlanner.getInstance().getPathMemo();
        this.costMemo = CraftingPlanner.getInstance().getCostMemo();
    }

    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget) {
        // 1. 初始化虚拟库存
        Map<Item, Integer> virtualInventory = new HashMap<>();
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (!stack.isEmpty()) {
                virtualInventory.put(stack.getItem(), virtualInventory.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
        }

        RecursiveCraft.LOGGER.info("--- [CALCULATION START (DFS Mode)] ---");
        RecursiveCraft.LOGGER.info("Target: {} x{}", target.getDescription().getString(), amount);

        Set<Item> recursionStack = new HashSet<>();
        this.uncraftableCache.clear();

        CraftingTransaction result = calculateRecursive(target, amount, isFinalTarget, virtualInventory, 1, recursionStack);

        RecursiveCraft.LOGGER.info("--- [CALCULATION END] ---");
        return result;
    }

    // ======================================================================
    // 核心逻辑层级 1: 递归入口与库存管理
    // ======================================================================

    private CraftingTransaction calculateRecursive(Item target, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth, Set<Item> recursionStack) {
        String indent = "  ".repeat(debugDepth);
        CraftingTransaction currentTransaction = new CraftingTransaction();

        // 1. 守卫检查
        if (debugDepth > MAX_DEPTH) {
            RecursiveCraft.LOGGER.warn("{}!! Max Depth Reached for {} !!", indent, target.getDescription().getString());
            currentTransaction.addNeed(target, amount);
            return currentTransaction;
        }
        if (recursionStack.contains(target)) {
            RecursiveCraft.LOGGER.warn("{}!! Cycle Detected for {} !!", indent, target.getDescription().getString());
            currentTransaction.addNeed(target, amount);
            return currentTransaction;
        }
        if (uncraftableCache.contains(target)) {
            currentTransaction.addNeed(target, amount);
            return currentTransaction;
        }

        recursionStack.add(target);
        try {
            // 2. 优先消耗库存
            int amountInInventory = 0;
            if (!isFinalTarget) {
                amountInInventory = virtualInventory.getOrDefault(target, 0);
                if (amountInInventory > 0) {
                    int amountToConsume = Math.min(amount, amountInInventory);
                    currentTransaction.addNeed(target, amountToConsume);
                    virtualInventory.put(target, amountInInventory - amountToConsume);
                }
            }

            // 3. 计算仍需合成
            int amountToCraft = amount - amountInInventory;
            if (amountToCraft <= 0) {
                return currentTransaction;
            }

            // 4. 委托决策层
            CraftingTransaction craftTx = findAndApplyBestRecipe(target, amountToCraft, virtualInventory, debugDepth, recursionStack);

            // 5. 判定失败缓存
            if (shouldCacheAsFailure(target, craftTx, amountToCraft)) {
                uncraftableCache.add(target);
            }

            currentTransaction.merge(craftTx);
            return currentTransaction;

        } finally {
            recursionStack.remove(target);
        }
    }

    // ======================================================================
    // 核心逻辑层级 2: 配方决策与快照模拟
    // ======================================================================

    private CraftingTransaction findAndApplyBestRecipe(Item target, int amountToCraft, Map<Item, Integer> virtualInventory, int debugDepth, Set<Item> recursionStack) {
        String indent = "  ".repeat(debugDepth);

        // 注意：此处需要 CraftingPlanner.getRecipesFor
        List<CraftingRecipe> candidates = new ArrayList<>(CraftingPlanner.getInstance().getRecipesFor(target));

        if (candidates.isEmpty()) {
            // 这是一个基础物品（无配方），或者确实缺配方
            // 只有当它是我们要找的目标配方时才打印 Log，避免递归中大量 Base Item 刷屏
            // RecursiveCraft.LOGGER.debug("{}-> No Recipe for {}. Missing: {}", indent, target.getDescription().getString(), amountToCraft);
            CraftingTransaction tx = new CraftingTransaction();
            tx.addNeed(target, amountToCraft);
            return tx;
        }

        // 排序优化
        CraftingRecipe theoreticalBest = pathMemo.get(target);
        candidates.sort((r1, r2) -> {
            boolean s1 = checkShallowRecipe(r1, virtualInventory);
            boolean s2 = checkShallowRecipe(r2, virtualInventory);
            if (s1 && !s2) return -1;
            if (!s1 && s2) return 1;
            if (r1 == theoreticalBest) return -1;
            if (r2 == theoreticalBest) return 1;
            return 0;
        });

        CraftingTransaction bestFailure = null;

        for (CraftingRecipe recipe : candidates) {
            // [快照]
            Map<Item, Integer> snapshotInventory = new HashMap<>(virtualInventory);

            // [模拟]
            CraftingTransaction trialTx = simulateRecipe(recipe, amountToCraft, snapshotInventory, debugDepth, recursionStack);

            // [判定] 关键修复：检查需求是否被库存满足
            if (isTransactionSatisfied(trialTx, virtualInventory, snapshotInventory)) {
                // >>> 成功 <<<
                RecursiveCraft.LOGGER.info("{}   [Decision] Selected recipe for {}", indent, target.getDescription().getString());
                virtualInventory.putAll(snapshotInventory); // Commit
                return trialTx;
            } else {
                // >>> 失败 <<<
                if (bestFailure == null || recipe == theoreticalBest) {
                    bestFailure = trialTx;
                }
            }
        }

        return bestFailure != null ? bestFailure : new CraftingTransaction();
    }

    // ======================================================================
    // 核心逻辑层级 3: 单个配方执行模拟
    // ======================================================================

    private CraftingTransaction simulateRecipe(CraftingRecipe recipe, int amountToCraft, Map<Item, Integer> virtualInventory, int debugDepth, Set<Item> recursionStack) {
        CraftingTransaction tx = new CraftingTransaction();

        int outputCount = recipe.getResultItem(null).getCount();
        if (outputCount <= 0) outputCount = 1;
        int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);

        Map<String, IngredientNeed> aggregatedNeeds = new HashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            String key = ingredient.toString();
            aggregatedNeeds.computeIfAbsent(key, k -> new IngredientNeed(ingredient, 0)).amount++;
        }

        List<IngredientNeed> needsList = new ArrayList<>(aggregatedNeeds.values());
        needsList.sort(Comparator.comparingInt(n -> n.ingredient.getItems().length));

        for (IngredientNeed need : needsList) {
            int totalNeed = need.amount * recipeRuns;
            CraftingTransaction subTx = resolveIngredient(need.ingredient, totalNeed, virtualInventory, debugDepth + 1, recursionStack);
            tx.merge(subTx);
        }

        int totalProduced = recipeRuns * outputCount;
        int extra = totalProduced - amountToCraft;
        if (extra > 0) {
            Item outputItem = recipe.getResultItem(null).getItem();
            tx.addProvide(outputItem, extra);
            virtualInventory.put(outputItem, virtualInventory.getOrDefault(outputItem, 0) + extra);
        }

        return tx;
    }

    // ======================================================================
    // 核心逻辑层级 4: 原料/Tag 解析
    // ======================================================================

    private CraftingTransaction resolveIngredient(Ingredient ingredient, int totalNeed, Map<Item, Integer> virtualInventory, int debugDepth, Set<Item> recursionStack) {
        ItemStack[] options = ingredient.getItems();
        if (options.length == 0) return new CraftingTransaction();

        if (options.length == 1) {
            return calculateRecursive(options[0].getItem(), totalNeed, false, virtualInventory, debugDepth, recursionStack);
        }

        // 多选项 Tag 处理
        List<Item> sortedOptions = Arrays.stream(options)
                .map(ItemStack::getItem)
                .filter(item -> item != Items.AIR)
                .sorted((i1, i2) -> {
                    int c1 = virtualInventory.getOrDefault(i1, 0);
                    int c2 = virtualInventory.getOrDefault(i2, 0);
                    if (c1 > 0 && c2 == 0) return -1;
                    if (c1 == 0 && c2 > 0) return 1;
                    double cost1 = getCost(i1);
                    double cost2 = getCost(i2);
                    return Double.compare(cost1, cost2);
                })
                .collect(Collectors.toList());

        CraftingTransaction bestFailure = null;

        for (Item itemOption : sortedOptions) {
            Map<Item, Integer> snapshotInventory = new HashMap<>(virtualInventory);

            CraftingTransaction trialTx = calculateRecursive(itemOption, totalNeed, false, snapshotInventory, debugDepth, recursionStack);

            // [判定]
            if (isTransactionSatisfied(trialTx, virtualInventory, snapshotInventory)) {
                virtualInventory.putAll(snapshotInventory); // Commit
                return trialTx;
            } else {
                if (bestFailure == null) bestFailure = trialTx;
            }
        }

        return bestFailure != null ? bestFailure : new CraftingTransaction();
    }

    // ======================================================================
    // 关键修复：成功判定逻辑
    // ======================================================================

    /**
     * 判断一个事务产生的 Net Needs 是否已经被库存消耗所覆盖。
     * @param tx 产生的事务 (包含 Needs)
     * @param startInv 模拟开始时的库存
     * @param endInv 模拟结束时的库存 (已被扣减)
     * @return true 如果所有需求都通过库存扣减得到了满足
     */
    private boolean isTransactionSatisfied(CraftingTransaction tx, Map<Item, Integer> startInv, Map<Item, Integer> endInv) {
        Map<Item, Integer> netDeltas = tx.getNetDeltas();

        for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
            Item item = entry.getKey();
            int delta = entry.getValue(); // 负数代表需求

            // 我们只关心需求 (Needs)
            if (delta >= 0) continue;

            int amountNeeded = -delta; // 需要多少 (e.g., 2)

            // 计算库存实际消耗了多少
            int startCount = startInv.getOrDefault(item, 0);
            int endCount = endInv.getOrDefault(item, 0);
            int consumed = startCount - endCount; // e.g., 10 - 8 = 2

            // 如果 消耗量 < 需求量，说明不仅吃光了库存，还有缺口 -> 失败
            if (consumed < amountNeeded) {
                return false;
            }
        }

        return true;
    }

    // ======================================================================
    // 辅助方法
    // ======================================================================

    private double getCost(Item item) {
        CostMap map = costMemo.get(item);
        return map != null ? map.getTotalItemCost() : Double.MAX_VALUE;
    }

    private boolean checkShallowRecipe(CraftingRecipe recipe, Map<Item, Integer> virtualInventory) {
        if (recipe == null) return false;
        for (Ingredient subIngredient : recipe.getIngredients()) {
            if (subIngredient.isEmpty()) continue;
            boolean hasSubMat = false;
            for (ItemStack stack : subIngredient.getItems()) {
                if (virtualInventory.getOrDefault(stack.getItem(), 0) > 0) {
                    hasSubMat = true;
                    break;
                }
            }
            if (!hasSubMat) return false;
        }
        return true;
    }

    private boolean shouldCacheAsFailure(Item target, CraftingTransaction result, int amountRequested) {
        Map<Item, Integer> netDeltas = result.getNetDeltas();
        int deficit = -netDeltas.getOrDefault(target, 0);
        return deficit >= amountRequested;
    }
}