package xczl.recursivecraft.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.recipe.DebugTaggedResultRecipe;
import xczl.recursivecraft.recipe.DebugTaggedResultRecipeSerializer;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(RecursiveCraft.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static final DebugTaggedResultRecipeSerializer DEBUG_TAGGED_RESULT_SERIALIZER =
            new DebugTaggedResultRecipeSerializer();

    public static final RegistrySupplier<RecipeSerializer<DebugTaggedResultRecipe>> DEBUG_TAGGED_RESULT =
            RECIPE_SERIALIZERS.register("debug_tagged_result", () -> DEBUG_TAGGED_RESULT_SERIALIZER);

    public static void register() {
        RECIPE_SERIALIZERS.register();
    }
}
