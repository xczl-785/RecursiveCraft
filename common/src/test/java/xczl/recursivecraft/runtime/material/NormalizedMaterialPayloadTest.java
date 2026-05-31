package xczl.recursivecraft.runtime.material;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizedMaterialPayloadTest {
    @Test
    void fields_shouldBeStableAfterNormalizationSort() {
        NormalizedMaterialPayload a = new NormalizedMaterialPayload("v1", List.of(
                new CanonicalField("b", "2"),
                new CanonicalField("a", "1")
        ));
        NormalizedMaterialPayload b = new NormalizedMaterialPayload("v1", List.of(
                new CanonicalField("a", "1"),
                new CanonicalField("b", "2")
        ));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
