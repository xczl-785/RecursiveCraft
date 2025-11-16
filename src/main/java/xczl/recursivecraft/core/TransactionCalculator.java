// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/core/TransactionCalculator.java
// (简体中文注释版)
// ======================================================================
package xczl.recursivecraft.core;

import xczl.recursivecraft.data.CostMap;
import xczl.recursivecraft.data.CraftingTransaction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

// 导入
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 流程二：事务计算器。
 * 负责在玩家请求合成时，根据 "流程一" (CraftingPlanner) 生成的 `pathMemo`，
 * 结合玩家的 "虚拟库存" 快照，递归地计算出最终需要消耗 (Needs) 和产出 (Provides) 的物品列表。
 *
 * 核心特性：
 * 1. 虚拟库存 (virtualInventory)：解决 "合成工作台时消耗工作台" 的 Bug。
 * 2. 原料聚合 (aggregatedNeeds)：解决 "合成箱子时 8 个木板被计算 8 次" 的 Bug。
 * 3. Tag 解析 (resolveIngredient)：智能选择合成 Tag 中的哪种物品。
 */
public class TransactionCalculator {

    /**
     * 内部辅助类，用于原料聚合。
     * 因为 Ingredient 没有 .equals / .hashCode，我们无法直接在 Map 中聚合。
     */
    private static class IngredientNeed {
        public Ingredient ingredient; // 原料
        public int amount; // 需要的数量
        public IngredientNeed(Ingredient ing, int amt) {
            this.ingredient = ing;
            this.amount = amt;
        }
    }


    private final Map<Item, CraftingRecipe> pathMemo; // 流程一的最优路径图
    private final Map<Item, CostMap> costMemo; // 流程一的成本图 (用于 Tag 解析)
    private final Inventory playerInventory; // 玩家物品栏 (仅用于创建初始快照)

    /**
     * 构造函数。
     *
     * @param playerInventory 玩家的真实物品栏 (用于创建初始 "虚拟库存" 快照)
     */
    public TransactionCalculator(Inventory playerInventory) {
        this.playerInventory = playerInventory;
        this.pathMemo = CraftingPlanner.getInstance().getPathMemo();
        this.costMemo = CraftingPlanner.getInstance().getCostMemo();
    }

    /**
     * (流程二：公共入口点)
     * 计算合成 `target` 物品 `amount` 数量所需的事务。
     *
     * @param target        最终要合成的目标物品
     * @param amount        目标数量
     * @param isFinalTarget 标记这是否为“最终目标”。
     * (如果为 true, 则*不会*尝试从背包消耗 `target` 物品)
     * @return 包含 Needs 和 Provides 列表的合成事务
     */
    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget) {

        // 1. 创建一个可变的“虚拟库存”快照
        // 这是整个递归计算过程中共享的状态
        Map<Item, Integer> virtualInventory = new HashMap<>();
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (!stack.isEmpty()) {
                virtualInventory.put(stack.getItem(), virtualInventory.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
        }

        // 2. 调用私有的、有状态的递归 "worker"
        return calculate(target, amount, isFinalTarget, virtualInventory);
    }

    /**
     * (流程二：私有递归核心)
     *
     * @param target           当前递归层级的目标物品
     * @param amount           需要的目标物品数量
     * @param isFinalTarget    是否为最终目标
     * @param virtualInventory 共享的虚拟库存 (会被递归调用修改)
     * @return 仅包含 *此层级* 所需的 Needs 和 Provides 的事务
     */
    private CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory) {

        CraftingTransaction currentTransaction = new CraftingTransaction();
        int amountInInventory = 0;

        // --- 1. 检查虚拟库存 (消耗已有物品) ---
        // (修复 "工作台 Bug"：如果这不是最终目标 (例如工作台是中间产物)，
        // 我们优先尝试从虚拟库存中消耗它)
        if (!isFinalTarget) {
            amountInInventory = virtualInventory.getOrDefault(target, 0);

            if (amountInInventory >= amount) {
                // 库存足够，无需合成
                currentTransaction.addNeed(target, amount); // 将此物品添加到 "需求" (它将被上层递归消耗)
                virtualInventory.put(target, amountInInventory - amount); // 从虚拟库存中扣除
                return currentTransaction;
            }
        }

        // --- 2. 计算还需要合成多少 ---
        int amountToCraft = amount - amountInInventory;
        if (amountInInventory > 0) {
            // 库存不足，先消耗掉所有库存
            currentTransaction.addNeed(target, amountInInventory);
            virtualInventory.put(target, 0); // 库存清零
        }

        // --- 3. 查找配方 ---
        // (这是 "缺陷 1" 的发生点：如果 target=铁锭, optimalRecipe 会是 null)
        CraftingRecipe optimalRecipe = pathMemo.get(target);

        // --- 4. 基础情况 (无法合成) ---
        if (optimalRecipe == null) {
            // 没有配方，说明这是基础材料 (或者配方被 Planner 剪枝了)
            currentTransaction.addNeed(target, amountToCraft); // 直接将其添加到 "需求"
            return currentTransaction;
        }

        // --- 5. 递归合成 ---
        int outputCount = optimalRecipe.getResultItem(null).getCount(); // 配方单次产出量
        // 计算需要执行配方的次数 (向上取整)
        int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);


        // --- [原料聚合 修复] ---
        // (这是修复“缺少2个木板”BUG的关键)

        // 1. 聚合相同的原料 (使用 Ingredient.toString() 作为 Key)
        // (这是 "缺陷 4" 的发生点：toString() 可能不可靠)
        Map<String, IngredientNeed> aggregatedNeeds = new HashMap<>();
        for (Ingredient ingredient : optimalRecipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;

            String key = ingredient.toString();
            IngredientNeed entry = aggregatedNeeds.getOrDefault(key, new IngredientNeed(ingredient, 0));
            entry.amount += 1; // 增加该原料的需求数量
            aggregatedNeeds.put(key, entry);
        }

        // 2. 获取原料并排序 (特殊性优先)
        // (例如，"铁锭" (length=1) 优先于 "任意木板" (length=6))
        // (这是一种启发式优化，意义不大，但无害)
        List<IngredientNeed> sortedNeeds = new ArrayList<>(aggregatedNeeds.values());
        sortedNeeds.sort(Comparator.comparingInt(p -> p.ingredient.getItems().length));

        // 3. 遍历 *聚合后* 且 *排序后* 的原料
        for (IngredientNeed entry : sortedNeeds) {
            Ingredient ingredient = entry.ingredient;
            int amountPerRun = entry.amount; // 每次配方需要 X 个此原料

            // 总共需要 (每次配方所需数量 * 配方执行次数)
            int totalInputAmount = amountPerRun * recipeRuns;

            // 递归解析这个原料
            CraftingTransaction subTransaction = resolveIngredient(ingredient, totalInputAmount, false, virtualInventory);

            // 合并子事务 (Needs 和 Provides)
            currentTransaction.merge(subTransaction);
        }
        // --- [聚合 修复 结束] ---


        // --- 6. 处理副产品 (递归返回后) ---
        int totalProvided = recipeRuns * outputCount; // 配方总共实际产出了多少
        int leftover = totalProvided - amountToCraft; // 减去本层需要消耗的，还剩多少

        if (leftover > 0) {
            // 如果有剩余 (例如，配方产出4个，本层需要3个，剩余1个)
            currentTransaction.addProvide(target, leftover); // 将剩余的 1 个添加到 "产出"
            // 并将其添加回 "虚拟库存"，以供*其他*递归分支使用
            virtualInventory.put(target, virtualInventory.getOrDefault(target, 0) + leftover);
        }

        // (注意：本层需要的 `amountToCraft` 个物品，并没有被 addProvide，
        // 它们被隐式地 "消耗" 掉了，供上层调用使用)

        return currentTransaction;
    }


    /**
     * (流程二：私有 Tag 解析器)
     * 专门用于解析一个 `Ingredient` (原料)。
     * 它可以是单个物品 (如 铁锭)，也可以是 Tag (如 minecraft:planks)。
     *
     * @param ingredient       要解析的原料
     * @param amount           需要的数量
     * @param isFinalTarget    (此参数在此处总是 false)
     * @param virtualInventory 共享的虚拟库存
     * @return 解析该原料所需的事务
     */
    private CraftingTransaction resolveIngredient(Ingredient ingredient, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory) {

        CraftingTransaction currentTransaction = new CraftingTransaction();
        ItemStack[] matchingStacks = ingredient.getItems(); // 获取 Tag 匹配的所有物品

        if (matchingStacks.length == 0) return currentTransaction; // 空原料

        // 优化：如果这不是一个 Tag (只有一个物品匹配)
        if (matchingStacks.length == 1) {
            Item simpleItem = matchingStacks[0].getItem();
            if (simpleItem == Items.AIR) return currentTransaction;
            // 直接调用 `calculate` (传递 "虚拟库存")
            return calculate(simpleItem, amount, isFinalTarget, virtualInventory);
        }

        // --- Tag (或复杂) 原料的处理逻辑 ---
        int amountInInventory = 0;
        int amountToCraft = amount; // 还需要合成多少

        if (!isFinalTarget) {
            // 1. 从 "虚拟库存" 中消耗 *任何* 匹配 Tag 的物品
            Map<Item, Integer> foundItemsToConsume = new HashMap<>();
            int totalFound = 0;

            // 遍历 *虚拟库存*
            for (Map.Entry<Item, Integer> entry : virtualInventory.entrySet()) {
                Item itemInStock = entry.getKey();
                int foundInStack = entry.getValue();

                // 检查虚拟库存中的物品是否匹配 Tag
                if (foundInStack > 0 && ingredient.test(new ItemStack(itemInStock))) {
                    int amountToUse = Math.min(amountToCraft - totalFound, foundInStack);

                    if (amountToUse > 0) {
                        foundItemsToConsume.put(itemInStock, foundItemsToConsume.getOrDefault(itemInStock, 0) + amountToUse);
                        totalFound += amountToUse;
                    }

                    if (totalFound == amountToCraft) break; // 已经找够了
                }
            }

            // 2. 将找到的物品添加到 "Needs" 列表, 并 *实际更新* 虚拟库存
            for (Map.Entry<Item, Integer> entry : foundItemsToConsume.entrySet()) {
                Item itemToConsume = entry.getKey();
                int amountToConsume = entry.getValue();

                currentTransaction.addNeed(itemToConsume, amountToConsume);

                // 真正从虚拟库存中移除
                int newAmount = virtualInventory.getOrDefault(itemToConsume, 0) - amountToConsume;
                virtualInventory.put(itemToConsume, newAmount);
            }

            amountInInventory = totalFound;
            amountToCraft = amount - amountInInventory; // 更新还需要合成多少
        }

        // 3. 如果我们还需要合成 (amountToCraft > 0)
        if (amountToCraft > 0) {
            // (这是 Tag 解析的核心：我们应该合成 Tag 中的哪一个？)

            Item bestItemToCraft = Items.AIR; // 最终决定要合成的物品
            int bestScore = 999; // 决策优先级 (0 = 最好)
            Item cheapestItemFallback = Items.AIR; // 备用选项 (理论最便宜)
            double cheapestCost = Double.MAX_VALUE;

            for (ItemStack stackOption : matchingStacks) {
                Item itemOption = stackOption.getItem();
                if (itemOption == Items.AIR) continue;

                // --- 决策 1：浅层检查 (库存感知) ---
                // 检查我们是否*恰好*拥有合成这个 `itemOption` 的*直接*原料？
                CraftingRecipe recipe = this.pathMemo.get(itemOption);
                if (recipe != null) {
                    boolean hasAllDirectMats = true;
                    for (Ingredient subIngredient : recipe.getIngredients()) {
                        if (subIngredient.isEmpty()) continue;

                        // 浅层检查 *必须* 检查 "虚拟库存"
                        boolean hasSubMat = false;
                        for (Map.Entry<Item, Integer> entry : virtualInventory.entrySet()) {
                            if (entry.getValue() > 0 && subIngredient.test(new ItemStack(entry.getKey()))) {
                                hasSubMat = true;
                                break;
                            }
                        }
                        if (!hasSubMat) {
                            hasAllDirectMats = false;
                            break;
                        }
                    }

                    if (hasAllDirectMats) {
                        // 找到了！我们有合成它的直接原料。
                        bestItemToCraft = itemOption;
                        bestScore = 0; // 最高优先级
                        break;
                    }
                }

                // --- 决策 2：理论成本回退 (DP 成本) ---
                // 如果浅层检查未命中 (bestScore > 0)
                if (bestScore > 0) {
                    // 我们从 `costMemo` (流程一) 中查找理论上最便宜的
                    CostMap optionCost = this.costMemo.get(itemOption);
                    if (optionCost == null) {
                        optionCost = new CostMap(itemOption, 1.0); // 基础材料
                    }

                    if (!optionCost.isInfinite() && optionCost.getTotalItemCost() < cheapestCost) {
                        cheapestCost = optionCost.getTotalItemCost();
                        cheapestItemFallback = itemOption;
                        bestScore = 1; // 备用优先级
                    }
                }
            } // 结束遍历 Tag 选项

            // 4. 决定最终合成目标
            if (bestScore == 0) {
                // `bestItemToCraft` 已在浅层检查中被设置
            } else if (bestScore == 1) {
                bestItemToCraft = cheapestItemFallback; // 使用理论最便宜的
            } else {
                bestItemToCraft = matchingStacks[0].getItem(); // 随便选一个
            }

            // 5. 递归调用
            if (bestItemToCraft != Items.AIR) {
                // 传入 "虚拟库存"
                CraftingTransaction subTransaction = calculate(bestItemToCraft, amountToCraft, false, virtualInventory);
                currentTransaction.merge(subTransaction);
            }
        }

        return currentTransaction;
    }
}