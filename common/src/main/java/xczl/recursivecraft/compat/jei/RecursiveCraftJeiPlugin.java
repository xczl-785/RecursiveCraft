package xczl.recursivecraft.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper; // 新增导入
import mezz.jei.api.registration.IRecipeTransferRegistration;
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
        // [新增] 获取 Helper
        IRecipeTransferHandlerHelper transferHelper = registration.getTransferHelper();

        // 2. 玩家背包
        registration.addRecipeTransferHandler(
                new RecursiveCraftTransferHandler<>(InventoryMenu.class, transferHelper), // 传入 helper
                RecipeTypes.CRAFTING
        );

        // 3. [新增] 原版工作台 (CraftingMenu - 3x3)
        // 接管后，在工作台里按 Ctrl 也能触发递归合成；普通点击则走原版填充逻辑
        registration.addRecipeTransferHandler(
                new RecursiveCraftTransferHandler<>(CraftingMenu.class, transferHelper),
                RecipeTypes.CRAFTING
        );

        RecursiveCraft.LOGGER.info("RecursiveCraft: JEI Plugin Registered!");
    }
}