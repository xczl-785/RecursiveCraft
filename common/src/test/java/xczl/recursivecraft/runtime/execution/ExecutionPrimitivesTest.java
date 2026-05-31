package xczl.recursivecraft.runtime.execution;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.runtime.inventory.InventorySource;
import xczl.recursivecraft.runtime.inventory.InsertionResult;
import xczl.recursivecraft.runtime.inventory.PlayerInventoryView;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.runtime.material.NormalizedMaterialPayload;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionPrimitivesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void resolvedConsumption_shouldEnforceInvariants() {
        InventorySource src = new InventorySource() {
            @Override
            public Iterable<net.minecraft.world.item.ItemStack> snapshotStacks() { return List.of(); }
            @Override
            public boolean consumeAt(int slotIndex, MaterialKey expectedKey, int amount) { return true; }
            @Override
            public InsertionResult insert(net.minecraft.world.item.ItemStack stack) { return InsertionResult.ok(); }
        };
        MaterialKey key = new MaterialKey(Items.STICK, new NormalizedMaterialPayload("v1", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResolvedConsumption(src, -1, key, 1));
        assertThrows(IllegalArgumentException.class, () -> new ResolvedConsumption(src, 0, key, 0));
    }

    @Test
    void executionAndInsertionResult_shouldExposeStatus() {
        assertEquals(ExecutionCommitResult.Status.SUCCESS, ExecutionCommitResult.ok().status());
        assertEquals(ExecutionCommitResult.Status.FAILED_REVALIDATION, ExecutionCommitResult.failedRevalidate().status());
        assertEquals(InsertionResult.Status.INVENTORY_FULL, InsertionResult.inventoryFull().status());
    }

    @Test
    void singleSource_emptyPlanWithNeeds_shouldFail() {
        var player = mock(net.minecraft.world.entity.player.Player.class);
        var inv = mock(net.minecraft.world.entity.player.Inventory.class);
        when(player.getInventory()).thenReturn(inv);
        PlayerInventoryView view = new PlayerInventoryView(player);
        var tx = new xczl.recursivecraft.data.CraftingTransaction();
        tx.addMaterialNeed(new MaterialKey(Items.STICK, new NormalizedMaterialPayload("v1", List.of())), 1);
        ExecutionCommitResult r = view.commitExecution(new ResolvedExecutionPlan(List.of()), tx);
        assertEquals(ExecutionCommitResult.Status.FAILED_REVALIDATION, r.status());
    }
}
