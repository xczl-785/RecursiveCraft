package xczl.recursivecraft.runtime.inventory;

import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.runtime.material.MaterialKey;

public interface InventorySource {
    Iterable<ItemStack> snapshotStacks();
    boolean consumeAt(int slotIndex, MaterialKey expectedKey, int amount);
    InsertionResult insert(ItemStack stack);
}
