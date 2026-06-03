package xczl.recursivecraft.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.menu.RecursiveCrafterMenu;

@JeiPlugin
public class RecursiveCraftJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(RecursiveCraft.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper transferHelper = registration.getTransferHelper();

        registration.addRecipeTransferHandler(
                new RecursiveCraftTransferHandler<>(InventoryMenu.class, transferHelper),
                RecipeTypes.CRAFTING
        );

        registration.addRecipeTransferHandler(
                new RecursiveCraftTransferHandler<>(CraftingMenu.class, transferHelper),
                RecipeTypes.CRAFTING
        );

        RecursiveCraft.LOGGER.info("RecursiveCraft: JEI Plugin Registered!");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        RecursiveCraftJeiRuntime.setRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        RecursiveCraftJeiRuntime.clearRuntime();
    }
}
