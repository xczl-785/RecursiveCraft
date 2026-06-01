package xczl.recursivecraft.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import xczl.recursivecraft.registry.ModRecipeSerializers;

public class DebugTaggedResultRecipe extends ShapelessRecipe {
    public DebugTaggedResultRecipe(ResourceLocation id,
                                   String group,
                                   CraftingBookCategory category,
                                   ItemStack result,
                                   NonNullList<Ingredient> ingredients) {
        super(id, group, category, result, ingredients);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.DEBUG_TAGGED_RESULT_SERIALIZER;
    }
}
