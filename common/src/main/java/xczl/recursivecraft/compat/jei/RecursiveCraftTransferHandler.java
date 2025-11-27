package xczl.recursivecraft.compat.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.networking.PacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecursiveCraftTransferHandler<C extends AbstractContainerMenu> implements IRecipeTransferHandler<C, CraftingRecipe> {

    private final Class<C> containerClass;
    private final IRecipeTransferHandlerHelper transferHelper;

    public RecursiveCraftTransferHandler(Class<C> containerClass, IRecipeTransferHandlerHelper transferHelper) {
        this.containerClass = containerClass;
        this.transferHelper = transferHelper;
    }

    @Override
    public Class<C> getContainerClass() {
        return containerClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            C container,
            CraftingRecipe recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer
    ) {
        // 1. 基础检查
        ItemStack output = recipe.getResultItem(player.level().registryAccess());
        if (output.isEmpty()) {
            return new SimpleError(IRecipeTransferError.Type.USER_FACING, Component.literal("配方无效").withStyle(ChatFormatting.RED));
        }

        boolean isCtrlDown = Screen.hasControlDown();

        // === 逻辑一：递归合成 (接管逻辑) ===
        // 触发条件：按住 Ctrl
        if (isCtrlDown) {
            if (!doTransfer) return null; // 检查通过，显示蓝色/绿色按钮

            // 发送递归合成包
            // [核心修改] 传入 recipe.getId()，实现“所见即所得”的指定配方合成
            int craftAmount = maxTransfer ? 64 : 1;
            PacketHandler.CHANNEL.sendToServer(new C2SExecuteCraftPacket(
                    output.getItem(),
                    craftAmount,
                    recipe.getId() // <--- 关键修改：传递当前JEI展示的配方ID
            ));
            return null;
        }

        // === 逻辑二：原版合成 (背包 / 工作台) ===
        // 触发条件：未按 Ctrl，且容器是 InventoryMenu 或 CraftingMenu
        if (container instanceof InventoryMenu || container instanceof CraftingMenu) {

            // 2.1 尺寸检查 (仅针对 2x2 的背包)
            if (container instanceof InventoryMenu) {
                if (!recipe.canCraftInDimensions(2, 2)) {
                    Component warningText = Component.literal("配方过大，请按 Ctrl + 点击 进行递归合成")
                            .withStyle(ChatFormatting.RED);
                    return transferHelper.createUserErrorWithTooltip(warningText);
                }
            }

            // 2.2 材料检查 (通用)
            List<IRecipeSlotView> missingSlots = calculateMissingSlots(recipe, recipeSlots, player);

            if (!missingSlots.isEmpty()) {
                return transferHelper.createUserErrorForMissingSlots(
                        Component.literal("缺少材料").withStyle(ChatFormatting.RED),
                        missingSlots
                );
            }

            // 2.3 执行原版摆放
            if (!doTransfer) return null;

            Minecraft.getInstance().getConnection().send(
                    new ServerboundPlaceRecipePacket(
                            container.containerId,
                            recipe,
                            maxTransfer
                    )
            );

            return null;
        }

        return null;
    }

    /**
     * 计算缺失材料 (精准坐标映射版)
     */
    private List<IRecipeSlotView> calculateMissingSlots(CraftingRecipe recipe, IRecipeSlotsView recipeSlots, Player player) {
        List<IRecipeSlotView> missingViews = new ArrayList<>();
        List<ItemStack> inventoryCopy = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                inventoryCopy.add(stack.copy());
            }
        }

        List<Ingredient> requiredIngredients = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) {
                requiredIngredients.add(ing);
            }
        }

        List<IRecipeSlotView> activeSlotViews = new ArrayList<>();
        for (IRecipeSlotView view : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            if (!view.isEmpty()) {
                activeSlotViews.add(view);
            }
        }

        int checkCount = Math.min(requiredIngredients.size(), activeSlotViews.size());

        for (int i = 0; i < checkCount; i++) {
            Ingredient ingredient = requiredIngredients.get(i);
            IRecipeSlotView view = activeSlotViews.get(i);

            boolean found = false;
            for (ItemStack invStack : inventoryCopy) {
                if (!invStack.isEmpty() && ingredient.test(invStack)) {
                    invStack.shrink(1);
                    found = true;
                    break;
                }
            }
            if (!found) {
                missingViews.add(view);
            }
        }

        return missingViews;
    }

    private static class SimpleError implements IRecipeTransferError {
        private final Type type;
        private final Component message;

        public SimpleError(Type type, Component message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public Type getType() { return type; }

        @Override
        public void showError(GuiGraphics graphics, int mouseX, int mouseY, IRecipeSlotsView recipeSlotsView, int recipeX, int recipeY) {
            if (type == Type.USER_FACING) {
                graphics.renderTooltip(Minecraft.getInstance().font, message, mouseX, mouseY);
            }
        }
    }
}