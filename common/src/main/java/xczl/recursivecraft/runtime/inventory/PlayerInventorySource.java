package xczl.recursivecraft.runtime.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.runtime.material.MaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.runtime.material.NormalizationKind;

import java.util.ArrayList;
import java.util.List;

public class PlayerInventorySource implements InventorySource {
    private final Player player;
    private final MaterialIdentityNormalizer normalizer;

    public PlayerInventorySource(Player player, MaterialIdentityNormalizer normalizer) {
        this.player = player;
        this.normalizer = normalizer;
    }

    @Override
    public Iterable<ItemStack> snapshotStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            stacks.add(player.getInventory().getItem(i).copy());
        }
        return stacks;
    }

    @Override
    public boolean consumeAt(int slotIndex, MaterialKey expectedKey, int amount) {
        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (stack.isEmpty() || stack.getCount() < amount) return false;
        var r = normalizer.normalize(stack);
        if (r.kind() != NormalizationKind.NORMALIZED || !r.key().equals(expectedKey)) return false;
        stack.shrink(amount);
        return true;
    }

    @Override
    public InsertionResult insert(ItemStack stack) {
        if (player.getInventory().add(stack)) return InsertionResult.ok();
        return InsertionResult.inventoryFull();
    }
}
