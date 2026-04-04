package xczl.recursivecraft.data;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostMapTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void addAndMultiply_shouldKeepExpectedTotalCost() {
        var a = Items.STONE;
        var b = Items.DIRT;

        CostMap map = new CostMap();
        map.addMaterial(a, 2.0);
        map.addMaterial(b, 3.5);

        assertEquals(5.5, map.getTotalItemCost(), 1e-9);

        CostMap doubled = map.multiply(2.0);
        assertEquals(11.0, doubled.getTotalItemCost(), 1e-9);
        assertEquals(5.5, map.getTotalItemCost(), 1e-9);
    }

    @Test
    void divideByZero_shouldReturnInfiniteCostConstant() {
        var item = Items.DIAMOND;
        CostMap map = new CostMap(item, 4.0);

        CostMap divided = map.divide(0);
        assertSame(CostMap.INFINITE_COST, divided);
        assertTrue(divided.isInfinite());
    }

    @Test
    void addWithInfiniteOperand_shouldNotMutateFiniteMap() {
        var item = Items.EMERALD;
        CostMap finite = new CostMap(item, 2.0);

        finite.add(CostMap.INFINITE_COST);

        assertEquals(2.0, finite.getTotalItemCost(), 1e-9);
        assertEquals(1, finite.baseMaterials.size());
    }
}
