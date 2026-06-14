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
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.networking.PacketHandler;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecursiveCraftTransferHandler<C extends AbstractContainerMenu> implements IRecipeTransferHandler<C, RecipeHolder<CraftingRecipe>> {

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
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            C container,
            RecipeHolder<CraftingRecipe> recipeHolder,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer
    ) {
        CraftingRecipe recipe = recipeHolder.value();
        ItemStack output = recipe.getResultItem(player.level().registryAccess());
        if (output.isEmpty()) {
            return new SimpleError(
                    IRecipeTransferError.Type.USER_FACING,
                    Component.translatable("recursivecraft.msg.invalid_recipe").withStyle(ChatFormatting.RED)
            );
        }

        if (Screen.hasControlDown()) {
            if (!doTransfer) {
                return null;
            }

            List<ItemStack> displayedInputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                    .map(IRecipeSlotView::getDisplayedItemStack)
                    .flatMap(Optional::stream)
                    .filter(stack -> !stack.isEmpty())
                    .map(ItemStack::copy)
                    .toList();
            ItemStack displayedOutput = recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                    .map(IRecipeSlotView::getDisplayedItemStack)
                    .flatMap(Optional::stream)
                    .filter(stack -> !stack.isEmpty())
                    .findFirst()
                    .orElse(output);
            PacketHandler.CHANNEL.sendToServer(
                    RecursiveCraftTransferPackets.createRecursivePacket(recipeHolder, displayedInputs, displayedOutput, maxTransfer)
            );
            return null;
        }

        if (container instanceof InventoryMenu || container instanceof CraftingMenu) {
            if (container instanceof InventoryMenu && !recipe.canCraftInDimensions(2, 2)) {
                Component warningText = Component.translatable("recursivecraft.msg.recipe_too_large")
                        .withStyle(ChatFormatting.RED);
                return transferHelper.createUserErrorWithTooltip(warningText);
            }

            List<IRecipeSlotView> missingSlots = calculateMissingSlots(recipe, recipeSlots, player);
            if (!missingSlots.isEmpty()) {
                return transferHelper.createUserErrorForMissingSlots(
                        Component.translatable("recursivecraft.msg.missing_ingredients").withStyle(ChatFormatting.RED),
                        missingSlots
                );
            }

            if (!doTransfer) {
                return null;
            }

            Minecraft.getInstance().getConnection().send(
                    new ServerboundPlaceRecipePacket(container.containerId, recipeHolder, maxTransfer)
            );
        }

        return null;
    }

    private List<IRecipeSlotView> calculateMissingSlots(CraftingRecipe recipe, IRecipeSlotsView recipeSlots, Player player) {
        List<IRecipeSlotView> missingViews = new ArrayList<>();
        List<ItemStack> inventoryCopy = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                inventoryCopy.add(stack.copy());
            }
        }

        List<Ingredient> requiredIngredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                requiredIngredients.add(ingredient);
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

        private SimpleError(Type type, Component message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public void showError(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                IRecipeSlotsView recipeSlotsView,
                int recipeX,
                int recipeY
        ) {
            if (type == Type.USER_FACING) {
                graphics.renderTooltip(Minecraft.getInstance().font, message, mouseX, mouseY);
            }
        }
    }
}

final class RecursiveCraftTransferPackets {
    private RecursiveCraftTransferPackets() {
    }

    static C2SExecuteCraftPacket createRecursivePacket(RecipeHolder<CraftingRecipe> recipeHolder,
                                                       List<ItemStack> displayedInputs,
                                                       ItemStack displayedOutput,
                                                       boolean maxTransfer) {
        int craftAmount = maxTransfer ? 64 : 1;
        return new C2SExecuteCraftPacket(
                displayedOutput.getItem(),
                craftAmount,
                recipeHolder.id(),
                displayedOutput.getComponentsPatch().isEmpty() ? null : TargetOutputSpec.fromStack(displayedOutput),
                displayedInputs
        );
    }
}
