package xczl.recursivecraft.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.runtime.material.ItemStackComponentSupport;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CraftableTarget {
    private final ItemStack displayStack;
    private final @Nullable ResourceLocation forcedRecipeId;
    private final @Nullable TargetOutputSpec targetOutputSpec;
    private final @Nullable List<ItemStack> displayedIngredients;

    public CraftableTarget(ItemStack displayStack,
                           @Nullable ResourceLocation forcedRecipeId,
                           @Nullable TargetOutputSpec targetOutputSpec,
                           @Nullable List<ItemStack> displayedIngredients) {
        if (displayStack == null || displayStack.isEmpty()) {
            throw new IllegalArgumentException("displayStack must be non-empty");
        }
        this.displayStack = displayStack.copy();
        this.forcedRecipeId = forcedRecipeId;
        this.targetOutputSpec = targetOutputSpec;
        this.displayedIngredients = copyIngredients(displayedIngredients);
    }

    public Item item() {
        return displayStack.getItem();
    }

    public ItemStack displayStack() {
        return displayStack.copy();
    }

    public @Nullable ResourceLocation forcedRecipeId() {
        return forcedRecipeId;
    }

    public @Nullable TargetOutputSpec targetOutputSpec() {
        return targetOutputSpec;
    }

    public @Nullable List<ItemStack> displayedIngredients() {
        return copyIngredients(displayedIngredients);
    }

    public String searchKey() {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(displayStack.getItem());
        return itemId + "|" + ItemStackComponentSupport.componentKey(displayStack);
    }

    private static @Nullable List<ItemStack> copyIngredients(@Nullable List<ItemStack> displayedIngredients) {
        if (displayedIngredients == null || displayedIngredients.isEmpty()) {
            return null;
        }
        List<ItemStack> copies = new ArrayList<>(displayedIngredients.size());
        for (ItemStack stack : displayedIngredients) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            copies.add(stack.copy());
        }
        return copies.isEmpty() ? null : List.copyOf(copies);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CraftableTarget that)) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(this.displayStack, that.displayStack)
                && Objects.equals(this.forcedRecipeId, that.forcedRecipeId)
                && sameTargetOutputSpec(this.targetOutputSpec, that.targetOutputSpec)
                && sameIngredients(this.displayedIngredients, that.displayedIngredients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                BuiltInRegistries.ITEM.getKey(displayStack.getItem()),
                ItemStackComponentSupport.componentKey(displayStack),
                forcedRecipeId,
                targetOutputSpecKey(targetOutputSpec),
                ingredientKey(displayedIngredients)
        );
    }

    private static boolean sameTargetOutputSpec(@Nullable TargetOutputSpec left, @Nullable TargetOutputSpec right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.item() == right.item() && Objects.equals(left.componentsPatch(), right.componentsPatch());
    }

    private static String targetOutputSpecKey(@Nullable TargetOutputSpec spec) {
        if (spec == null) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(spec.item());
        return itemId + "|" + ItemStackComponentSupport.componentKey(spec.componentsPatch());
    }

    private static boolean sameIngredients(@Nullable List<ItemStack> left, @Nullable List<ItemStack> right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(left.get(i), right.get(i)) || left.get(i).getCount() != right.get(i).getCount()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> ingredientKey(@Nullable List<ItemStack> ingredients) {
        if (ingredients == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(ingredients.size());
        for (ItemStack stack : ingredients) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            keys.add(id + "|" + stack.getCount() + "|" + ItemStackComponentSupport.componentKey(stack));
        }
        return keys;
    }
}
