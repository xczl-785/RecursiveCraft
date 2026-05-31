package xczl.recursivecraft.runtime.execution;

import xczl.recursivecraft.runtime.inventory.InventorySource;
import xczl.recursivecraft.runtime.material.MaterialKey;

public record ResolvedConsumption(InventorySource source, int slotIndex, MaterialKey key, int amount) {}
