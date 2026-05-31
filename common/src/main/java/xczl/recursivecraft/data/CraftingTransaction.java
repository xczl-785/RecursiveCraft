package xczl.recursivecraft.data;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xczl.recursivecraft.runtime.material.MaterialKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CraftingTransaction {
    private final Map<Item, Integer> needs = new HashMap<>();
    private final Map<MaterialKey, Integer> materialNeeds = new HashMap<>();
    private final List<ItemStack> resolvedOutputs = new ArrayList<>();
    private boolean unsupported;

    public Map<Item, Integer> getNeeds() {
        return needs;
    }

    public Map<Item, Integer> getProvides() {
        Map<Item, Integer> provides = new HashMap<>();
        for (ItemStack output : resolvedOutputs) {
            provides.merge(output.getItem(), output.getCount(), Integer::sum);
        }
        return Map.copyOf(provides);
    }

    public Map<MaterialKey, Integer> getMaterialNeeds() {
        return materialNeeds;
    }

    public List<ItemStack> getResolvedOutputs() {
        return List.copyOf(resolvedOutputs);
    }

    public boolean isUnsupported() {
        return unsupported;
    }

    public void markUnsupported() {
        this.unsupported = true;
    }

    public void addNeed(Item item, int amount) {
        if (item == Items.AIR || amount <= 0) {
            return;
        }
        needs.put(item, needs.getOrDefault(item, 0) + amount);
    }

    public void addMaterialNeed(MaterialKey key, int amount) {
        if (key == null || amount <= 0) {
            return;
        }
        materialNeeds.put(key, materialNeeds.getOrDefault(key, 0) + amount);
    }

    public void addResolvedOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == Items.AIR || stack.getCount() <= 0) {
            return;
        }
        ItemStack copy = stack.copy();
        resolvedOutputs.add(copy);
    }

    public void merge(CraftingTransaction other) {
        other.needs.forEach(this::addNeed);
        other.materialNeeds.forEach(this::addMaterialNeed);
        other.resolvedOutputs.forEach(this::addResolvedOutput);
        this.unsupported = this.unsupported || other.unsupported;
    }
}
