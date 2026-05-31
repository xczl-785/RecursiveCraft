package xczl.recursivecraft.runtime.match;

import xczl.recursivecraft.runtime.material.MaterialKey;

import java.util.List;
import java.util.Objects;

public record IngredientRequirement(List<MaterialKey> exactCandidates,
                                    List<RuntimeMatchClause> runtimeClauses,
                                    boolean hasUnsupportedCandidates) {
    public IngredientRequirement {
        Objects.requireNonNull(exactCandidates);
        Objects.requireNonNull(runtimeClauses);
        exactCandidates = List.copyOf(exactCandidates);
        runtimeClauses = List.copyOf(runtimeClauses);
        if (exactCandidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("exactCandidates must not contain null");
        }
        if (runtimeClauses.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("runtimeClauses must not contain null");
        }
    }
}
