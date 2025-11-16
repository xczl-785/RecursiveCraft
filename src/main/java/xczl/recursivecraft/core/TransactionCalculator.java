package xczl.recursivecraft.core;

import xczl.recursivecraft.data.CraftingTransaction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.HashMap;
import java.util.Map;

/**
 * 流程二：事务计算 (智能版) (来源: [1])
 * 修复了 Bug 1 (背包刷新)
 */
public class TransactionCalculator {

    private final Map<Item, CraftingRecipe> pathMemo;
    private final Inventory playerInventory;

    public TransactionCalculator(Inventory playerInventory) {
        this.playerInventory = playerInventory;
        this.pathMemo = CraftingPlanner.getInstance().getPathMemo();
    }

    /**
     * (流程二) 命令触发时的入口点 [1]
     *
     * @param target        要合成的物品
     * @param amount        要合成的数量
     * @param isFinalTarget 这是一个“最终产品”(true) 还是“中间材料”(false)?
     * [修复 Bug 1: 最终产品 *不能* 从背包消耗]
     * @return 计算出的事务
     */
    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget) {

        CraftingTransaction currentTransaction = new CraftingTransaction();
        int amountInInventory = 0;

        // === [修复 Bug 1] ===
        // 只有 *不是* 最终产品时 (即, 这是一个中间材料),
        // 我们才检查背包并可能 "跳过" 合成。
        if (!isFinalTarget) {
            amountInInventory = playerInventory.countItem(target);

            if (amountInInventory >= amount) {
                // 背包里有足够的中间材料, 直接 "消耗" 它们。
                currentTransaction.addNeed(target, amount);
                return currentTransaction;
            }
        }

        // --- 2. 计算还需要合成多少 ---
        // (e.g., 需要4个木板, 背包里有3个. amountToCraft = 1)
        int amountToCraft = amount - amountInInventory;

        // 如果玩家有一部分, 我们先标记 "消耗" 这一部分
        if (amountInInventory > 0) {
            currentTransaction.addNeed(target, amountInInventory);
        }

        // --- 3. 查找配方 ---
        CraftingRecipe optimalRecipe = pathMemo.get(target);

        // --- 4. 基础情况 (无法合成) ---
        if (optimalRecipe == null) {
            // 这是一个基础材料 (如钻石) 或一个因 Bug 2A 导致配方为 null 的物品
            // 我们需要 `amountToCraft` 这么多, 但是我们又没法合成它。
            // 我们将总需求（amount）添加到 Needs 列表。
            // 最终的 "缺少材料" 检查会处理这个。

            // (注意: 这就是 Bug 2A 和 2B 发生的地方)
            currentTransaction.addNeed(target, amountToCraft);
            return currentTransaction;
        }

        // --- 5. 递归合成 (逻辑同前，但目标是 amountToCraft) ---

        // 计算需要执行多少次配方 (为 'amountToCraft')
        int outputCount = optimalRecipe.getResultItem(null).getCount();
        int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);

        // 聚合所有子材料的需求
        Map<Item, Integer> aggregatedIngredients = new HashMap<>();
        for (Ingredient ingredient : optimalRecipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;

            ItemStack[] matchingStacks = ingredient.getItems();
            if (matchingStacks.length == 0) continue;

            Item inputItem = matchingStacks[0].getItem();
            if(inputItem == Items.AIR) continue;

            aggregatedIngredients.put(inputItem, aggregatedIngredients.getOrDefault(inputItem, 0) + 1);
        }

        // 递归计算 *聚合后* 的事务
        for (Map.Entry<Item, Integer> entry : aggregatedIngredients.entrySet()) {
            Item inputItem = entry.getKey();
            int inputAmount = entry.getValue() * recipeRuns;

            // *** 递归调用 ***
            // 传入 'false', 因为所有子材料都是 "中间材料"
            CraftingTransaction subTransaction = calculate(inputItem, inputAmount, false);
            currentTransaction.merge(subTransaction); // 合并子事务
        }

        // --- 6. 处理副产品 ---
        int totalProvided = recipeRuns * outputCount;
        int leftover = totalProvided - amountToCraft; // 多合成了多少

        if (leftover > 0) {
            currentTransaction.addProvide(target, leftover);
        }

        return currentTransaction;
    }
}