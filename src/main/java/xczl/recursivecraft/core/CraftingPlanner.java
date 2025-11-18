// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/core/CraftingPlanner.java
// (V9.8: 引入加工成本惩罚，打破数值相等的配方死循环)
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

public class CraftingPlanner {
    private static final CraftingPlanner INSTANCE = new CraftingPlanner();
    public static volatile boolean isReady = false;

    // [V9.8] 合成成本惩罚 (Entropy)
    // 防止 A(1.0) -> B(1.0) -> A(1.0) 的无损循环。
    // 增加此值确保经过合成步骤越多的物品，单位理论成本越高。
    private static final double RECIPE_COST_PENALTY = 0.1d;

    private final Map<Item, Double> minCostTable = new HashMap<>();
    private final Map<Item, CostMap> costMemo = new HashMap<>();
    private final Map<Item, CraftingRecipe> pathMemo = new HashMap<>();
    private final Map<Item, List<CraftingRecipe>> recipeLookup = new HashMap<>();

    private CraftingPlanner() {}
    public static CraftingPlanner getInstance() { return INSTANCE; }
    public Map<Item, CraftingRecipe> getPathMemo() { return pathMemo; }
    public Map<Item, CostMap> getCostMemo() { return costMemo; }

    public void buildOptimalPathTree(RecipeManager recipeManager) {
        RecursiveCraft.LOGGER.info("RecursiveCraft: Building optimal path tree (Engine V9.8 Entropy)...");
        long startTime = System.currentTimeMillis();

        isReady = false;
        minCostTable.clear();
        costMemo.clear();
        pathMemo.clear();
        recipeLookup.clear();

        List<CraftingRecipe> allRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        Set<Item> allItems = new HashSet<>();

        for (CraftingRecipe recipe : allRecipes) {
            if (recipe.isSpecial() || recipe.getResultItem(null).isEmpty()) continue;
            Item output = recipe.getResultItem(null).getItem();
            recipeLookup.computeIfAbsent(output, k -> new ArrayList<>()).add(recipe);
            allItems.add(output);
        }
        allItems.addAll(ForgeRegistries.ITEMS.getValues());

        for (Item item : allItems) minCostTable.put(item, Double.MAX_VALUE);
        int baseCount = 0;
        for (Item item : allItems) {
            if (!recipeLookup.containsKey(item)) {
                minCostTable.put(item, 1.0);
                baseCount++;
            }
        }
        RecursiveCraft.LOGGER.info("Phase 1: Initialized {} absolute base items.", baseCount);

        // Phase 1: Convergence
        runConvergenceLoop(100, null);

        // Phase 2: Rescue
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

        for (Item item : itemsToRescue) {
            minCostTable.put(item, 1.0);
        }
        RecursiveCraft.LOGGER.info("Phase 2: Rescued {} items.", itemsToRescue.size());

        // Phase 3: Finalize
        runConvergenceLoop(100, itemsToRescue);

        // Result Generation
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

        long endTime = System.currentTimeMillis();
        RecursiveCraft.LOGGER.info("RecursiveCraft: Engine V9.8 finished. Found {} craftable items.", craftableCount);
        isReady = true;
    }

    private void runConvergenceLoop(int maxIterations, Set<Item> rescuedItems) {
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

                    if (recipeCost < currentBestCost || (rescuedItems != null && rescuedItems.contains(target) && recipeCost < Double.MAX_VALUE)) {
                        minCostTable.put(target, recipeCost);
                        pathMemo.put(target, recipe);
                        currentBestCost = recipeCost;
                        changed = true;

                        if (rescuedItems != null) rescuedItems.remove(target);
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

        // [V9.8] 加上加工成本惩罚
        // 这保证了: Cost(Block) > 9 * Cost(Ingot)
        // 进而保证: Cost(Ingot from Block) > Cost(Ingot base)
        totalIngredientsCost += RECIPE_COST_PENALTY;

        int outputCount = recipe.getResultItem(null).getCount();
        if (outputCount <= 0) outputCount = 1;

        return totalIngredientsCost / outputCount;
    }
}