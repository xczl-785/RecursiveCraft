package xczl.recursivecraft.runtime.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class TargetOutputSpec {
    private final Item item;
    private final @Nullable CompoundTag tag;

    public TargetOutputSpec(Item item, @Nullable CompoundTag tag) {
        this.item = Objects.requireNonNull(item);
        if (item == Items.AIR) {
            throw new IllegalArgumentException("Target item must not be AIR");
        }
        this.tag = tag == null ? null : tag.copy();
    }

    public Item item() {
        return item;
    }

    public @Nullable CompoundTag tag() {
        return tag == null ? null : tag.copy();
    }

    public ItemStack toTemplateStack() {
        ItemStack stack = new ItemStack(item);
        if (tag != null) {
            stack.setTag(tag.copy());
        }
        return stack;
    }
}
