package xczl.recursivecraft.runtime.material;

public record NormalizationResult(NormalizationKind kind, MaterialKey key) {
    public static NormalizationResult normalized(MaterialKey key) {
        return new NormalizationResult(NormalizationKind.NORMALIZED, key);
    }
    public static NormalizationResult unsupported() {
        return new NormalizationResult(NormalizationKind.UNSUPPORTED_MATERIAL_SEMANTICS, null);
    }
}
