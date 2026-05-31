package xczl.recursivecraft.runtime.match;

import xczl.recursivecraft.runtime.material.MaterialKey;

import java.util.List;

public record IngredientRequirement(List<MaterialKey> exactCandidates, List<RuntimeMatchClause> runtimeClauses) {}
