package xczl.recursivecraft.runtime.material;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.lang.reflect.Field;

/**
 * Helper to obtain the result ItemStack from a CraftingRecipe in MC 1.21.11+,
 * where the old getResultItem(RegistryAccess) method has been removed.
 * <p>
 * Concrete recipe implementations (ShapedRecipe, ShapelessRecipe) expose their
 * result as a public final field {@code result}. We access it via reflection
 * as a fallback when assemble() is not convenient.
 */
public final class RecipeHelper {
    private static final CraftingInput EMPTY_1x1 = CraftingInput.of(1, 1,
            java.util.List.of(ItemStack.EMPTY));

    private RecipeHelper() {}

    /**
     * Returns the result ItemStack for the given recipe.
     * Tries the public {@code result} field first (ShapedRecipe, ShapelessRecipe),
     * then falls back to assemble() with an empty 1x1 CraftingInput.
     */
    public static ItemStack getResultItem(CraftingRecipe recipe) {
        // Try the public 'result' field
        try {
            Field resultField = recipe.getClass().getField("result");
            Object value = resultField.get(recipe);
            if (value instanceof ItemStack stack) {
                return stack;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }

        // Fallback: assemble with empty input
        try {
            return recipe.assemble(EMPTY_1x1, null);
        } catch (Exception ignored) {
        }

        return ItemStack.EMPTY;
    }
}
