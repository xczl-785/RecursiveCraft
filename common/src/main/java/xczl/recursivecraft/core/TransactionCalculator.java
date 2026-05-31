package xczl.recursivecraft.core;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.inventory.VirtualInventorySnapshot;
import xczl.recursivecraft.runtime.match.DefaultMaterialMatcher;
import xczl.recursivecraft.runtime.match.IngredientRequirement;
import xczl.recursivecraft.runtime.match.MaterialMatcher;
import xczl.recursivecraft.runtime.match.RequestLevelKind;
import xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.runtime.material.NormalizationKind;
import xczl.recursivecraft.runtime.material.NormalizationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 合成事务计算器 (支持指定配方版).
 */
public class TransactionCalculator {
    private record MaterialRequestKey(Item item, MaterialKey desiredKey) {
        private MaterialRequestKey {
            Objects.requireNonNull(item);
        }
    }

    private static class IngredientNeed {
        final IngredientRequirement requirement;
        int amount;

        private IngredientNeed(IngredientRequirement requirement, int amount) {
            this.requirement = requirement;
            this.amount = amount;
        }
    }

    private static class CalcContext {
        VirtualInventorySnapshot virtualInventory;
        final Set<MaterialRequestKey> recursionStack;

        private CalcContext(VirtualInventorySnapshot virtualInventory, Set<MaterialRequestKey> recursionStack) {
            this.virtualInventory = virtualInventory;
            this.recursionStack = recursionStack;
        }
    }

    private record AttemptResult(CraftingTransaction transaction, RequestLevelKind kind) {}

    private static final int MAX_DEPTH = 30;

    private final CraftingPlanner.PlanningResult planningResult;
    private final Inventory playerInventory;
    private final Set<MaterialRequestKey> uncraftableCache = new HashSet<>();
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
     */
    public CraftingTransaction calculate(Item target, int amount, boolean isFinalTarget, CraftingRecipe forcedRecipe) {
        CalcContext context = new CalcContext(snapshotPlayerInventory(), new HashSet<>());
        logCalculationStart(target, amount, forcedRecipe);
        uncraftableCache.clear();

        AttemptResult result = calculateRecursive(target, amount, isFinalTarget, context, 1, forcedRecipe, null);

        RecursiveCraft.LOGGER.info("--- [CALCULATION END] ---");
        return result.transaction();
    }

    private AttemptResult calculateRecursive(Item target, int amount, boolean isFinalTarget,
                                             CalcContext context, int debugDepth,
                                             CraftingRecipe forcedRecipe, MaterialKey desiredKey) {
        CraftingTransaction currentTransaction = new CraftingTransaction();
        MaterialRequestKey requestKey = requestKey(target, desiredKey);

        if (debugDepth > MAX_DEPTH || context.recursionStack.contains(requestKey) || uncraftableCache.contains(requestKey)) {
            return missing(createNeedOnlyTransaction(target, amount, desiredKey));
        }

        context.recursionStack.add(requestKey);
        try {
            int consumed = consumeFromVirtualInventory(target, desiredKey, amount, isFinalTarget, context, currentTransaction);
            int amountToCraft = amount - consumed;
            if (amountToCraft <= 0) {
                return satisfied(currentTransaction);
            }

            AttemptResult craftResult = findAndApplyBestRecipe(target, desiredKey, amountToCraft, isFinalTarget, context, debugDepth, forcedRecipe);
            currentTransaction.merge(craftResult.transaction());

            if (craftResult.kind() != RequestLevelKind.SATISFIED
                    && shouldCacheAsFailure(target, desiredKey, craftResult, amountToCraft)) {
                uncraftableCache.add(requestKey);
            }
            return new AttemptResult(currentTransaction, craftResult.kind());
        } finally {
            context.recursionStack.remove(requestKey);
        }
    }

    private AttemptResult findAndApplyBestRecipe(Item target, MaterialKey desiredKey, int amountToCraft,
                                                 boolean isFinalTarget, CalcContext context, int debugDepth, CraftingRecipe forcedRecipe) {
        String indent = "  ".repeat(debugDepth);
        List<CraftingRecipe> candidates = collectCandidateRecipes(target, forcedRecipe, indent);
        if (candidates.isEmpty()) {
            return missing(createNeedOnlyTransaction(target, amountToCraft, desiredKey));
        }

        CraftingRecipe theoreticalBest = planningResult.getPathMemo().get(target);
        sortCandidates(candidates, theoreticalBest, desiredKey, context.virtualInventory);
        return tryRecipeCandidates(candidates, theoreticalBest, target, desiredKey, amountToCraft, isFinalTarget, context, debugDepth, forcedRecipe, indent);
    }

    private AttemptResult simulateRecipe(CraftingRecipe recipe, Item target, MaterialKey desiredKey,
                                         int amountToCraft, boolean isFinalTarget, CalcContext context, int debugDepth) {
        CraftingTransaction tx = new CraftingTransaction();
        NormalizationResult outputIdentity = normalizeRecipeOutput(recipe);
        if (outputIdentity.kind() != NormalizationKind.NORMALIZED) {
            tx.markUnsupported();
            return unsupported(tx);
        }

        if (desiredKey != null && !desiredKey.equals(outputIdentity.key())) {
            return missing(createNeedOnlyTransaction(target, amountToCraft, desiredKey));
        }

        int outputCount = recipe.getResultItem(null).getCount();
        if (outputCount <= 0) {
            outputCount = 1;
        }
        int recipeRuns = (int) Math.ceil((double) amountToCraft / outputCount);

        List<IngredientNeed> needsList = aggregateIngredientNeeds(recipe);
        for (IngredientNeed need : needsList) {
            int totalNeed = need.amount * recipeRuns;
            AttemptResult subTx = resolveIngredient(need.requirement, totalNeed, context, debugDepth + 1);
            tx.merge(subTx.transaction());
            if (subTx.kind() != RequestLevelKind.SATISFIED) {
                return new AttemptResult(tx, subTx.kind());
            }
        }

        int totalProduced = recipeRuns * outputCount;
        appendResolvedOutputs(tx, recipe.getResultItem(null), isFinalTarget ? totalProduced : (totalProduced - amountToCraft));

        int extra = totalProduced - amountToCraft;
        if (extra > 0) {
            addToVirtualInventory(context, outputIdentity.key(), extra);
        }

        return satisfied(tx);
    }

    private AttemptResult resolveIngredient(IngredientRequirement requirement, int totalNeed,
                                            CalcContext context, int debugDepth) {
        if (requirement.hasUnsupportedCandidates() && requirement.exactCandidates().isEmpty()) {
            CraftingTransaction tx = new CraftingTransaction();
            tx.markUnsupported();
            return unsupported(tx);
        }
        if (requirement.exactCandidates().isEmpty()) {
            return missing(new CraftingTransaction());
        }

        List<MaterialKey> sortedOptions = sortIngredientOptions(requirement, context.virtualInventory, totalNeed);
        AttemptResult bestFailure = null;
        boolean sawUnsupported = false;

        for (MaterialKey candidate : sortedOptions) {
            CalcContext snapshotContext = cloneContext(context);
            CraftingTransaction trialTx = new CraftingTransaction();

            int consumed = consumeExactCandidate(candidate, totalNeed, snapshotContext, trialTx);
            int remaining = totalNeed - consumed;
            AttemptResult recursiveResult = remaining > 0
                    ? calculateRecursive(candidate.item(), remaining, false, snapshotContext, debugDepth, null, candidate)
                    : satisfied(new CraftingTransaction());
            trialTx.merge(recursiveResult.transaction());

            if (recursiveResult.kind() == RequestLevelKind.SATISFIED) {
                context.virtualInventory = snapshotContext.virtualInventory;
                return satisfied(trialTx);
            }

            if (bestFailure == null) {
                bestFailure = new AttemptResult(trialTx, recursiveResult.kind());
            }
            sawUnsupported |= recursiveResult.kind() == RequestLevelKind.UNSUPPORTED;
        }

        if (bestFailure == null) {
            bestFailure = missing(createNeedOnlyTransaction(
                    sortedOptions.get(0).item(),
                    totalNeed,
                    sortedOptions.get(0)
            ));
        }

        if (sawUnsupported || requirement.hasUnsupportedCandidates()) {
            bestFailure.transaction().markUnsupported();
            return unsupported(bestFailure.transaction());
        }
        return missing(bestFailure.transaction());
    }

    private NormalizationResult normalizeRecipeOutput(CraftingRecipe recipe) {
        ItemStack result = recipe.getResultItem(null);
        if (result == null || result.isEmpty()) {
            return NormalizationResult.unsupported();
        }
        return normalizer.normalize(result.copy());
    }

    private List<IngredientNeed> aggregateIngredientNeeds(CraftingRecipe recipe) {
        Map<IngredientRequirement, Integer> aggregatedNeeds = new HashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            IngredientRequirement requirement = matcher.requirementOf(ingredient);
            aggregatedNeeds.merge(requirement, 1, Integer::sum);
        }

        List<IngredientNeed> needsList = new ArrayList<>();
        aggregatedNeeds.forEach((requirement, amount) -> needsList.add(new IngredientNeed(requirement, amount)));
        needsList.sort(Comparator
                .comparingInt((IngredientNeed need) -> need.requirement.exactCandidates().size())
                .thenComparing(need -> stableRequirementKey(need.requirement)));
        return needsList;
    }

    private List<MaterialKey> sortIngredientOptions(IngredientRequirement requirement,
                                                    VirtualInventorySnapshot virtualInventory,
                                                    int totalNeed) {
        LinkedHashSet<MaterialKey> uniqueCandidates = new LinkedHashSet<>();
        for (MaterialKey candidate : requirement.exactCandidates()) {
            if (candidate != null) {
                uniqueCandidates.add(candidate);
            }
        }
        return uniqueCandidates.stream()
                .sorted((left, right) -> compareIngredientCandidates(left, right, virtualInventory, totalNeed))
                .toList();
    }

    private int compareIngredientCandidates(MaterialKey left, MaterialKey right,
                                            VirtualInventorySnapshot virtualInventory, int totalNeed) {
        int leftAvailable = virtualInventory.totals().getOrDefault(left, 0);
        int rightAvailable = virtualInventory.totals().getOrDefault(right, 0);

        boolean leftEnough = leftAvailable >= totalNeed;
        boolean rightEnough = rightAvailable >= totalNeed;
        if (leftEnough != rightEnough) {
            return leftEnough ? -1 : 1;
        }
        if (leftAvailable != rightAvailable) {
            return Integer.compare(rightAvailable, leftAvailable);
        }

        boolean leftShallow = checkShallowItem(left.item(), virtualInventory);
        boolean rightShallow = checkShallowItem(right.item(), virtualInventory);
        if (leftShallow != rightShallow) {
            return leftShallow ? -1 : 1;
        }

        int costCompare = Double.compare(getCost(left.item()), getCost(right.item()));
        if (costCompare != 0) {
            return costCompare;
        }

        return left.toString().compareTo(right.toString());
    }

    private void sortCandidates(List<CraftingRecipe> candidates, CraftingRecipe theoreticalBest,
                                MaterialKey desiredKey, VirtualInventorySnapshot virtualInventory) {
        if (candidates.size() <= 1) {
            return;
        }
        candidates.sort((left, right) -> {
            boolean leftMatchesIdentity = recipeMatchesDesiredIdentity(left, desiredKey);
            boolean rightMatchesIdentity = recipeMatchesDesiredIdentity(right, desiredKey);
            if (leftMatchesIdentity != rightMatchesIdentity) {
                return leftMatchesIdentity ? -1 : 1;
            }

            boolean leftShallow = checkShallowRecipe(left, virtualInventory);
            boolean rightShallow = checkShallowRecipe(right, virtualInventory);
            if (leftShallow != rightShallow) {
                return leftShallow ? -1 : 1;
            }
            if (left == theoreticalBest) {
                return -1;
            }
            if (right == theoreticalBest) {
                return 1;
            }
            return 0;
        });
    }

    private AttemptResult tryRecipeCandidates(List<CraftingRecipe> candidates, CraftingRecipe theoreticalBest,
                                              Item target, MaterialKey desiredKey, int amountToCraft,
                                              boolean isFinalTarget, CalcContext context, int debugDepth,
                                              CraftingRecipe forcedRecipe, String indent) {
        CraftingTransaction bestFailure = null;
        boolean sawUnsupported = false;

        for (CraftingRecipe recipe : candidates) {
            CalcContext snapshotContext = cloneContext(context);
            AttemptResult trial = simulateRecipe(recipe, target, desiredKey, amountToCraft, isFinalTarget, snapshotContext, debugDepth);

            if (trial.kind() == RequestLevelKind.SATISFIED) {
                if (forcedRecipe == null) {
                    RecursiveCraft.LOGGER.info("{}   [Decision] Selected recipe for {}", indent, target.getDescription().getString());
                }
                context.virtualInventory = snapshotContext.virtualInventory;
                return trial;
            }

            if (bestFailure == null || recipe == theoreticalBest) {
                bestFailure = trial.transaction();
            }
            sawUnsupported |= trial.kind() == RequestLevelKind.UNSUPPORTED;
        }

        if (bestFailure == null) {
            bestFailure = createNeedOnlyTransaction(target, amountToCraft, desiredKey);
        }
        if (sawUnsupported) {
            bestFailure.markUnsupported();
            return unsupported(bestFailure);
        }
        return missing(bestFailure);
    }

    private boolean recipeMatchesDesiredIdentity(CraftingRecipe recipe, MaterialKey desiredKey) {
        if (desiredKey == null) {
            return true;
        }
        NormalizationResult outputIdentity = normalizeRecipeOutput(recipe);
        return outputIdentity.kind() == NormalizationKind.NORMALIZED && desiredKey.equals(outputIdentity.key());
    }

    private boolean checkShallowRecipe(CraftingRecipe recipe, VirtualInventorySnapshot virtualInventory) {
        if (recipe == null) {
            return false;
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            IngredientRequirement requirement = matcher.requirementOf(ingredient);
            if (requirement.hasUnsupportedCandidates()) {
                return false;
            }
            boolean hasSubMaterial = requirement.exactCandidates().stream()
                    .anyMatch(key -> virtualInventory.totals().getOrDefault(key, 0) > 0);
            if (!hasSubMaterial) {
                return false;
            }
        }
        return true;
    }

    private boolean checkShallowItem(Item item, VirtualInventorySnapshot virtualInventory) {
        return checkShallowRecipe(planningResult.getPathMemo().get(item), virtualInventory);
    }

    private int consumeFromVirtualInventory(Item target, MaterialKey desiredKey, int amount, boolean isFinalTarget,
                                            CalcContext context, CraftingTransaction currentTransaction) {
        if (isFinalTarget) {
            return 0;
        }

        if (desiredKey != null) {
            return consumeExactCandidate(desiredKey, amount, context, currentTransaction);
        }

        int remaining = amount;
        for (MaterialKey candidateKey : keysForItem(context.virtualInventory, target)) {
            if (remaining <= 0) {
                break;
            }
            int consumed = consumeExactCandidate(candidateKey, remaining, context, currentTransaction);
            remaining -= consumed;
        }
        return amount - remaining;
    }

    private int consumeExactCandidate(MaterialKey key, int amount, CalcContext context, CraftingTransaction transaction) {
        int available = context.virtualInventory.totals().getOrDefault(key, 0);
        if (available <= 0) {
            return 0;
        }
        int consumed = Math.min(amount, available);
        transaction.addMaterialNeed(key, consumed);
        addToVirtualInventory(context, key, -consumed);
        return consumed;
    }

    private List<CraftingRecipe> collectCandidateRecipes(Item target, CraftingRecipe forcedRecipe, String indent) {
        if (forcedRecipe != null) {
            RecursiveCraft.LOGGER.debug("{} [Force] Applying forced recipe: {}", indent, forcedRecipe.getId());
            return Collections.singletonList(forcedRecipe);
        }
        return new ArrayList<>(planningResult.getRecipesFor(target));
    }

    private CraftingTransaction createNeedOnlyTransaction(Item target, int amountToCraft, MaterialKey desiredKey) {
        CraftingTransaction tx = new CraftingTransaction();
        tx.addNeed(target, amountToCraft);
        if (desiredKey != null) {
            tx.addMaterialNeed(desiredKey, amountToCraft);
        }
        return tx;
    }

    private double getCost(Item item) {
        Double cost = planningResult.getCostMemo().get(item);
        return cost != null ? cost : Double.MAX_VALUE;
    }

    private boolean shouldCacheAsFailure(Item target, MaterialKey desiredKey, AttemptResult result, int amountRequested) {
        if (result.kind() != RequestLevelKind.MISSING) {
            return false;
        }
        int deficit = desiredKey != null
                ? result.transaction().getMaterialNeeds().getOrDefault(desiredKey, 0)
                : result.transaction().getNeeds().getOrDefault(target, 0);
        return deficit >= amountRequested;
    }

    private MaterialRequestKey requestKey(Item target, MaterialKey desiredKey) {
        return new MaterialRequestKey(target, desiredKey);
    }

    private void appendResolvedOutputs(CraftingTransaction transaction, ItemStack template, int totalAmount) {
        if (template == null || template.isEmpty() || totalAmount <= 0) {
            return;
        }
        int remaining = totalAmount;
        int maxStackSize = template.getMaxStackSize();
        while (remaining > 0) {
            int split = Math.min(remaining, maxStackSize);
            ItemStack copy = template.copy();
            copy.setCount(split);
            transaction.addResolvedOutput(copy);
            remaining -= split;
        }
    }

    private VirtualInventorySnapshot snapshotPlayerInventory() {
        return VirtualInventorySnapshot.fromInventory(playerInventory, normalizer);
    }

    private CalcContext cloneContext(CalcContext source) {
        return new CalcContext(copySnapshot(source.virtualInventory), source.recursionStack);
    }

    private VirtualInventorySnapshot copySnapshot(VirtualInventorySnapshot source) {
        Map<MaterialKey, Integer> totals = new HashMap<>(source.totals());
        Map<Item, List<MaterialKey>> itemIndex = new HashMap<>();
        source.itemIndex().forEach((item, keys) -> itemIndex.put(item, new ArrayList<>(keys)));
        return new VirtualInventorySnapshot(totals, itemIndex);
    }

    private List<MaterialKey> keysForItem(VirtualInventorySnapshot snapshot, Item item) {
        List<MaterialKey> keys = snapshot.itemIndex().getOrDefault(item, List.of());
        List<MaterialKey> ordered = new ArrayList<>(keys);
        ordered.sort((left, right) -> Integer.compare(
                snapshot.totals().getOrDefault(right, 0),
                snapshot.totals().getOrDefault(left, 0)
        ));
        return ordered;
    }

    private void addToVirtualInventory(CalcContext context, MaterialKey key, int delta) {
        Map<MaterialKey, Integer> totals = new HashMap<>(context.virtualInventory.totals());
        Map<Item, List<MaterialKey>> itemIndex = new HashMap<>();
        context.virtualInventory.itemIndex().forEach((item, keys) -> itemIndex.put(item, new ArrayList<>(keys)));

        int updated = totals.getOrDefault(key, 0) + delta;
        if (updated > 0) {
            totals.put(key, updated);
            itemIndex.computeIfAbsent(key.item(), ignored -> new ArrayList<>());
            if (!itemIndex.get(key.item()).contains(key)) {
                itemIndex.get(key.item()).add(key);
            }
        } else {
            totals.remove(key);
            List<MaterialKey> keys = itemIndex.get(key.item());
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    itemIndex.remove(key.item());
                }
            }
        }

        context.virtualInventory = new VirtualInventorySnapshot(totals, itemIndex);
    }

    private String stableRequirementKey(IngredientRequirement requirement) {
        StringBuilder builder = new StringBuilder();
        for (MaterialKey candidate : requirement.exactCandidates()) {
            builder.append(candidate).append(';');
        }
        builder.append('|').append(requirement.hasUnsupportedCandidates());
        return builder.toString();
    }

    private void logCalculationStart(Item target, int amount, CraftingRecipe forcedRecipe) {
        RecursiveCraft.LOGGER.info("--- [CALCULATION START] ---");
        if (forcedRecipe != null) {
            RecursiveCraft.LOGGER.info("Target: {} x{} (Forced Recipe: {})", target.getDescription().getString(), amount, forcedRecipe.getId());
        } else {
            RecursiveCraft.LOGGER.info("Target: {} x{}", target.getDescription().getString(), amount);
        }
    }

    private AttemptResult satisfied(CraftingTransaction transaction) {
        return new AttemptResult(transaction, RequestLevelKind.SATISFIED);
    }

    private AttemptResult missing(CraftingTransaction transaction) {
        return new AttemptResult(transaction, RequestLevelKind.MISSING);
    }

    private AttemptResult unsupported(CraftingTransaction transaction) {
        transaction.markUnsupported();
        return new AttemptResult(transaction, RequestLevelKind.UNSUPPORTED);
    }
}
