package xczl.recursivecraft.runtime.match;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.runtime.material.CanonicalField;
import xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.runtime.material.NormalizationResult;
import xczl.recursivecraft.runtime.material.NormalizedMaterialPayload;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultMaterialMatcherTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void aggregate_shouldKeepUnsupportedSemantics() {
        DefaultMaterialMatcher matcher = new DefaultMaterialMatcher(new DefaultMaterialIdentityNormalizer());
        RequestLevelResult result = matcher.aggregate(List.of(
                new CandidateMatchResult(CandidateMatchKind.REJECTED_BY_IDENTITY),
                new CandidateMatchResult(CandidateMatchKind.UNSUPPORTED_MATERIAL_SEMANTICS)
        ));
        assertEquals(RequestLevelKind.UNSUPPORTED, result.kind());
    }

    @Test
    void match_withUnsupportedRequirement_shouldReturnUnsupported() {
        DefaultMaterialMatcher matcher = new DefaultMaterialMatcher(new DefaultMaterialIdentityNormalizer());
        MaterialKey candidate = new MaterialKey(Items.STICK, new NormalizedMaterialPayload("v1", List.of()));
        IngredientRequirement requirement = new IngredientRequirement(List.of(candidate), List.of(), true);
        assertEquals(CandidateMatchKind.UNSUPPORTED_MATERIAL_SEMANTICS, matcher.match(candidate, requirement).kind());
    }

    @Test
    void match_withExactPayloadClause_shouldRejectDifferentPayload() {
        MaterialIdentityNormalizer stub = stack -> NormalizationResult.normalized(
                new MaterialKey(stack.getItem(), new NormalizedMaterialPayload("v1", List.of()))
        );
        DefaultMaterialMatcher matcher = new DefaultMaterialMatcher(stub);
        MaterialKey candidate = new MaterialKey(Items.STICK, new NormalizedMaterialPayload("v1", List.of(new CanonicalField("x", "1"))));
        MaterialKey requirementKey = new MaterialKey(Items.STICK, new NormalizedMaterialPayload("v1", List.of(new CanonicalField("x", "2"))));
        IngredientRequirement requirement = new IngredientRequirement(
                List.of(requirementKey),
                List.of(new RuntimeMatchClause("p", RuntimeMatchClause.ClauseKind.EXACT_PAYLOAD, Map.of())),
                false
        );
        assertEquals(CandidateMatchKind.REJECTED_BY_IDENTITY, matcher.match(candidate, requirement).kind());
    }

    @Test
    void requirement_constructorShouldRejectNullEntries() {
        assertThrows(NullPointerException.class, () -> new IngredientRequirement(List.of((MaterialKey) null), List.of(), false));
    }
}
