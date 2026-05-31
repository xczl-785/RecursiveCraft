package xczl.recursivecraft.runtime.material;

import java.util.Objects;

public record NormalizationResult(NormalizationKind kind, MaterialKey key) {
    public NormalizationResult {
        Objects.requireNonNull(kind);
        if (kind == NormalizationKind.NORMALIZED && key == null) {
            throw new IllegalArgumentException("NORMALIZED requires non-null key");
        }
        if (kind == NormalizationKind.UNSUPPORTED_MATERIAL_SEMANTICS && key != null) {
            throw new IllegalArgumentException("UNSUPPORTED must not carry key");
        }
    }

    public static NormalizationResult normalized(MaterialKey key) {
        return new NormalizationResult(NormalizationKind.NORMALIZED, key);
    }
    public static NormalizationResult unsupported() {
        return new NormalizationResult(NormalizationKind.UNSUPPORTED_MATERIAL_SEMANTICS, null);
    }
}
