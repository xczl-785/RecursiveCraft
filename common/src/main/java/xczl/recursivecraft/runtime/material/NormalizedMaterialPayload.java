package xczl.recursivecraft.runtime.material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class NormalizedMaterialPayload {
    private final String canonicalVersion;
    private final List<CanonicalField> fields;

    public NormalizedMaterialPayload(String canonicalVersion, List<CanonicalField> fields) {
        this.canonicalVersion = Objects.requireNonNull(canonicalVersion);
        List<CanonicalField> copy = new ArrayList<>(Objects.requireNonNull(fields));
        copy.sort(Comparator.comparing(CanonicalField::key).thenComparing(CanonicalField::value));
        this.fields = List.copyOf(copy);
    }

    public String canonicalVersion() { return canonicalVersion; }
    public List<CanonicalField> fields() { return fields; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NormalizedMaterialPayload that)) return false;
        return canonicalVersion.equals(that.canonicalVersion) && fields.equals(that.fields);
    }

    @Override
    public int hashCode() { return Objects.hash(canonicalVersion, fields); }
}
