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
 * 流程一：DP规划 (来源: [1])
 * 在服务器启动时运行，构建最优路径图 (pathMemo)。
 */
public class CraftingPlanner {
    private static final CraftingPlanner INSTANCE = new CraftingPlanner();

    // === [修复 2A] ===
    /**
     * 异步“就绪”标记。
     * 默认为 false。当后台线程完成所有DP计算后，此标记将设为 true。
     * 'volatile' 确保多线程之间的可见性。
     */
    public static volatile boolean isReady = false;
    // ===============

    private final Map<Item, CostMap> costMemo = new HashMap<>();
    private final Map<Item, CraftingRecipe> pathMemo = new HashMap<>();
    private final Map<Item, List<CraftingRecipe>> recipeLookup = new HashMap<>();

    private CraftingPlanner() {}

    public static CraftingPlanner getInstance() {
        return INSTANCE;
    }

    public Map<Item, CraftingRecipe> getPathMemo() {
        return pathMemo;
    }

    /**
     * 辅助函数：检查配方是否为“分解”配方 (1 -> 多)
     * (保留 == 9 的启发式判断，这是区分“分解”和“加工”的最佳方式)
     */
    private boolean isUncraftingRecipe(CraftingRecipe recipe) {
        int nonEmptyIngredients = 0;
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) {
                nonEmptyIngredients++;
            }
        }
        int outputCount = recipe.getResultItem(null).getCount();

        // "1变多" (e.g., 1 铁块 -> 9 铁锭)
        // 我们只关心 "1变9" 的分解配方 (例如 1铁块 -> 9铁锭 或 1铁锭 -> 9铁粒)
        // 这样 (1原木 -> 4木板) 就不会被匹配
        return nonEmptyIngredients == 1 && outputCount == 9;
    }

    /**
     * (流程一) Mod加载时的入口点 [1]
     */
    public void buildOptimalPathTree(RecipeManager recipeManager) {
        RecursiveCraft.LOGGER.info("RecursiveCraft: Building optimal path tree...");
        long startTime = System.currentTimeMillis();

        // 1. 清理缓存 (并将状态设为未就绪)
        isReady = false; // <<< 确保在计算开始时设为 false
        costMemo.clear();
        pathMemo.clear();
        recipeLookup.clear();

        // 2. 构建反向查找表
        List<CraftingRecipe> allCraftingRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        for (CraftingRecipe recipe : allCraftingRecipes) {
            if (recipe.isSpecial() || recipe.getResultItem(null).getItem() == Items.AIR) {
                continue;
            }

            // === 【V5 剪枝：已移除】 ===
            // 按照您的建议，我们不再进行预剪枝。
            // 所有配方都将被添加到 lookup 表中。
            // if (isUncraftingRecipe(recipe)) {
            //     continue;
            // }
            // === 【V5 剪枝结束】 ===

            Item outputItem = recipe.getResultItem(null).getItem();
            recipeLookup.computeIfAbsent(outputItem, k -> new ArrayList<>()).add(recipe);
        }

        // 3. 为所有可合成的物品计算最优路径
        for (Item craftableItem : recipeLookup.keySet()) {
            getMinCost(craftableItem, new HashSet<>());
        }

        // === 【V5 新增：为所有物品（包括基础物品）填充成本】 ===
        // 确保像 "钻石" 这样的基础材料 (现在配方表为空) 也能被计算
        // (此步骤在原版中缺失，导致钻石被排除)
        for (Item item : ForgeRegistries.ITEMS) {
            if (!costMemo.containsKey(item)) {
                getMinCost(item, new HashSet<>());
            }
        }
        // ============================================


        long endTime = System.currentTimeMillis();
        RecursiveCraft.LOGGER.info("RecursiveCraft: Path tree built in {}ms. Found {} optimal paths.", (endTime - startTime), pathMemo.size());

        // === [修复 2A] ===
        // 4. 所有计算完成，设置“就绪”标记
        isReady = true;
        RecursiveCraft.LOGGER.info("RecursiveCraft: Planner is now READY.");
        // ===============
    }

    /**
     * DP递归计算核心 (来源: [1])
     * (V5方案中，此函数 *不需要* 任何剪枝逻辑，因为剪枝在 buildOptimalPathTree 中已完成)
     */
    private CostMap getMinCost(Item targetItem, Set<Item> currentPath) {
        // 1. 检查循环
        if (currentPath.contains(targetItem)) {
            return CostMap.INFINITE_COST;
        }

        // 2. 检查缓存
        if (costMemo.containsKey(targetItem)) {
            return costMemo.get(targetItem);
        }

        // 3. 将当前物品加入路径
        currentPath.add(targetItem);

        // 4. 查找所有能合成 targetItem 的配方
        // (V5: 此列表现在包含 *所有* 配方)
        List<CraftingRecipe> recipes = recipeLookup.get(targetItem);

        // 5. 基础情况
        if (recipes == null || recipes.isEmpty()) {
            CostMap baseCost = new CostMap(targetItem, 1.0);
            costMemo.put(targetItem, baseCost);
            currentPath.remove(targetItem); // 回溯
            return baseCost;
        }

        // 6. 递归计算
        CostMap minCost = CostMap.INFINITE_COST;
        CraftingRecipe bestRecipe = null;

        for (CraftingRecipe recipe : recipes) {

            // === 【新的“内侧”剪枝】 ===
            // 我们在DP *期间* 忽略 "1变多" 的分解配方
            // 这可以避免 (钻石 <-> 钻石块) 的循环被 V5 的 Step 7 逻辑错误地“修复”
            if (isUncraftingRecipe(recipe)) {
                continue; // 跳过这个分解配方 (例如：1 铁块 -> 9 铁锭)
            }
            // === 【剪枝结束】 ===


            CostMap currentRecipeCost = new CostMap();

            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;

                ItemStack[] matchingStacks = ingredient.getItems();
                if (matchingStacks.length == 0) {
                    currentRecipeCost = CostMap.INFINITE_COST;
                    break;
                }

                Item ingredientItem = matchingStacks[0].getItem();
                if(ingredientItem == Items.AIR) continue;

                CostMap ingredientCost = getMinCost(ingredientItem, currentPath);
                if (ingredientCost.isInfinite()) {
                    currentRecipeCost = CostMap.INFINITE_COST;
                    break;
                }
                currentRecipeCost.add(ingredientCost);
            }

            if (currentRecipeCost.isInfinite()) {
                continue;
            }

            int outputCount = recipe.getResultItem(null).getCount();
            CostMap normalizedCost = currentRecipeCost.divide(outputCount);

            if (normalizedCost.getTotalItemCost() < minCost.getTotalItemCost()) {
                minCost = normalizedCost;
                bestRecipe = recipe;
            }
        }

        // 7. 存入缓存
        // (V5: 如果 minCost 仍然是 INFINITE, 意味着它虽然有配方(但被我们剪枝了),
        // 或者配方有我们未处理的循环)
        // (我们应该把它当作基础材料)
        if (minCost.isInfinite()) {
            minCost = new CostMap(targetItem, 1.0);
        }

        costMemo.put(targetItem, minCost);

        if (bestRecipe != null) {
            pathMemo.put(targetItem, bestRecipe);
        }

        // 8. 回溯
        currentPath.remove(targetItem);
        return minCost;
    }
}