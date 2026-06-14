package xczl.recursivecraft.registry;

import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import xczl.recursivecraft.RecursiveCraft;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(RecursiveCraft.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static void register() {
        RECIPE_SERIALIZERS.register();
    }
}
