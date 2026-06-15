package xczl.recursivecraft.compat.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import xczl.recursivecraft.runtime.material.RecipeHelper;
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
    public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
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
        ItemStack output = RecipeHelper.getResultItem(recipe);
        if (output.isEmpty()) {
            return new SimpleError(
                    IRecipeTransferError.Type.USER_FACING,
                    Component.translatable("recursivecraft.msg.invalid_recipe").withStyle(ChatFormatting.RED)
            );
        }

        if (isControlDown()) {
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
            PacketHandler.sendToServer(
                    RecursiveCraftTransferPackets.createRecursivePacket(recipeHolder, displayedInputs, displayedOutput, maxTransfer)
            );
            return null;
        }

        if (container instanceof InventoryMenu || container instanceof CraftingMenu) {
            // canCraftInDimensions removed in 1.21.11 - skip size check for inventory menu

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

            // In 1.21.11, ServerboundPlaceRecipePacket requires RecipeDisplayId which is not
            // easily obtainable from RecipeHolder. Route through our custom packet handler instead.
            PacketHandler.sendToServer(
                    RecursiveCraftTransferPackets.createRecursivePacket(recipeHolder, List.of(), output, maxTransfer)
            );
        }

        return null;
    }

    private List<IRecipeSlotView> calculateMissingSlots(CraftingRecipe recipe, IRecipeSlotsView recipeSlots, Player player) {
        List<IRecipeSlotView> missingViews = new ArrayList<>();
        List<ItemStack> inventoryCopy = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                inventoryCopy.add(stack.copy());
            }
        }

        List<Ingredient> requiredIngredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
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

    private static boolean isControlDown() {
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
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
                List<ClientTooltipComponent> tooltipComponents = List.of(
                        new ClientTextTooltip(message.getVisualOrderText())
                );
                graphics.renderTooltip(
                        Minecraft.getInstance().font,
                        tooltipComponents,
                        mouseX, mouseY,
                        DefaultTooltipPositioner.INSTANCE,
                        Identifier.withDefaultNamespace("empty")
                );
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
                recipeHolder.id().identifier(),
                displayedOutput.getComponentsPatch().isEmpty() ? null : TargetOutputSpec.fromStack(displayedOutput),
                displayedInputs
        );
    }
}
