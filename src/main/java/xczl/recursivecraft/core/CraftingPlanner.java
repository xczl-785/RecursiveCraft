// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/core/CraftingPlanner.java
// (V9.3: 多阶段救援算法 - 完美修复金属/宝石类物品无法合成的问题)
// ======================================================================
package xczl.recursivecraft.core;

import net.minecraftforge.registries.ForgeRegistries;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.data.CostMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程一：合成规划器 (V9.3 Multi-Stage Rescue)。
 * 解决“死锁原料导致其下游产物（如工具）被误判为不可合成”的问题。
 */
public class CraftingPlanner {
    private static final CraftingPlanner INSTANCE = new CraftingPlanner();
    public static volatile boolean isReady = false;

    private final Map<Item, Double> minCostTable = new HashMap<>();
    private final Map<Item, CostMap> costMemo = new HashMap<>();
    private final Map<Item, CraftingRecipe> pathMemo = new HashMap<>();
    private final Map<Item, List<CraftingRecipe>> recipeLookup = new HashMap<>();

    private CraftingPlanner() {}
    public static CraftingPlanner getInstance() { return INSTANCE; }
    public Map<Item, CraftingRecipe> getPathMemo() { return pathMemo; }
    public Map<Item, CostMap> getCostMemo() { return costMemo; }

    // 调试关键词
    private static final List<String> DEBUG_KEYWORDS = List.of("ore", "ingot", "diamond", "pickaxe");
    private boolean isDebugItem(Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase();
        for (String keyword : DEBUG_KEYWORDS) if (id.contains(keyword)) return true;
        return false;
    }

    public void buildOptimalPathTree(RecipeManager recipeManager) {
        RecursiveCraft.LOGGER.info("RecursiveCraft: Building optimal path tree (Engine V9.3)...");
        long startTime = System.currentTimeMillis();

        isReady = false;
        minCostTable.clear();
        costMemo.clear();
        pathMemo.clear();
        recipeLookup.clear();

        // 1. 收集配方
        List<CraftingRecipe> allRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        Set<Item> allItems = new HashSet<>();

        for (CraftingRecipe recipe : allRecipes) {
            if (recipe.isSpecial() || recipe.getResultItem(null).isEmpty()) continue;
            Item output = recipe.getResultItem(null).getItem();
            recipeLookup.computeIfAbsent(output, k -> new ArrayList<>()).add(recipe);
            allItems.add(output);
        }
        allItems.addAll(ForgeRegistries.ITEMS.getValues());

        // 2. 初始化：默认无限，无配方物品设为 1.0
        for (Item item : allItems) minCostTable.put(item, Double.MAX_VALUE);
        int baseCount = 0;
        for (Item item : allItems) {
            if (!recipeLookup.containsKey(item)) {
                minCostTable.put(item, 1.0);
                baseCount++;
            }
        }
        RecursiveCraft.LOGGER.info("Phase 1: Initialized {} absolute base items.", baseCount);

        // 3. 第一轮计算 (Phase 1: Convergence)
        // 计算所有能从木头、石头等自然资源合成出来的物品
        runConvergenceLoop(100);

        // 4. 救援行动 (Phase 2: Rescue Ingredients)
        // 此时，铁锭(Ingot)、钻石(Diamond) 等因为有循环配方且无自然来源，成本仍为无限。
        // 进而导致 铁镐(Pickaxe) 也是无限。
        // 我们需要找到所有“配方所需的原料”，如果它们是无限的，强制设为 1.0。
        RecursiveCraft.LOGGER.info("Phase 2: Rescuing deadlock ingredients...");
        int rescuedCount = 0;
        Set<Item> itemsToRescue = new HashSet<>();

        for (Map.Entry<Item, List<CraftingRecipe>> entry : recipeLookup.entrySet()) {
            // 遍历所有配方
            for (CraftingRecipe recipe : entry.getValue()) {
                // 检查该配方的原料
                for (Ingredient ingredient : recipe.getIngredients()) {
                    for (ItemStack stack : ingredient.getItems()) {
                        Item inputItem = stack.getItem();
                        // 如果原料目前还是无限成本 (说明它是死锁环的一部分)
                        if (minCostTable.getOrDefault(inputItem, Double.MAX_VALUE) >= Double.MAX_VALUE) {
                            itemsToRescue.add(inputItem);
                        }
                    }
                }
            }
        }

        // 执行救援
        for (Item item : itemsToRescue) {
            minCostTable.put(item, 1.0);
            rescuedCount++;
            if (isDebugItem(item)) {
                RecursiveCraft.LOGGER.info("   -> Rescued ingredient: {}", item.getDescription().getString());
            }
        }
        RecursiveCraft.LOGGER.info("Phase 2: Rescued {} items (forced to base material).", rescuedCount);

        // 5. 第二轮计算 (Phase 3: Final Convergence)
        // 现在铁锭是 1.0 了，铁镐应该能算出成本了！
        RecursiveCraft.LOGGER.info("Phase 3: Finalizing costs...");
        runConvergenceLoop(100);

        // 6. 生成最终结果
        // 只有那些最终成本有限的物品，才会进入 pathMemo (可合成列表)
        int craftableCount = 0;
        for (Item item : allItems) {
            double finalCost = minCostTable.getOrDefault(item, Double.MAX_VALUE);

            if (finalCost >= Double.MAX_VALUE) {
                // 真的没救了 (孤立物品)
                costMemo.put(item, CostMap.INFINITE_COST);
            } else {
                // 成功
                costMemo.put(item, new CostMap(item, finalCost));
                // 只有当物品有配方，且配方被选中时，pathMemo 才有它
                if (pathMemo.containsKey(item)) {
                    craftableCount++;
                    if (isDebugItem(item)) {
                        RecursiveCraft.LOGGER.info("   -> [Success] Crafted: {} Cost={}", item.getDescription().getString(), finalCost);
                    }
                }
            }
        }

        long endTime = System.currentTimeMillis();
        RecursiveCraft.LOGGER.info("RecursiveCraft: Engine V9.3 finished in {}ms. Found {} craftable items.", (endTime - startTime), craftableCount);
        isReady = true;
    }

    /**
     * 运行一轮迭代收敛
     */
    private void runConvergenceLoop(int maxIterations) {
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < maxIterations) {
            changed = false;
            iterations++;

            for (Map.Entry<Item, List<CraftingRecipe>> entry : recipeLookup.entrySet()) {
                Item target = entry.getKey();
                double currentBestCost = minCostTable.get(target);

                for (CraftingRecipe recipe : entry.getValue()) {
                    double recipeCost = calculateRecipeCost(recipe);
                    if (recipeCost < currentBestCost) {
                        minCostTable.put(target, recipeCost);
                        pathMemo.put(target, recipe);
                        currentBestCost = recipeCost;
                        changed = true;
                    }
                }
            }
        }
    }

    private double calculateRecipeCost(CraftingRecipe recipe) {
        double totalIngredientsCost = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length == 0) return Double.MAX_VALUE;

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
        int outputCount = recipe.getResultItem(null).getCount();
        if (outputCount <= 0) outputCount = 1;
        return totalIngredientsCost / outputCount;
    }
}