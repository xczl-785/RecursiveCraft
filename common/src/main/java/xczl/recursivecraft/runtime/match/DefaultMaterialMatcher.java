package xczl.recursivecraft.runtime.match;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
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
        for (ItemStack stack : ingredient.getItems()) {
            NormalizationResult result = normalizer.normalize(stack);
            if (result.kind() == NormalizationKind.NORMALIZED) {
                candidates.add(result.key());
            }
        }
        return new IngredientRequirement(List.copyOf(candidates), List.of());
    }

    @Override
    public CandidateMatchResult match(MaterialKey candidate, IngredientRequirement requirement) {
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
