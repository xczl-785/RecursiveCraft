package xczl.recursivecraft.data;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTransactionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void getNetDeltas_shouldCalculateProvideMinusNeedAndDropZeroEntries() {
        var a = Items.STICK;
        var b = Items.COBBLESTONE;
        var c = Items.OAK_PLANKS;

        CraftingTransaction tx = new CraftingTransaction();
        tx.addNeed(a, 4);
        tx.addProvide(a, 2); // net: -2

        tx.addNeed(b, 1);
        tx.addProvide(b, 1); // net: 0 -> should be removed

        tx.addProvide(c, 3); // net: +3

        Map<Item, Integer> deltas = tx.getNetDeltas();

        assertEquals(-2, deltas.get(a));
        assertEquals(3, deltas.get(c));
        assertFalse(deltas.containsKey(b));
        assertEquals(2, deltas.size());
    }

    @Test
    void merge_shouldAccumulateNeedsAndProvides() {
        var item = Items.IRON_INGOT;

        CraftingTransaction left = new CraftingTransaction();
        left.addNeed(item, 2);

        CraftingTransaction right = new CraftingTransaction();
        right.addNeed(item, 3);
        right.addProvide(item, 1);

        left.merge(right);

        assertEquals(5, left.getNeeds().get(item));
        assertEquals(1, left.getProvides().get(item));
    }

    @Test
    void addNeedAndProvide_shouldIgnoreInvalidInputs() {
        var item = Items.GOLD_INGOT;
        CraftingTransaction tx = new CraftingTransaction();

        tx.addNeed(Items.AIR, 1);
        tx.addNeed(item, 0);
        tx.addNeed(item, -1);
        tx.addProvide(Items.AIR, 1);
        tx.addProvide(item, 0);
        tx.addProvide(item, -3);

        assertTrue(tx.getNeeds().isEmpty());
        assertTrue(tx.getProvides().isEmpty());
    }

    @Test
    void addResolvedOutput_shouldPreserveItemStackIdentity() {
        CraftingTransaction tx = new CraftingTransaction();
        ItemStack output = new ItemStack(Items.STICK, 2);
        output.getOrCreateTag().putString("variant", "red-output");

        tx.addResolvedOutput(output);

        assertEquals(1, tx.getResolvedOutputs().size());
        assertEquals(2, tx.getProvides().getOrDefault(Items.STICK, 0));
        assertEquals("red-output", tx.getResolvedOutputs().get(0).getTag().getString("variant"));
    }

    @Test
    void merge_shouldUseResolvedOutputsAsOutputSourceOfTruth() {
        CraftingTransaction left = new CraftingTransaction();
        CraftingTransaction right = new CraftingTransaction();

        ItemStack red = new ItemStack(Items.STICK, 1);
        red.getOrCreateTag().putString("variant", "red-output");
        ItemStack blue = new ItemStack(Items.STICK, 1);
        blue.getOrCreateTag().putString("variant", "blue-output");

        left.addResolvedOutput(red);
        right.addResolvedOutput(blue);

        left.merge(right);

        assertEquals(2, left.getProvides().getOrDefault(Items.STICK, 0));
        assertEquals(2, left.getResolvedOutputs().size());
        assertEquals("red-output", left.getResolvedOutputs().get(0).getTag().getString("variant"));
        assertEquals("blue-output", left.getResolvedOutputs().get(1).getTag().getString("variant"));
    }
}
