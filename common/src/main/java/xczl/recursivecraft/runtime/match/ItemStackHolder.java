package xczl.recursivecraft.runtime.match;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A Holder&lt;Item&gt; that also carries the full ItemStack (with components/NBT).
 * Used by ingredientOf() in NBT-aware tests so that DefaultMaterialMatcher
 * can recover the full ItemStack identity instead of creating a bare new ItemStack(item).
 */
public final class ItemStackHolder implements Holder<Item> {
    private final ItemStack stack;

    public ItemStackHolder(ItemStack stack) {
        this.stack = stack.copy();
    }

    public ItemStack stack() {
        return stack.copy();
    }

    @Override
    public Item value() {
        return stack.getItem();
    }

    @Override
    public boolean isBound() {
        return true;
    }

    @Override
    public boolean is(Identifier location) {
        return false;
    }

    @Override
    public boolean is(ResourceKey<Item> resourceKey) {
        return false;
    }

    @Override
    public boolean is(Predicate<ResourceKey<Item>> predicate) {
        return false;
    }

    @Override
    public boolean is(TagKey<Item> tag) {
        return false;
    }

    @Override
    public boolean is(Holder<Item> other) {
        return this == other || this.value() == other.value();
    }

    @Override
    public Either<ResourceKey<Item>, Item> unwrap() {
        return Either.right(value());
    }

    @Override
    public Optional<ResourceKey<Item>> unwrapKey() {
        return Optional.empty();
    }

    @Override
    public Kind kind() {
        return Kind.DIRECT;
    }

    @Override
    public boolean canSerializeIn(HolderOwner<Item> owner) {
        return true;
    }

    @Override
    public Stream<TagKey<Item>> tags() {
        return Stream.empty();
    }
}
