// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/core/CraftingPlanner.java
// (简体中文注释版)
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
 * 流程一：合成规划器 (DP 核心)。
 * 这是一个单例类，负责在服务器启动时，通过动态规划 (DP) 预先计算
 * 游戏中所有可合成物品的最优合成路径和最小材料成本。
 *
 * 这个计算过程在 ModEvents 触发的一个单独的背景线程中运行，以避免阻塞服务器启动。
 */
public class CraftingPlanner {
    /** 单例实例 */
    private static final CraftingPlanner INSTANCE = new CraftingPlanner();

    /**
     * 异步“就绪”标记。
     * 默认为 false。当后台线程完成所有 DP 计算后，此标记将设为 true。
     * 'volatile' 确保多线程之间的可见性 (例如命令线程/网络线程能立即看到 true)。
     */
    public static volatile boolean isReady = false;

    /**
     * 成本缓存 (DP 备忘录)：
     * 存储 Item -> CostMap (该物品的最小基础材料成本)
     */
    private final Map<Item, CostMap> costMemo = new HashMap<>();

    /**
     * 最优路径缓存 (DP 结果)：
     * 存储 Item -> CraftingRecipe (合成该物品的“最优”配方)
     * 这是 "流程二" TransactionCalculator 的主要依据。
     */
    private final Map<Item, CraftingRecipe> pathMemo = new HashMap<>();

    /**
     * 反向配方查找表：
     * 存储 Item -> List<CraftingRecipe> (所有能产出该物品的配方列表)
     * 在 DP 计算开始前构建。
     */
    private final Map<Item, List<CraftingRecipe>> recipeLookup = new HashMap<>();

    private CraftingPlanner() {}

    /**
     * 获取 CraftingPlanner 的全局单例。
     */
    public static CraftingPlanner getInstance() {
        return INSTANCE;
    }

    /**
     * (公开) 获取最优路径图 (pathMemo)。
     * 主要由 RecursiveCrafterScreen 用于构建 GUI 列表。
     * 也由 TransactionCalculator 用于执行合成。
     */
    public Map<Item, CraftingRecipe> getPathMemo() {
        return pathMemo;
    }

    /**
     * (公开) 获取成本缓存 (costMemo)。
     * 主要由 TransactionCalculator 在解析 Tag 时作为备用决策依据。
     */
    public Map<Item, CostMap> getCostMemo() {
        return costMemo;
    }


    /**
     * 辅助函数：检查配方是否为“分解”配方 (例如 1 铁块 -> 9 铁锭)。
     *
     * @param recipe 要检查的配方
     * @return 如果是 1x -> 9x 的分解配方，返回 true
     */
    private boolean isUncraftingRecipe(CraftingRecipe recipe) {
        int nonEmptyIngredients = 0;
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) {
                nonEmptyIngredients++;
            }
        }
        int outputCount = recipe.getResultItem(null).getCount();

        // 启发式判断：我们只关心 "1变9" 的分解配方 (例如 1铁块 -> 9铁锭 或 1铁锭 -> 9铁粒)。
        // 这种判断会忽略 "1原木 -> 4木板"，这是当前设计的一个取舍。
        return nonEmptyIngredients == 1 && outputCount == 9;
    }

    /**
     * (流程一：入口点) 构建最优路径树。
     * 在服务器启动时由 ModEvents 调用。
     *
     * @param recipeManager 原版的配方管理器
     */
    public void buildOptimalPathTree(RecipeManager recipeManager) {
        RecursiveCraft.LOGGER.info("RecursiveCraft: Building optimal path tree...");
        long startTime = System.currentTimeMillis();

        // 1. 清理缓存 (并将状态设为未就绪)
        isReady = false; // 确保在计算开始时设为 false
        costMemo.clear();
        pathMemo.clear();
        recipeLookup.clear();

        // 2. 构建反向查找表 (recipeLookup)
        List<CraftingRecipe> allCraftingRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        for (CraftingRecipe recipe : allCraftingRecipes) {
            // 忽略特殊配方 (如修补、烟花) 和无效配方
            if (recipe.isSpecial() || recipe.getResultItem(null).getItem() == Items.AIR) {
                continue;
            }

            // (注意：这里不再进行 "isUncraftingRecipe" 剪枝，
            // 所有配方都将被添加到 lookup 表中。剪枝操作被移动到 getMinCost 内部)

            Item outputItem = recipe.getResultItem(null).getItem();
            recipeLookup.computeIfAbsent(outputItem, k -> new ArrayList<>()).add(recipe);
        }

        // 3. 为所有可合成的物品 (即 recipeLookup 中的 Key) 计算最优路径
        for (Item craftableItem : recipeLookup.keySet()) {
            getMinCost(craftableItem, new HashSet<>());
        }

        // 4. 为所有物品（包括基础物品）填充成本
        // (确保像 "钻石" 这样的基础材料也能被计算成本，
        // 否则它们在 costMemo 中会缺失，导致 Tag 解析时出错)
        for (Item item : ForgeRegistries.ITEMS) {
            if (!costMemo.containsKey(item)) {
                getMinCost(item, new HashSet<>());
            }
        }

        long endTime = System.currentTimeMillis();
        RecursiveCraft.LOGGER.info("RecursiveCraft: Path tree built in {}ms. Found {} optimal paths.", (endTime - startTime), pathMemo.size());

        // 5. 所有计算完成，设置“就绪”标记
        isReady = true;
        RecursiveCraft.LOGGER.info("RecursiveCraft: Planner is now READY.");
    }

    /**
     * DP 递归计算核心 (带备忘录的递归)。
     * 计算合成 `targetItem` 所需的最小基础材料成本 (CostMap)。
     *
     * @param targetItem  要计算的目标物品
     * @param currentPath 当前的递归路径 (用于检测循环)
     * @return 该物品的最小成本 (CostMap)
     */
    private CostMap getMinCost(Item targetItem, Set<Item> currentPath) {
        // 1. 检查循环 (例如 A -> B -> C -> A)
        // 如果当前路径已包含目标，说明出现循环，返回无限成本
        if (currentPath.contains(targetItem)) {
            return CostMap.INFINITE_COST;
        }

        // 2. 检查缓存 (备忘录)
        // 如果已经计算过，直接返回结果
        if (costMemo.containsKey(targetItem)) {
            return costMemo.get(targetItem);
        }

        // 3. 将当前物品加入路径 (准备递归)
        currentPath.add(targetItem);

        // 4. 查找所有能合成 targetItem 的配方
        List<CraftingRecipe> recipes = recipeLookup.get(targetItem);

        // 5. 基础情况 (Base Case)
        // 如果物品没有配方 (recipes == null)，说明它是基础材料 (如 钻石、沙子)
        if (recipes == null || recipes.isEmpty()) {
            CostMap baseCost = new CostMap(targetItem, 1.0); // 成本就是它自己 1 个
            costMemo.put(targetItem, baseCost);
            currentPath.remove(targetItem); // 回溯
            return baseCost;
        }

        // 6. 递归计算
        CostMap minCost = CostMap.INFINITE_COST;
        CraftingRecipe bestRecipe = null;

        for (CraftingRecipe recipe : recipes) {

            // --- [关键设计缺陷] ---
            // 在 DP *期间* 忽略 "1变多" 的分解配方。
            // 这是为了防止 DP 陷入 (钻石 <-> 钻石块) 这样的成本循环。
            // 但这也导致了 "缺陷 1"：pathMemo 中将永远不会有分解配方。
            if (isUncraftingRecipe(recipe)) {
                continue; // 跳过这个分解配方 (例如：1 铁块 -> 9 铁锭)
            }
            // --- [剪枝结束] ---


            CostMap currentRecipeCost = new CostMap();

            // 7. 遍历配方的所有原料 (Ingredient)
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;

                ItemStack[] matchingStacks = ingredient.getItems(); // 获取原料匹配的所有物品
                if (matchingStacks.length == 0) {
                    currentRecipeCost = CostMap.INFINITE_COST;
                    break;
                }

                // --- Tag 感知逻辑 ---
                CostMap cheapestIngredientCost = CostMap.INFINITE_COST;

                // 遍历该 Ingredient 匹配的所有物品 (例如：所有木板)
                for (ItemStack stackOption : matchingStacks) {
                    Item itemOption = stackOption.getItem();
                    if (itemOption == Items.AIR) continue;

                    // 递归计算 *这个选项* 的成本
                    CostMap optionCost = getMinCost(itemOption, currentPath);

                    // 找到 Tag 中最便宜的那个
                    if (optionCost.getTotalItemCost() < cheapestIngredientCost.getTotalItemCost()) {
                        cheapestIngredientCost = optionCost;
                    }
                }
                // --- 逻辑结束 ---

                if (cheapestIngredientCost.isInfinite()) {
                    currentRecipeCost = CostMap.INFINITE_COST;
                    break;
                }
                // 添加 *最便宜的那个* 选项的成本
                currentRecipeCost.add(cheapestIngredientCost);
            }

            if (currentRecipeCost.isInfinite()) {
                continue; // 此配方无效 (可能包含循环或无法获取的物品)
            }

            // 8. 成本归一化
            // (例如：1原木 -> 4木板，木板成本 = 原木成本 / 4)
            int outputCount = recipe.getResultItem(null).getCount();
            CostMap normalizedCost = currentRecipeCost.divide(outputCount);

            // 9. 找到最小成本的配方
            if (normalizedCost.getTotalItemCost() < minCost.getTotalItemCost()) {
                minCost = normalizedCost;
                bestRecipe = recipe;
            }
        }

        // 10. 存入缓存
        // 如果 minCost 仍然是 INFINITE (意味着所有配方都被剪枝了，例如 铁锭)，
        // 那么我们就把它当作基础材料处理。
        if (minCost.isInfinite()) {
            minCost = new CostMap(targetItem, 1.0);
        }

        costMemo.put(targetItem, minCost); // 存入成本备忘录

        if (bestRecipe != null) {
            pathMemo.put(targetItem, bestRecipe); // 存入最优路径
        }

        // 11. 回溯
        currentPath.remove(targetItem);
        return minCost;
    }
}