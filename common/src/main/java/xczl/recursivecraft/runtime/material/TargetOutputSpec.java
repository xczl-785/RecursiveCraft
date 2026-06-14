package xczl.recursivecraft.runtime.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class TargetOutputSpec {
    private final Item item;
    private final DataComponentPatch componentsPatch;

    public TargetOutputSpec(Item item, @Nullable CompoundTag tag) {
        this(item, ItemStackComponentSupport.patchFromCustomData(tag));
    }

    public TargetOutputSpec(Item item, DataComponentPatch componentsPatch) {
        this.item = Objects.requireNonNull(item);
        if (item == Items.AIR) {
            throw new IllegalArgumentException("Target item must not be AIR");
        }
        this.componentsPatch = Objects.requireNonNull(componentsPatch);
    }

    public static TargetOutputSpec fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Target stack must be non-empty");
        }
        return new TargetOutputSpec(stack.getItem(), stack.getComponentsPatch());
    }

    public Item item() {
        return item;
    }

    public @Nullable CompoundTag tag() {
        return ItemStackComponentSupport.copyCustomData(componentsPatch);
    }

    public DataComponentPatch componentsPatch() {
        return componentsPatch;
    }

    public ItemStack toTemplateStack() {
        ItemStack stack = new ItemStack(item);
        if (!componentsPatch.isEmpty()) {
            stack.applyComponentsAndValidate(componentsPatch);
        }
        return stack;
    }
}
