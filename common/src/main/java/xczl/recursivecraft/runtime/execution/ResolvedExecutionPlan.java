package xczl.recursivecraft.runtime.execution;

import java.util.List;
import java.util.Objects;

public record ResolvedExecutionPlan(List<ResolvedConsumption> consumptions) {
    public ResolvedExecutionPlan {
        Objects.requireNonNull(consumptions);
        consumptions = List.copyOf(consumptions);
        if (consumptions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("consumptions must not contain null");
        }
    }
}
