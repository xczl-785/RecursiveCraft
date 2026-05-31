package xczl.recursivecraft.runtime.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.execution.ExecutionCommitResult;
import xczl.recursivecraft.runtime.execution.ResolvedConsumption;
import xczl.recursivecraft.runtime.execution.ResolvedExecutionPlan;
import xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerInventoryViewTest {
    private static final DefaultMaterialIdentityNormalizer NORMALIZER = new DefaultMaterialIdentityNormalizer();

    @BeforeAll
    static void init() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void commitExecution_shouldFailRevalidateWithoutPartialConsumeWhenIdentityChanged() {
        ItemStack redStick = stackWithVariant("red");
        ItemStack blueStick = stackWithVariant("blue");
        MaterialKey expectedKey = NORMALIZER.normalize(redStick.copy()).key();

        Inventory inventory = mock(Inventory.class);
        when(inventory.getContainerSize()).thenReturn(2);
        when(inventory.getItem(0)).thenReturn(redStick);
        when(inventory.getItem(1)).thenReturn(blueStick);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);

        PlayerInventoryView view = new PlayerInventoryView(player);
        PlayerInventorySource source = new PlayerInventorySource(player, NORMALIZER);
        ResolvedExecutionPlan plan = new ResolvedExecutionPlan(List.of(
                new ResolvedConsumption(source, 0, expectedKey, 1),
                new ResolvedConsumption(source, 1, expectedKey, 1)
        ));
        CraftingTransaction transaction = new CraftingTransaction();
        transaction.addMaterialNeed(expectedKey, 2);

        ExecutionCommitResult result = view.commitExecution(plan, transaction);

        assertEquals(ExecutionCommitResult.Status.FAILED_REVALIDATION, result.status());
        assertEquals(1, redStick.getCount());
        assertEquals(1, blueStick.getCount());
    }

    @Test
    void commitExecution_shouldFallbackOutputDropAfterSuccessfulConsume() {
        ItemStack redStick = stackWithVariant("red");
        MaterialKey expectedKey = NORMALIZER.normalize(redStick.copy()).key();

        Inventory inventory = mock(Inventory.class);
        when(inventory.getContainerSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(redStick);
        when(inventory.add(any(ItemStack.class))).thenReturn(false);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);

        PlayerInventoryView view = new PlayerInventoryView(player);
        PlayerInventorySource source = new PlayerInventorySource(player, NORMALIZER);
        ResolvedExecutionPlan plan = new ResolvedExecutionPlan(List.of(
                new ResolvedConsumption(source, 0, expectedKey, 1)
        ));
        CraftingTransaction transaction = new CraftingTransaction();
        transaction.addMaterialNeed(expectedKey, 1);
        ItemStack output = new ItemStack(Items.TORCH, 1);
        output.getOrCreateTag().putString("variant", "torch-output");
        transaction.addResolvedOutput(output);

        ExecutionCommitResult result = view.commitExecution(plan, transaction);

        assertEquals(ExecutionCommitResult.Status.SUCCESS_OUTPUT_FALLBACK, result.status());
        assertEquals(0, redStick.getCount());
        verify(player).drop(any(ItemStack.class), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void planExecution_shouldUseExactNbtIdentityForMaterialNeeds() {
        ItemStack redStick = stackWithVariant("red");
        ItemStack blueStick = stackWithVariant("blue");
        MaterialKey redKey = NORMALIZER.normalize(redStick.copy()).key();

        Inventory inventory = mock(Inventory.class);
        when(inventory.getContainerSize()).thenReturn(2);
        when(inventory.getItem(0)).thenReturn(blueStick);
        when(inventory.getItem(1)).thenReturn(redStick);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);

        PlayerInventoryView view = new PlayerInventoryView(player);
        CraftingTransaction transaction = new CraftingTransaction();
        transaction.addMaterialNeed(redKey, 1);

        ResolvedExecutionPlan plan = view.planExecution(transaction, NORMALIZER, new xczl.recursivecraft.runtime.match.DefaultMaterialMatcher(NORMALIZER));

        assertEquals(1, plan.consumptions().size());
        assertEquals(1, plan.consumptions().get(0).slotIndex());
        assertEquals(redKey, plan.consumptions().get(0).key());
        assertFalse(plan.consumptions().isEmpty());
        verify(player, never()).drop(any(ItemStack.class), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void snapshot_shouldDeduplicateMaterialKeysPerItem() {
        ItemStack redStickA = stackWithVariant("red");
        redStickA.setCount(2);
        ItemStack redStickB = stackWithVariant("red");
        redStickB.setCount(3);
        MaterialKey redKey = NORMALIZER.normalize(redStickA.copy()).key();

        Inventory inventory = mock(Inventory.class);
        when(inventory.getContainerSize()).thenReturn(2);
        when(inventory.getItem(0)).thenReturn(redStickA);
        when(inventory.getItem(1)).thenReturn(redStickB);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);

        PlayerInventoryView view = new PlayerInventoryView(player);
        VirtualInventorySnapshot snapshot = view.snapshot(NORMALIZER);

        assertEquals(5, snapshot.totals().getOrDefault(redKey, 0));
        assertEquals(1, snapshot.itemIndex().getOrDefault(Items.STICK, List.of()).size());
        assertEquals(redKey, snapshot.itemIndex().get(Items.STICK).get(0));
    }

    private static ItemStack stackWithVariant(String variant) {
        ItemStack stack = new ItemStack(Items.STICK, 1);
        stack.getOrCreateTag().putString("variant", variant);
        return stack;
    }
}
