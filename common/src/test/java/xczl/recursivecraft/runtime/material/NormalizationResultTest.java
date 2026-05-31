package xczl.recursivecraft.runtime.material;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizationResultTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void ctor_shouldRejectInvalidInvariantStates() {
        assertThrows(IllegalArgumentException.class, () -> new NormalizationResult(NormalizationKind.NORMALIZED, null));
        MaterialKey key = new MaterialKey(Items.STICK, new NormalizedMaterialPayload("v1", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new NormalizationResult(NormalizationKind.UNSUPPORTED_MATERIAL_SEMANTICS, key));
    }
}
