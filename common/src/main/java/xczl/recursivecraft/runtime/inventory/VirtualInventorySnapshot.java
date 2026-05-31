package xczl.recursivecraft.runtime.inventory;

import net.minecraft.world.item.Item;
import xczl.recursivecraft.runtime.material.MaterialKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record VirtualInventorySnapshot(Map<MaterialKey, Integer> totals, Map<Item, List<MaterialKey>> itemIndex) {
    public VirtualInventorySnapshot {
        Objects.requireNonNull(totals);
        Objects.requireNonNull(itemIndex);
        totals = Map.copyOf(totals);
        itemIndex = Map.copyOf(itemIndex);
    }
}
