package xczl.recursivecraft.runtime.match;

import net.minecraft.world.item.crafting.Ingredient;
import xczl.recursivecraft.runtime.material.MaterialKey;

import java.util.List;

public interface MaterialMatcher {
    IngredientRequirement requirementOf(Ingredient ingredient);
    CandidateMatchResult match(MaterialKey candidate, IngredientRequirement requirement);
    RequestLevelResult aggregate(List<CandidateMatchResult> candidateResults);
}
