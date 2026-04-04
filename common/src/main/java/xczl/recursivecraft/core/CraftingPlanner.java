package xczl.recursivecraft.core;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.registries.BuiltInRegistries; // 使用原版注册表
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.data.CostMap;

import java.util.*;

/**
 * 合成规划器 (战略规划者).
 * <p>
 * 该类的核心职责是在游戏加载时，通过分析所有合成配方，为游戏中每一个物品计算出其最优的合成路径和最小的理论"成本"。
 * 它扮演着"战略家"的角色，预先计算好所有可能性，为后续的实时合成计算提供数据支持。
 * <p>
 * 算法核心：
 * 1. 采用类似贝尔曼-福特（Bellman-Ford）的迭代松弛算法，通过多轮迭代使所有物品的成本收敛到最小值。
 * 2. 引入"合成成本惩罚 (RECIPE_COST_PENALTY)"机制，为每次合成操作附加一个微小的成本，有效打破了等价配方之间的无限合成循环（例如：A(成本1.0) <-> B(成本1.0)）。
 * 3. 采用多阶段计算（收敛-拯救-终结），以处理复杂的循环依赖和孤岛物品，确保成本计算的鲁棒性。
 */
public class CraftingPlanner {
    private static final CraftingPlanner INSTANCE = new CraftingPlanner();

    /**
     * 规划结果的不可变快照。通过 volatile 引用发布，保证线程安全。
     * 其他线程只需读取此引用即可安全访问所有规划数据。
     */
    public static final class PlanningResult {
        public static final PlanningResult EMPTY = new PlanningResult(
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        private final Map<Item, CraftingRecipe> pathMemo;
        private final Map<Item, CostMap> costMemo;
        private final Map<Item, List<CraftingRecipe>> recipeLookup;

        private PlanningResult(Map<Item, CraftingRecipe> pathMemo,
                               Map<Item, CostMap> costMemo,
                               Map<Item, List<CraftingRecipe>> recipeLookup) {
            this.pathMemo = Collections.unmodifiableMap(pathMemo);
            this.costMemo = Collections.unmodifiableMap(costMemo);
            // recipeLookup 的 value (List) 也需要包装为不可变
            Map<Item, List<CraftingRecipe>> unmodifiableLookup = new HashMap<>();
            recipeLookup.forEach((k, v) -> unmodifiableLookup.put(k, Collections.unmodifiableList(v)));
            this.recipeLookup = Collections.unmodifiableMap(unmodifiableLookup);
        }

        public Map<Item, CraftingRecipe> getPathMemo() { return pathMemo; }
        public Map<Item, CostMap> getCostMemo() { return costMemo; }
        public List<CraftingRecipe> getRecipesFor(Item item) {
            return recipeLookup.getOrDefault(item, Collections.emptyList());
        }
    }

    /** volatile 保证跨线程可见性：写入 result 之前的所有数据写入对读取线程可见 */
    private volatile PlanningResult result = PlanningResult.EMPTY;

    /**
     * 合成成本惩罚 (熵值).
     * 为每次合成操作增加一个固定的成本，确保合成链条越长，其理论成本越高。
     * 这是打破无损合成循环的关键机制。
     */
    private static final double RECIPE_COST_PENALTY = 0.1d;

    private CraftingPlanner() {
    }

    public static CraftingPlanner getInstance() {
        return INSTANCE;
    }

    /** 检查规划是否已完成 */
    public boolean isReady() {
        return result != PlanningResult.EMPTY;
    }

    /** 获取当前的规划结果快照（线程安全） */
    public PlanningResult getResult() {
        return result;
    }

    /**
     * 构建最优合成路径树。这是该类的主要入口点。
     * 所有中间状态均为方法局部变量，计算完成后通过 volatile 写入一次性发布不可变结果。
     *
     * @param recipeManager 配方管理器实例。
     */
    public void buildOptimalPathTree(RecipeManager recipeManager) {
        RecursiveCraft.LOGGER.info("RecursiveCraft: Building optimal path tree...");
        long startTime = System.currentTimeMillis();

        // 所有中间状态均为局部变量，不存在并发问题
        Map<Item, Double> minCostTable = new HashMap<>();
        Map<Item, CraftingRecipe> pathMemo = new HashMap<>();
        Map<Item, List<CraftingRecipe>> recipeLookup = new HashMap<>();

        Set<Item> allItems = indexRecipesAndCollectItems(recipeManager, recipeLookup);
        int baseCount = initializeBaseCosts(allItems, minCostTable, recipeLookup);
        RecursiveCraft.LOGGER.info("Phase 1: Initialized {} absolute base items.", baseCount);

        // 2. 第一轮收敛
        runConvergenceLoop(100, null, minCostTable, pathMemo, recipeLookup);

        // 3. 拯救阶段：识别并临时处理在第一轮中未能计算成本的"孤岛"物品
        Set<Item> itemsToRescue = findItemsToRescue(minCostTable, recipeLookup);
        applyRescueBaseCost(itemsToRescue, minCostTable);
        RecursiveCraft.LOGGER.info("Phase 2: Rescued {} items.", itemsToRescue.size());

        // 4. 第二轮收敛（终结阶段）
        runConvergenceLoop(100, itemsToRescue, minCostTable, pathMemo, recipeLookup);

        // 5. 生成最终结果并通过 volatile 写入一次性发布
        Map<Item, CostMap> costMemo = new HashMap<>();
        int craftableCount = finalizeCostMemo(allItems, minCostTable, pathMemo, costMemo);

        // volatile 写入：此前所有数据写入对读取线程可见
        this.result = new PlanningResult(pathMemo, costMemo, recipeLookup);

        long endTime = System.currentTimeMillis();
        RecursiveCraft.LOGGER.info("RecursiveCraft: Engine finished. Found {} craftable items in {}ms.", craftableCount, (endTime - startTime));
    }

    private static Set<Item> indexRecipesAndCollectItems(RecipeManager recipeManager,
                                                         Map<Item, List<CraftingRecipe>> recipeLookup) {
        List<CraftingRecipe> allRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        Set<Item> allItems = new HashSet<>();

        for (CraftingRecipe recipe : allRecipes) {
            if (recipe.isSpecial() || recipe.getResultItem(null).isEmpty()) continue;
            Item output = recipe.getResultItem(null).getItem();
            recipeLookup.computeIfAbsent(output, k -> new ArrayList<>()).add(recipe);
            allItems.add(output);
        }

        // BuiltInRegistries.ITEM 在 Forge 和 Fabric 下都可用 (通过 Mojang 映射)
        allItems.addAll(BuiltInRegistries.ITEM.stream().toList());
        return allItems;
    }

    private static int initializeBaseCosts(Set<Item> allItems, Map<Item, Double> minCostTable,
                                           Map<Item, List<CraftingRecipe>> recipeLookup) {
        for (Item item : allItems) minCostTable.put(item, Double.MAX_VALUE);

        int baseCount = 0;
        for (Item item : allItems) {
            if (!recipeLookup.containsKey(item)) {
                minCostTable.put(item, 1.0);
                baseCount++;
            }
        }
        return baseCount;
    }

    private static Set<Item> findItemsToRescue(Map<Item, Double> minCostTable,
                                               Map<Item, List<CraftingRecipe>> recipeLookup) {
        Set<Item> itemsToRescue = new HashSet<>();
        for (Map.Entry<Item, List<CraftingRecipe>> entry : recipeLookup.entrySet()) {
            for (CraftingRecipe recipe : entry.getValue()) {
                for (Ingredient ingredient : recipe.getIngredients()) {
                    for (ItemStack stack : ingredient.getItems()) {
                        Item inputItem = stack.getItem();
                        if (minCostTable.getOrDefault(inputItem, Double.MAX_VALUE) >= Double.MAX_VALUE) {
                            itemsToRescue.add(inputItem);
                        }
                    }
                }
            }
        }
        return itemsToRescue;
    }

    private static void applyRescueBaseCost(Set<Item> itemsToRescue, Map<Item, Double> minCostTable) {
        for (Item item : itemsToRescue) {
            minCostTable.put(item, 1.0);
        }
    }

    private static int finalizeCostMemo(Set<Item> allItems, Map<Item, Double> minCostTable,
                                        Map<Item, CraftingRecipe> pathMemo, Map<Item, CostMap> costMemo) {
        int craftableCount = 0;
        for (Item item : allItems) {
            double finalCost = minCostTable.getOrDefault(item, Double.MAX_VALUE);
            if (finalCost >= Double.MAX_VALUE) {
                costMemo.put(item, CostMap.INFINITE_COST);
            } else {
                costMemo.put(item, new CostMap(item, finalCost));
                if (pathMemo.containsKey(item)) {
                    craftableCount++;
                }
            }
        }
        return craftableCount;
    }

    /**
     * 运行成本收敛循环。
     *
     * @param maxIterations 最大迭代次数。
     * @param rescuedItems  一个可变的集合，包含被"拯救"的物品。在计算中，这些物品的成本将被优先更新。
     */
    private static void runConvergenceLoop(int maxIterations, Set<Item> rescuedItems,
                                           Map<Item, Double> minCostTable,
                                           Map<Item, CraftingRecipe> pathMemo,
                                           Map<Item, List<CraftingRecipe>> recipeLookup) {
        boolean changed = true;
        int iterations = 0;
        while (changed && iterations < maxIterations) {
            changed = false;
            iterations++;

            for (Map.Entry<Item, List<CraftingRecipe>> entry : recipeLookup.entrySet()) {
                Item target = entry.getKey();
                double currentBestCost = minCostTable.get(target);

                for (CraftingRecipe recipe : entry.getValue()) {
                    double recipeCost = calculateRecipeCost(recipe, minCostTable);

                    // 如果新成本更优，则更新成本和路径
                    // 对于被拯救的物品，即使成本没有严格变小，只要它从无穷大变为一个有效值，也进行更新
                    if (recipeCost < currentBestCost || (rescuedItems != null && rescuedItems.contains(target) && recipeCost < Double.MAX_VALUE)) {
                        minCostTable.put(target, recipeCost);
                        pathMemo.put(target, recipe);
                        currentBestCost = recipeCost;
                        changed = true;

                        // 如果更新了一个被拯救物品的成本，将其从集合中移除
                        if (rescuedItems != null) rescuedItems.remove(target);
                    }
                }
            }
        }
    }

    /**
     * 计算单个合成配方的成本。
     *
     * @param recipe 要计算的配方。
     * @return 配方的理论成本。
     */
    private static double calculateRecipeCost(CraftingRecipe recipe, Map<Item, Double> minCostTable) {
        double totalIngredientsCost = 0;

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length == 0) return Double.MAX_VALUE;

            // 对于使用物品标签（Tag）的原料，选择其中成本最低的物品
            double cheapestOption = Double.MAX_VALUE;
            for (ItemStack stack : stacks) {
                Double itemCost = minCostTable.get(stack.getItem());
                if (itemCost != null && itemCost < cheapestOption) {
                    cheapestOption = itemCost;
                }
            }

            if (cheapestOption >= Double.MAX_VALUE) return Double.MAX_VALUE;
            totalIngredientsCost += cheapestOption;
        }

        // 加上固定的加工成本惩罚，以打破循环
        totalIngredientsCost += RECIPE_COST_PENALTY;

        int outputCount = recipe.getResultItem(null).getCount();
        if (outputCount <= 0) outputCount = 1; // 防止除零

        return totalIngredientsCost / outputCount;
    }
}
