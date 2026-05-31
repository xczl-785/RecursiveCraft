package xczl.recursivecraft.runtime.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.runtime.material.MaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.runtime.material.NormalizationKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record VirtualInventorySnapshot(Map<MaterialKey, Integer> totals, Map<Item, List<MaterialKey>> itemIndex) {
    public VirtualInventorySnapshot {
        Objects.requireNonNull(totals);
        Objects.requireNonNull(itemIndex);
        totals = Map.copyOf(totals);
        itemIndex = Map.copyOf(itemIndex);
    }

    public static VirtualInventorySnapshot fromInventory(Inventory inventory, MaterialIdentityNormalizer normalizer) {
        Map<MaterialKey, Integer> totals = new HashMap<>();
        Map<Item, Set<MaterialKey>> indexedKeys = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            var normalized = normalizer.normalize(stack);
            if (normalized.kind() != NormalizationKind.NORMALIZED) {
                continue;
            }
            totals.merge(normalized.key(), stack.getCount(), Integer::sum);
            indexedKeys.computeIfAbsent(stack.getItem(), ignored -> new LinkedHashSet<>()).add(normalized.key());
        }

        Map<Item, List<MaterialKey>> itemIndex = new HashMap<>();
        indexedKeys.forEach((item, keys) -> itemIndex.put(item, new ArrayList<>(keys)));
        return new VirtualInventorySnapshot(totals, itemIndex);
    }
}
