package xczl.recursivecraft.runtime.execution;

import xczl.recursivecraft.runtime.inventory.InventorySource;
import xczl.recursivecraft.runtime.material.MaterialKey;

import java.util.Objects;

public record ResolvedConsumption(InventorySource source, int slotIndex, MaterialKey key, int amount) {
    public ResolvedConsumption {
        Objects.requireNonNull(source);
        Objects.requireNonNull(key);
        if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be >= 0");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
    }
}
