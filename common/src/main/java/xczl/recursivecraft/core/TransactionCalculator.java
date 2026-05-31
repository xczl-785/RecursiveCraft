package xczl.recursivecraft.core;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.BuiltInRegistries;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.match.DefaultMaterialMatcher;
import xczl.recursivecraft.runtime.match.IngredientRequirement;
import xczl.recursivecraft.runtime.match.MaterialMatcher;
import xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer;
import xczl.recursivecraft.utils.InventoryUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 合成事务计算器 (支持指定配方版).
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

    private static class CalcContext {
        final Map<Item, Integer> virtualInventory;
        final Set<Item> recursionStack;

        private CalcContext(Map<Item, Integer> virtualInventory, Set<Item> recursionStack) {
            this.virtualInventory = virtualInventory;
            this.recursionStack = recursionStack;
        }
    }

    private final CraftingPlanner.PlanningResult planningResult;
    private final Inventory playerInventory;

    private final Set<Item> uncraftableCache = new HashSet<>();
    private static final int MAX_DEPTH = 30;
    private final DefaultMaterialIdentityNormalizer normalizer = new DefaultMaterialIdentityNormalizer();
    private final MaterialMatcher matcher = new DefaultMaterialMatcher(normalizer);

    public TransactionCalculator(Inventory playerInventory) {
        this.playerInventory = playerInventory;
        this.planningResult = CraftingPlanner.getInstance().getResult();
    }

    /**
     * 计算合成事务 (自动寻路)
     */
    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget) {
        return calculate(target, amount, isFinalTarget, null);
    }

    /**
     * 计算合成事务 (支持强制指定首层配方)
     * @param forcedRecipe 如果不为 null，则第一步合成强制使用该配方
     */
    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget, CraftingRecipe forcedRecipe) {
        CalcContext context = new CalcContext(snapshotPlayerInventory(), new HashSet<>());
        logCalculationStart(target, amount, forcedRecipe);
        this.uncraftableCache.clear();

        CraftingTransaction result = calculateRecursive(target, amount, isFinalTarget, context, 1, forcedRecipe);

        RecursiveCraft.LOGGER.info("--- [CALCULATION END] ---");
        return result;
    }

    // ======================================================================
    // 核心逻辑层级 1: 递归入口与库存管理
    // ======================================================================

    private CraftingTransaction calculateRecursive(Item target, int amount, boolean isFinalTarget,
                                                   CalcContext context, int debugDepth, CraftingRecipe forcedRecipe) {
        String indent = "  ".repeat(debugDepth);
        CraftingTransaction currentTransaction = new CraftingTransaction();

        // 1. 守卫检查
        if (debugDepth > MAX_DEPTH) {
            RecursiveCraft.LOGGER.warn("{}!! Max Depth Reached for {} !!", indent, target.getDescription().getString());
            currentTransaction.addNeed(target, amount);
            return currentTransaction;
        }
        if (context.recursionStack.contains(target)) {
            RecursiveCraft.LOGGER.warn("{}!! Cycle Detected for {} !!", indent, target.getDescription().getString());
            currentTransaction.addNeed(target, amount);
            return currentTransaction;
        }
        if (uncraftableCache.contains(target)) {
            currentTransaction.addNeed(target, amount);
            return currentTransaction;
        }

        context.recursionStack.add(target);
        try {
            // 2. 优先消耗库存
            // 即使指定了配方，依然优先消耗现有成品（除非逻辑有变，目前保持原样）
            int consumed = consumeFromVirtualInventory(target, amount, isFinalTarget, context.virtualInventory, currentTransaction);

            // 3. 计算仍需合成
            int amountToCraft = amount - consumed;
            if (amountToCraft <= 0) {
                return currentTransaction;
            }

            // 4. 委托决策层
            CraftingTransaction craftTx = findAndApplyBestRecipe(target, amountToCraft, context, debugDepth, forcedRecipe);

            // 5. 判定失败缓存
            if (shouldCacheAsFailure(target, craftTx, amountToCraft)) {
                uncraftableCache.add(target);
            }

            currentTransaction.merge(craftTx);
            return currentTransaction;

        } finally {
            context.recursionStack.remove(target);
        }
    }

    // ======================================================================
    // 核心逻辑层级 2: 配方决策与快照模拟
    // ======================================================================

    private CraftingTransaction findAndApplyBestRecipe(Item target, int amountToCraft,
                                                       CalcContext context, int debugDepth,
                                                       CraftingRecipe forcedRecipe) {
        String indent = "  ".repeat(debugDepth);
        List<CraftingRecipe> candidates = collectCandidateRecipes(target, forcedRecipe, indent);

        if (candidates.isEmpty()) {
            return createNeedOnlyTransaction(target, amountToCraft);
        }

        CraftingRecipe theoreticalBest = planningResult.getPathMemo().get(target);
        sortCandidates(candidates, theoreticalBest, context.virtualInventory);

        CraftingTransaction bestFailure = tryRecipeCandidates(candidates, theoreticalBest, target, amountToCraft, context, debugDepth, forcedRecipe, indent);
        return bestFailure != null ? bestFailure : new CraftingTransaction();
    }

    // ======================================================================
    // 核心逻辑层级 3: 单个配方执行模拟
    // ======================================================================

    private CraftingTransaction simulateRecipe(CraftingRecipe recipe, int amountToCraft,
                                               CalcContext context, int debugDepth) {
        CraftingTransaction tx = new CraftingTransaction();

        int outputCount = recipe.getResultItem(null).getCount();
        if (outputCount <= 0) outputCount = 1;
        int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);

        Map<String, IngredientNeed> aggregatedNeeds = new HashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            String key = Arrays.stream(ingredient.getItems())
                    .map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                    .sorted()
                    .collect(Collectors.joining("|"));
            aggregatedNeeds.computeIfAbsent(key, k -> new IngredientNeed(ingredient, 0)).amount++;
        }

        List<IngredientNeed> needsList = new ArrayList<>(aggregatedNeeds.values());
        needsList.sort(Comparator.comparingInt(n -> n.ingredient.getItems().length));

        for (IngredientNeed need : needsList) {
            int totalNeed = need.amount * recipeRuns;
            // 解析子原料
            CraftingTransaction subTx = resolveIngredient(need.ingredient, totalNeed, context, debugDepth + 1);
            tx.merge(subTx);
        }

        int totalProduced = recipeRuns * outputCount;
        int extra = totalProduced - amountToCraft;
        if (extra > 0) {
            Item outputItem = recipe.getResultItem(null).getItem();
            tx.addProvide(outputItem, extra);
            context.virtualInventory.put(outputItem, context.virtualInventory.getOrDefault(outputItem, 0) + extra);
        }

        return tx;
    }

    // ======================================================================
    // 核心逻辑层级 4: 原料/Tag 解析
    // ======================================================================

    private CraftingTransaction resolveIngredient(Ingredient ingredient, int totalNeed,
                                                  CalcContext context, int debugDepth) {
        ItemStack[] options = ingredient.getItems();
        if (options.length == 0) return new CraftingTransaction();
        IngredientRequirement req = matcher.requirementOf(ingredient);
        if (req.hasUnsupportedCandidates()) {
            CraftingTransaction tx = new CraftingTransaction();
            tx.markUnsupported();
            return tx;
        }

        if (options.length == 1) {
            // [关键] 递归调用子项时，forcedRecipe 必须传 null，确保子材料自动寻优
            return calculateRecursive(options[0].getItem(), totalNeed, false, context, debugDepth, null);
        }

        List<Item> sortedOptions = sortIngredientOptions(options, context.virtualInventory);
        CraftingTransaction bestFailure = tryIngredientOptions(totalNeed, context, debugDepth, sortedOptions);

        return bestFailure != null ? bestFailure : new CraftingTransaction();
    }

    // ======================================================================
    // 成功判定与辅助逻辑 (保持不变)
    // ======================================================================

    private boolean isTransactionSatisfied(CraftingTransaction tx, Map<Item, Integer> startInv, Map<Item, Integer> endInv) {
        Map<Item, Integer> netDeltas = tx.getNetDeltas();

        for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
            Item item = entry.getKey();
            int delta = entry.getValue(); // 负数代表需求

            // 我们只关心需求 (Needs)
            if (delta >= 0) continue;

            int amountNeeded = -delta;

            // 计算库存实际消耗了多少
            int startCount = startInv.getOrDefault(item, 0);
            int endCount = endInv.getOrDefault(item, 0);
            int consumed = startCount - endCount;

            // 如果 消耗量 < 需求量，说明不仅吃光了库存，还有缺口 -> 失败
            if (consumed < amountNeeded) {
                return false;
            }
        }

        return true;
    }

    private double getCost(Item item) {
        Double cost = planningResult.getCostMemo().get(item);
        return cost != null ? cost : Double.MAX_VALUE;
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

    private Map<Item, Integer> snapshotPlayerInventory() {
        return InventoryUtils.snapshot(playerInventory);
    }

    private void logCalculationStart(Item target, int amount, CraftingRecipe forcedRecipe) {
        RecursiveCraft.LOGGER.info("--- [CALCULATION START] ---");
        if (forcedRecipe != null) {
            RecursiveCraft.LOGGER.info("Target: {} x{} (Forced Recipe: {})", target.getDescription().getString(), amount, forcedRecipe.getId());
        } else {
            RecursiveCraft.LOGGER.info("Target: {} x{}", target.getDescription().getString(), amount);
        }
    }

    private int consumeFromVirtualInventory(Item target, int amount, boolean isFinalTarget,
                                            Map<Item, Integer> virtualInventory, CraftingTransaction currentTransaction) {
        if (isFinalTarget) return 0;

        int amountInInventory = virtualInventory.getOrDefault(target, 0);
        if (amountInInventory > 0) {
            int consumed = Math.min(amount, amountInInventory);
            currentTransaction.addNeed(target, consumed);
            var nk = normalizer.normalize(new ItemStack(target, 1));
            if (nk.kind() == xczl.recursivecraft.runtime.material.NormalizationKind.NORMALIZED) {
                currentTransaction.addMaterialNeed(nk.key(), consumed);
            }
            virtualInventory.put(target, amountInInventory - consumed);
            return consumed;
        }
        return 0;
    }

    private List<CraftingRecipe> collectCandidateRecipes(Item target, CraftingRecipe forcedRecipe, String indent) {
        if (forcedRecipe != null) {
            RecursiveCraft.LOGGER.debug("{} [Force] Applying forced recipe: {}", indent, forcedRecipe.getId());
            return Collections.singletonList(forcedRecipe);
        }
        return new ArrayList<>(planningResult.getRecipesFor(target));
    }

    private CraftingTransaction createNeedOnlyTransaction(Item target, int amountToCraft) {
        CraftingTransaction tx = new CraftingTransaction();
        tx.addNeed(target, amountToCraft);
        var nk = normalizer.normalize(new ItemStack(target, 1));
        if (nk.kind() == xczl.recursivecraft.runtime.material.NormalizationKind.NORMALIZED) {
            tx.addMaterialNeed(nk.key(), amountToCraft);
        }
        return tx;
    }

    private void sortCandidates(List<CraftingRecipe> candidates, CraftingRecipe theoreticalBest, Map<Item, Integer> virtualInventory) {
        if (candidates.size() <= 1) return;
        candidates.sort((r1, r2) -> {
            boolean s1 = checkShallowRecipe(r1, virtualInventory);
            boolean s2 = checkShallowRecipe(r2, virtualInventory);
            if (s1 && !s2) return -1;
            if (!s1 && s2) return 1;
            if (r1 == theoreticalBest) return -1;
            if (r2 == theoreticalBest) return 1;
            return 0;
        });
    }

    private CraftingTransaction tryRecipeCandidates(List<CraftingRecipe> candidates, CraftingRecipe theoreticalBest,
                                                    Item target, int amountToCraft, CalcContext context, int debugDepth,
                                                    CraftingRecipe forcedRecipe, String indent) {
        CraftingTransaction bestFailure = null;
        for (CraftingRecipe recipe : candidates) {
            Map<Item, Integer> snapshotInventory = new HashMap<>(context.virtualInventory);
            CalcContext snapshotContext = new CalcContext(snapshotInventory, context.recursionStack);
            CraftingTransaction trialTx = simulateRecipe(recipe, amountToCraft, snapshotContext, debugDepth);

            if (isTransactionSatisfied(trialTx, context.virtualInventory, snapshotInventory)) {
                if (forcedRecipe == null) {
                    RecursiveCraft.LOGGER.info("{}   [Decision] Selected recipe for {}", indent, target.getDescription().getString());
                }
                context.virtualInventory.putAll(snapshotInventory);
                return trialTx;
            }

            if (bestFailure == null || recipe == theoreticalBest) {
                bestFailure = trialTx;
            }
        }
        return bestFailure;
    }

    private List<Item> sortIngredientOptions(ItemStack[] options, Map<Item, Integer> virtualInventory) {
        return Arrays.stream(options)
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
    }

    private CraftingTransaction tryIngredientOptions(int totalNeed, CalcContext context, int debugDepth, List<Item> sortedOptions) {
        CraftingTransaction bestFailure = null;
        for (Item itemOption : sortedOptions) {
            Map<Item, Integer> snapshotInventory = new HashMap<>(context.virtualInventory);
            CalcContext snapshotContext = new CalcContext(snapshotInventory, context.recursionStack);
            CraftingTransaction trialTx = calculateRecursive(itemOption, totalNeed, false, snapshotContext, debugDepth, null);

            if (isTransactionSatisfied(trialTx, context.virtualInventory, snapshotInventory)) {
                context.virtualInventory.putAll(snapshotInventory);
                return trialTx;
            }
            if (bestFailure == null) {
                bestFailure = trialTx;
            }
        }
        return bestFailure;
    }

}
