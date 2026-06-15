package xczl.recursivecraft.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingTransactionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void legacyExecutionBridgeMethods_shouldNotBeExposed() {
        assertThrows(NoSuchMethodException.class, () -> CraftingTransaction.class.getDeclaredMethod("getNetDeltas"));
        assertThrows(NoSuchMethodException.class, () -> CraftingTransaction.class.getDeclaredMethod("execute", net.minecraft.world.entity.player.Player.class));
        assertThrows(NoSuchMethodException.class, () -> CraftingTransaction.class.getDeclaredMethod("addProvide", Item.class, int.class));
    }

    @Test
    void merge_shouldAccumulateNeedsAndResolvedOutputs() {
        var item = Items.IRON_INGOT;

        CraftingTransaction left = new CraftingTransaction();
        left.addNeed(item, 2);

        CraftingTransaction right = new CraftingTransaction();
        right.addNeed(item, 3);
        right.addResolvedOutput(new ItemStack(item, 1));

        left.merge(right);

        assertEquals(5, left.getNeeds().get(item));
        assertEquals(1, left.getProvides().get(item));
    }

    @Test
    void addNeedAndResolvedOutput_shouldIgnoreInvalidInputs() {
        var item = Items.GOLD_INGOT;
        CraftingTransaction tx = new CraftingTransaction();

        tx.addNeed(Items.AIR, 1);
        tx.addNeed(item, 0);
        tx.addNeed(item, -1);
        tx.addResolvedOutput(new ItemStack(Items.AIR, 1));
        tx.addResolvedOutput(new ItemStack(item, 0));
        tx.addResolvedOutput(new ItemStack(item, -3));

        assertTrue(tx.getNeeds().isEmpty());
        assertTrue(tx.getProvides().isEmpty());
    }

    @Test
    void addResolvedOutput_shouldPreserveItemStackIdentity() {
        CraftingTransaction tx = new CraftingTransaction();
        ItemStack output = new ItemStack(Items.STICK, 2);
        setVariant(output, "red-output");

        tx.addResolvedOutput(output);

        assertEquals(1, tx.getResolvedOutputs().size());
        assertEquals(2, tx.getProvides().getOrDefault(Items.STICK, 0));
        assertEquals("red-output", variant(tx.getResolvedOutputs().get(0)));
    }

    @Test
    void merge_shouldUseResolvedOutputsAsOutputSourceOfTruth() {
        CraftingTransaction left = new CraftingTransaction();
        CraftingTransaction right = new CraftingTransaction();

        ItemStack red = new ItemStack(Items.STICK, 1);
        setVariant(red, "red-output");
        ItemStack blue = new ItemStack(Items.STICK, 1);
        setVariant(blue, "blue-output");

        left.addResolvedOutput(red);
        right.addResolvedOutput(blue);

        left.merge(right);

        assertEquals(2, left.getProvides().getOrDefault(Items.STICK, 0));
        assertEquals(2, left.getResolvedOutputs().size());
        assertEquals("red-output", variant(left.getResolvedOutputs().get(0)));
        assertEquals("blue-output", variant(left.getResolvedOutputs().get(1)));
    }

    private static void setVariant(ItemStack stack, String variant) {
        CompoundTag tag = new CompoundTag();
        tag.putString("variant", variant);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static String variant(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? "" : customData.copyTag().getString("variant").orElse("");
    }
}
