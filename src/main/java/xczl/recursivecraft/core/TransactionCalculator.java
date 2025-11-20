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

/**
 * 合成事务计算器 (战术执行者).
 * <p>
 * 该类的核心职责是，在给定一个具体的合成目标（如“合成10个活塞”）和玩家当前的库存时，
 * 计算出一份详细的、可执行的合成计划。它扮演着“战术家”的角色。
 * <p>
 * 工作流程:
 * 1. 依赖 {@link CraftingPlanner} 预先计算好的最优合成路径 (pathMemo) 和成本数据 (costMemo)。
 * 2. 使用递归的方式，从最终目标开始，反向推导所需的每一层原料。
 * 3. 在每一步推导中，优先消耗玩家背包中已有的物品。
 * 4. 面对使用物品标签（Tag）的配方（即多种物品可选），它采用一套智能的“双重扫描”策略来做出最佳选择：
 *    a. **第一扫描 (贪心消耗)**: 优先消耗背包里已有的、符合标签的物品。
 *    b. **第二扫描 (智能合成)**: 如果库存物品不足，它会分析所有选项，优先选择“即时可合成”（其直接原料背包里都有）的物品进行合成，
 *       如果都不满足，则退而求其次，选择理论成本最低的物品进行合成。
 * 5. 最终生成一份 {@link CraftingTransaction}，其中包含了需要从外界获取的基础材料列表和合成过程中产生的副产品列表。
 */
public class TransactionCalculator {

    /**
     * 内部辅助类，用于在处理配方时聚合相同的原料需求。
     */
    private static class IngredientNeed {
        public final Ingredient ingredient;
        public int amount;

        public IngredientNeed(Ingredient ing, int amt) {
            this.ingredient = ing;
            this.amount = amt;
        }
    }

    /**
     * 内部辅助类，用于对物品标签（Tag）中的多个选项进行评分和排序。
     */
    private static class ItemOptionScore {
        public final Item item;
        /** 评分: 0=库存中存在, 1=可立即合成, 2=理论上可合成 */
        public final int score;
        /** 理论成本，用于在评分相同时做决策 */
        public final double cost;

        public ItemOptionScore(Item item, int score, double cost) {
            this.item = item;
            this.score = score;
            this.cost = cost;
        }
    }

    private final Map<Item, CraftingRecipe> pathMemo;
    private final Map<Item, CostMap> costMemo;
    private final Inventory playerInventory;

    /**
     * 构造一个新的事务计算器。
     * @param playerInventory 玩家的实时库存。
     */
    public TransactionCalculator(Inventory playerInventory) {
        this.playerInventory = playerInventory;
        // 从战略规划器获取预计算好的数据
        this.pathMemo = CraftingPlanner.getInstance().getPathMemo();
        this.costMemo = CraftingPlanner.getInstance().getCostMemo();
    }

    /**
     * 计算合成目标所需的事务。这是该类的主要入口点。
     *
     * @param target        目标物品。
     * @param amount        目标数量。
     * @param isFinalTarget 标记这是否是玩家请求的最终目标。最终目标不能从库存中直接消耗。
     * @return 一个包含所需材料和副产品的合成事务。
     */
    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget) {
        // 创建一个虚拟库存，模拟计算过程中的物品消耗和产出
        Map<Item, Integer> virtualInventory = new HashMap<>();
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (!stack.isEmpty()) {
                virtualInventory.put(stack.getItem(), virtualInventory.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
        }

        RecursiveCraft.LOGGER.info("--- [CALCULATION START] ---");
        RecursiveCraft.LOGGER.info("Target: {} x{}", target.getDescription().getString(), amount);

        Set<Item> recursionStack = new HashSet<>(); // 用于检测合成死循环
        CraftingTransaction result = calculateRecursive(target, amount, isFinalTarget, virtualInventory, 1, recursionStack);

        RecursiveCraft.LOGGER.info("--- [CALCULATION END] ---");
        return result;
    }

    /**
     * 核心递归计算函数。
     *
     * @param target           当前要计算的物品。
     * @param amount           需要的数量。
     * @param isFinalTarget    是否为最终目标。
     * @param virtualInventory 模拟库存。
     * @param debugDepth       递归深度，用于日志缩进。
     * @param recursionStack   递归调用栈，用于防止死循环。
     * @return 当前层级的计算结果。
     */
    private CraftingTransaction calculateRecursive(Item target, int amount, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth, Set<Item> recursionStack) {
        String indent = "  ".repeat(debugDepth);
        RecursiveCraft.LOGGER.info("{}Request: {} x{}", indent, target.getDescription().getString(), amount);

        CraftingTransaction currentTransaction = new CraftingTransaction();

        // 递归守卫，防止因配方错误导致的无限循环
        if (recursionStack.contains(target)) {
            RecursiveCraft.LOGGER.warn("{}!! Cycle Detected for {} !! Breaking.", indent, target.getDescription().getString());
            currentTransaction.addNeed(target, amount);
            return currentTransaction;
        }
        recursionStack.add(target);

        try {
            // 1. 检查并消耗库存 (最终目标除外)
            int amountInInventory = 0;
            if (!isFinalTarget) {
                amountInInventory = virtualInventory.getOrDefault(target, 0);
                if (amountInInventory >= amount) {
                    RecursiveCraft.LOGGER.info("{}-> Stock: Using {} (Full)", indent, amount);
                    currentTransaction.addNeed(target, amount); // 记录为“需要”，因为它被消耗了
                    virtualInventory.put(target, amountInInventory - amount);
                    return currentTransaction;
                }
            }

            // 2. 计算仍需合成的数量
            int amountToCraft = amount - amountInInventory;
            if (amountInInventory > 0) {
                RecursiveCraft.LOGGER.info("{}-> Stock: Using {} (Partial), Need Craft: {}", indent, amountInInventory, amountToCraft);
                currentTransaction.addNeed(target, amountInInventory);
                virtualInventory.put(target, 0); // 全部消耗
            }

            // 3. 查找最优配方
            CraftingRecipe optimalRecipe = pathMemo.get(target);
            if (optimalRecipe == null) {
                RecursiveCraft.LOGGER.info("{}-> Base Item / No Recipe. Missing: {}", indent, amountToCraft);
                currentTransaction.addNeed(target, amountToCraft); // 记录为最终需求
                return currentTransaction;
            }

            // 4. 解析配方，计算需要执行的次数和聚合原料
            int outputCount = optimalRecipe.getResultItem(null).getCount();
            int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);

            Map<String, IngredientNeed> aggregatedNeeds = new HashMap<>();
            for (Ingredient ingredient : optimalRecipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                // 使用 Ingredient 的序列化形式作为 key 来聚合
                String key = ingredient.toString();
                IngredientNeed entry = aggregatedNeeds.getOrDefault(key, new IngredientNeed(ingredient, 0));
                entry.amount += 1;
                aggregatedNeeds.put(key, entry);
            }

            // 5. 递归解决所有原料
            List<IngredientNeed> sortedNeeds = new ArrayList<>(aggregatedNeeds.values());
            // 优先处理选项少的原料，这是一种启发式优化，但影响不大
            sortedNeeds.sort(Comparator.comparingInt(p -> p.ingredient.getItems().length));

            for (IngredientNeed entry : sortedNeeds) {
                int totalInputAmount = entry.amount * recipeRuns;
                CraftingTransaction subTransaction = resolveIngredientRecursive(entry.ingredient, totalInputAmount, false, virtualInventory, debugDepth + 1, recursionStack);
                currentTransaction.merge(subTransaction);
            }

            // 6. 处理副产物
            int totalProvided = recipeRuns * outputCount;
            int leftover = totalProvided - amountToCraft;
            if (leftover > 0) {
                currentTransaction.addProvide(target, leftover);
                virtualInventory.put(target, virtualInventory.getOrDefault(target, 0) + leftover); // 将副产物加入虚拟库存
            }

            return currentTransaction;

        } finally {
            recursionStack.remove(target); // 回溯时移除
        }
    }

    /**
     * 解析一个（可能带标签的）原料。这是处理物品标签（Tag）的核心。
     */
    private CraftingTransaction resolveIngredientRecursive(Ingredient ingredient, int amountNeeded, boolean isFinalTarget, Map<Item, Integer> virtualInventory, int debugDepth, Set<Item> recursionStack) {
        String indent = "  ".repeat(debugDepth);
        ItemStack[] matchingStacks = ingredient.getItems();
        if (matchingStacks.length == 0) return new CraftingTransaction();

        // 如果原料不带标签，直接递归计算
        if (matchingStacks.length == 1) {
            return calculateRecursive(matchingStacks[0].getItem(), amountNeeded, isFinalTarget, virtualInventory, debugDepth + 1, recursionStack);
        }

        // --- 智能选择阶段 ---
        // 1. 初始化评分：为所有选项打分
        List<ItemOptionScore> scoredOptions = new ArrayList<>();
        for (ItemStack stackOption : matchingStacks) {
            Item itemOption = stackOption.getItem();
            if (itemOption == Items.AIR) continue;

            CostMap optionCostMap = this.costMemo.get(itemOption);
            if (optionCostMap == null || optionCostMap.isInfinite()) continue; // 排除无法制作的物品

            int currentScore = 2; // 默认：理论可做
            if (virtualInventory.getOrDefault(itemOption, 0) > 0) {
                currentScore = 0; // 最高优先级：库存已有
            } else if (checkShallowRecipe(this.pathMemo.get(itemOption), virtualInventory)) {
                currentScore = 1; // 次高优先级：可立即合成
            }
            scoredOptions.add(new ItemOptionScore(itemOption, currentScore, optionCostMap.getTotalItemCost()));
        }

        // 初始排序: 库存 > 浅层 > 理论成本
        scoredOptions.sort(Comparator.comparingInt((ItemOptionScore o) -> o.score).thenComparingDouble(o -> o.cost));

        CraftingTransaction currentTransaction = new CraftingTransaction();
        int remainingAmount = amountNeeded;

        // 2. Phase 1: 贪心消耗库存 (只处理 score=0 的选项)
        for (ItemOptionScore option : scoredOptions) {
            if (option.score > 0 || remainingAmount == 0) continue;

            int amountInStock = virtualInventory.getOrDefault(option.item, 0);
            int amountToConsume = Math.min(remainingAmount, amountInStock);

            if (amountToConsume > 0) {
                RecursiveCraft.LOGGER.info("{}-> Tag Choice (Stock): {} x{}", indent, option.item.getDescription().getString(), amountToConsume);
                currentTransaction.addNeed(option.item, amountToConsume);
                virtualInventory.put(option.item, amountInStock - amountToConsume);
                remainingAmount -= amountToConsume;
            }
        }

        if (remainingAmount == 0) return currentTransaction;

        // 3. Phase 2: 智能合成剩余部分
        Item bestItemToCraft = findBestCraftingCandidate(scoredOptions, virtualInventory, indent);

        if (bestItemToCraft != Items.AIR) {
            RecursiveCraft.LOGGER.info("{}-> Tag Choice (Craft): {} x{}", indent, bestItemToCraft.getDescription().getString(), remainingAmount);
            CraftingTransaction subTransaction = calculateRecursive(bestItemToCraft, remainingAmount, false, virtualInventory, debugDepth + 1, recursionStack);
            currentTransaction.merge(subTransaction);
        }

        return currentTransaction;
    }

    /**
     * 从多个选项中找到当前最适合合成的候选项。
     */
    private Item findBestCraftingCandidate(List<ItemOptionScore> scoredOptions, Map<Item, Integer> virtualInventory, String indent) {
        // [策略 A]: 优先寻找“即时可合成”的物品 (Shallow Recipe)
        // 无论它之前是 Score 0 还是 1，只要现在背包里有原料，它就是最好的选择。
        for (ItemOptionScore option : scoredOptions) {
            if (checkShallowRecipe(this.pathMemo.get(option.item), virtualInventory)) {
                RecursiveCraft.LOGGER.info("{}   [Smart Select] Found ready-to-craft candidate: {}", indent, option.item.getDescription().getString());
                return option.item;
            }
        }

        // [策略 B]: 如果都不能立即合成，则选择理论上可合成且成本最低的
        // 这样 calculateRecursive 进去后会正确地报告“缺少材料”，而不是无声失败。
        for (ItemOptionScore option : scoredOptions) {
            if (this.pathMemo.containsKey(option.item)) {
                RecursiveCraft.LOGGER.info("{}   [Smart Select] Fallback to theoretical candidate: {}", indent, option.item.getDescription().getString());
                return option.item;
            }
        }

        // [策略 C]: 终极保底，如果连配方都没有，就选列表第一个，让系统报告“缺少基础材料”。
        if (!scoredOptions.isEmpty()) {
            return scoredOptions.get(0).item;
        }

        return Items.AIR;
    }

    /**
     * 检查一个配方的所有直接原料是否在虚拟库存中都存在（数量>0即可）。
     *
     * @param recipe           要检查的配方。
     * @param virtualInventory 虚拟库存。
     * @return 如果所有原料都至少有一个，则返回 true。
     */
    private boolean checkShallowRecipe(CraftingRecipe recipe, Map<Item, Integer> virtualInventory) {
        if (recipe == null) return false;
        for (Ingredient subIngredient : recipe.getIngredients()) {
            if (subIngredient.isEmpty()) continue;
            boolean hasSubMat = false;
            // 检查虚拟库存中是否有任何一个物品能匹配该原料
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
}
