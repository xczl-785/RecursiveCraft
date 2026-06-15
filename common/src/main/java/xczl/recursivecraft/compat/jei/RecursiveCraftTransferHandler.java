package xczl.recursivecraft.compat.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.core.CraftingPlanner;
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
        ItemStack output = CraftingPlanner.getRecipeResult(recipe);
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
            if (container instanceof InventoryMenu && !canCraftIn2x2(recipe)) {
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

            // NOTE: ServerboundPlaceRecipePacket now requires RecipeDisplayId (int index)
            // which cannot be reliably obtained from RecipeHolder in 1.21.8.
            // Standard vanilla transfer is deferred; RecursiveCraft's own transfer (Ctrl+click) works fine.
        }

        return null;
    }

    /**
     * 1.21.8 移除了 Recipe.canCraftInDimensions()。
     * 通过 display 信息判断配方是否为 3x3 专用。
     */
    private static boolean canCraftIn2x2(CraftingRecipe recipe) {
        for (RecipeDisplay display : recipe.display()) {
            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                if (shaped.width() > 2 || shaped.height() > 2) {
                    return false;
                }
            }
        }
        return true;
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
                        ClientTooltipComponent.create(message.getVisualOrderText())
                );
                graphics.renderTooltip(Minecraft.getInstance().font, tooltipComponents, mouseX, mouseY, null, net.minecraft.resources.ResourceLocation.parse("minecraft:tooltip/background"));
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
        ResourceLocation recipeId = recipeHolder.id().location();
        return new C2SExecuteCraftPacket(
                displayedOutput.getItem(),
                craftAmount,
                recipeId,
                displayedOutput.getComponentsPatch().isEmpty() ? null : TargetOutputSpec.fromStack(displayedOutput),
                displayedInputs
        );
    }
}
