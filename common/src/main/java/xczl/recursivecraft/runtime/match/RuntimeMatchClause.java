package xczl.recursivecraft.runtime.match;

import java.util.Map;

public record RuntimeMatchClause(String clauseId, ClauseKind kind, Map<String, String> parameters) {
    public enum ClauseKind {
        EXACT_PAYLOAD
    }
}
