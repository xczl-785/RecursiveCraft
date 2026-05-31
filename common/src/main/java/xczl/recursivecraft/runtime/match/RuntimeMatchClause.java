package xczl.recursivecraft.runtime.match;

import java.util.Map;
import java.util.Objects;

public record RuntimeMatchClause(String clauseId, ClauseKind kind, Map<String, String> parameters) {
    public RuntimeMatchClause {
        Objects.requireNonNull(clauseId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(parameters);
        parameters = Map.copyOf(parameters);
        if (clauseId.isBlank()) throw new IllegalArgumentException("clauseId must not be blank");
        if (parameters.keySet().stream().anyMatch(Objects::isNull) || parameters.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("parameters must not contain null keys/values");
        }
    }

    public enum ClauseKind {
        EXACT_PAYLOAD
    }
}
