package xczl.recursivecraft.runtime.material;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MaterialKeyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }
    @Test
    void sameItemAndPayload_shouldEqual() {
        NormalizedMaterialPayload p = new NormalizedMaterialPayload("v1", List.of(new CanonicalField("a", "1")));
        assertEquals(new MaterialKey(Items.STICK, p), new MaterialKey(Items.STICK, p));
    }

    @Test
    void sameItemDifferentPayload_shouldNotEqual() {
        MaterialKey a = new MaterialKey(Items.STICK, new NormalizedMaterialPayload("v1", List.of(new CanonicalField("a", "1"))));
        MaterialKey b = new MaterialKey(Items.STICK, new NormalizedMaterialPayload("v1", List.of(new CanonicalField("a", "2"))));
        assertNotEquals(a, b);
    }
}
