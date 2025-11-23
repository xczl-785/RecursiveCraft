package xczl.recursivecraft.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper; // 新增导入
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
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
        // [新增] 获取 Helper
        IRecipeTransferHandlerHelper transferHelper = registration.getTransferHelper();

        // 1. 递归合成器
        registration.addRecipeTransferHandler(
                new RecursiveCraftTransferHandler<>(RecursiveCrafterMenu.class, transferHelper), // 传入 helper
                RecipeTypes.CRAFTING
        );

        // 2. 玩家背包
        registration.addRecipeTransferHandler(
                new RecursiveCraftTransferHandler<>(InventoryMenu.class, transferHelper), // 传入 helper
                RecipeTypes.CRAFTING
        );

        RecursiveCraft.LOGGER.info("RecursiveCraft: JEI Plugin Registered!");
    }
}