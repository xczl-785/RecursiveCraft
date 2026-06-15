package xczl.recursivecraft.runtime.match;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import xczl.recursivecraft.runtime.material.MaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.runtime.material.NormalizationKind;
import xczl.recursivecraft.runtime.material.NormalizationResult;

import java.util.ArrayList;
import java.util.List;

public class DefaultMaterialMatcher implements MaterialMatcher {
    private final MaterialIdentityNormalizer normalizer;

    public DefaultMaterialMatcher(MaterialIdentityNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public IngredientRequirement requirementOf(Ingredient ingredient) {
        List<MaterialKey> candidates = new ArrayList<>();
        boolean hasUnsupported = false;
        // 1.21.8: Ingredient.items() returns Stream<Holder<Item>> without component data.
        // Try to extract full ItemStack from display() first for component-aware normalization.
        List<ItemStack> stacks = extractStacksFromIngredient(ingredient);
        for (ItemStack stack : stacks) {
            NormalizationResult result = normalizer.normalize(stack);
            if (result.kind() == NormalizationKind.NORMALIZED) {
                candidates.add(result.key());
            } else if (result.kind() == NormalizationKind.UNSUPPORTED_MATERIAL_SEMANTICS) {
                hasUnsupported = true;
            }
        }
        return new IngredientRequirement(List.copyOf(candidates), List.of(), hasUnsupported);
    }

    /**
     * 从 Ingredient 中提取 ItemStack 列表。
     * 优先使用 display() 中的 ItemStackSlotDisplay 以保留组件数据；
     * 回退到 items() 的 Holder<Item>（无组件数据）。
     */
    private static List<ItemStack> extractStacksFromIngredient(Ingredient ingredient) {
        SlotDisplay display = ingredient.display();
        List<ItemStack> fromDisplay = resolveDisplayStacks(display);
        if (!fromDisplay.isEmpty()) {
            return fromDisplay;
        }
        // Fallback: plain stacks from Holder<Item>
        return ingredient.items().map(h -> new ItemStack(h)).toList();
    }

    private static List<ItemStack> resolveDisplayStacks(SlotDisplay display) {
        if (display instanceof SlotDisplay.ItemStackSlotDisplay iss) {
            return List.of(iss.stack());
        }
        if (display instanceof SlotDisplay.ItemSlotDisplay isd) {
            return List.of(new ItemStack(isd.item()));
        }
        if (display instanceof SlotDisplay.TagSlotDisplay) {
            return List.of(); // tags: fall back to items() stream
        }
        if (display instanceof SlotDisplay.Composite composite) {
            List<ItemStack> result = new ArrayList<>();
            for (SlotDisplay child : composite.contents()) {
                result.addAll(resolveDisplayStacks(child));
            }
            return result;
        }
        return List.of();
    }

    @Override
    public CandidateMatchResult match(MaterialKey candidate, IngredientRequirement requirement) {
        if (requirement.hasUnsupportedCandidates()) {
            return new CandidateMatchResult(CandidateMatchKind.UNSUPPORTED_MATERIAL_SEMANTICS);
        }
        for (RuntimeMatchClause clause : requirement.runtimeClauses()) {
            if (clause.kind() == RuntimeMatchClause.ClauseKind.EXACT_PAYLOAD &&
                    !requirement.exactCandidates().stream().anyMatch(k -> k.payload().equals(candidate.payload()))) {
                return new CandidateMatchResult(CandidateMatchKind.REJECTED_BY_IDENTITY);
            }
        }
        if (requirement.exactCandidates().contains(candidate)) {
            return new CandidateMatchResult(CandidateMatchKind.MATCHED);
        }
        return new CandidateMatchResult(CandidateMatchKind.REJECTED_BY_IDENTITY);
    }

    @Override
    public RequestLevelResult aggregate(List<CandidateMatchResult> candidateResults) {
        boolean hasMatched = candidateResults.stream().anyMatch(r -> r.kind() == CandidateMatchKind.MATCHED);
        if (hasMatched) return new RequestLevelResult(RequestLevelKind.SATISFIED);
        boolean hasUnsupported = candidateResults.stream().anyMatch(r -> r.kind() == CandidateMatchKind.UNSUPPORTED_MATERIAL_SEMANTICS);
        if (hasUnsupported) return new RequestLevelResult(RequestLevelKind.UNSUPPORTED);
        return new RequestLevelResult(RequestLevelKind.MISSING);
    }
}
