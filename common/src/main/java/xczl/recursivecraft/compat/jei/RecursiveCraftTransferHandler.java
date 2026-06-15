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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.networking.C2SRecipeTransferPacket;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.networking.PacketHandler;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RecursiveCraftTransferHandler<C extends AbstractContainerMenu> implements IRecipeTransferHandler<C, RecipeHolder<CraftingRecipe>> {
    private static final List<Integer> PLAYER_GRID_INPUT_INDEXES = List.of(0, 1, 3, 4);

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

            StandardTransferPlan plan = createStandardTransferPlan(container, recipeSlots, player);
            if (!plan.missingSlots().isEmpty()) {
                return transferHelper.createUserErrorForMissingSlots(
                        Component.translatable("recursivecraft.msg.missing_ingredients").withStyle(ChatFormatting.RED),
                        plan.missingSlots()
                );
            }

            if (plan.inventoryFull()) {
                Component warningText = Component.translatable("jei.tooltip.error.recipe.transfer.inventory.full")
                        .withStyle(ChatFormatting.RED);
                return transferHelper.createUserErrorWithTooltip(warningText);
            }

            if (doTransfer) {
                PacketHandler.CHANNEL.sendToServer(new C2SRecipeTransferPacket(
                        plan.operations(),
                        plan.craftingSlotIds(),
                        plan.inventorySlotIds(),
                        maxTransfer,
                        false
                ));
            }
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

    static StandardTransferPlan createStandardTransferPlan(AbstractContainerMenu container, IRecipeSlotsView recipeSlots, Player player) {
        List<Slot> craftingSlots = craftingSlotsFor(container);
        List<Slot> inventorySlots = inventorySlotsFor(container);
        if (craftingSlots.isEmpty() || inventorySlots.isEmpty()) {
            return new StandardTransferPlan(List.of(), List.of(), List.of(), true, List.of());
        }

        List<IRecipeSlotView> inputViews = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        List<IRecipeSlotView> filteredInputViews = container instanceof InventoryMenu
                ? filterPlayerInventoryInputs(inputViews)
                : inputViews;

        InventoryState inventoryState = buildInventoryState(craftingSlots, inventorySlots);
        boolean inventoryFull = !inventoryState.hasRoom(filteredInputViews.size());

        Map<Slot, ItemStack> availableItemStacks = inventoryState.availableItemStacks();
        List<C2SRecipeTransferPacket.TransferOperation> operations = new ArrayList<>();
        List<IRecipeSlotView> missingItems = new ArrayList<>();

        int bound = Math.min(filteredInputViews.size(), craftingSlots.size());
        for (int i = 0; i < bound; i++) {
            IRecipeSlotView requiredSlotView = filteredInputViews.get(i);
            if (requiredSlotView.isEmpty()) {
                continue;
            }

            Slot craftingSlot = craftingSlots.get(i);
            Slot matchingSlot = findMatchingSlot(requiredSlotView, availableItemStacks);
            if (matchingSlot == null) {
                missingItems.add(requiredSlotView);
                continue;
            }

            ItemStack matchingStack = availableItemStacks.get(matchingSlot);
            matchingStack.shrink(1);
            operations.add(new C2SRecipeTransferPacket.TransferOperation(matchingSlot.index, craftingSlot.index));
        }

        return new StandardTransferPlan(
                operations,
                craftingSlots.stream().map(slot -> slot.index).toList(),
                inventorySlots.stream().map(slot -> slot.index).toList(),
                inventoryFull,
                missingItems
        );
    }

    private static Slot findMatchingSlot(IRecipeSlotView requiredSlotView, Map<Slot, ItemStack> availableItemStacks) {
        List<ItemStack> acceptableStacks = acceptableStacks(requiredSlotView);
        if (acceptableStacks.isEmpty()) {
            return null;
        }

        Slot bestSlot = null;
        int bestCount = -1;
        for (Map.Entry<Slot, ItemStack> entry : availableItemStacks.entrySet()) {
            ItemStack availableStack = entry.getValue();
            if (availableStack.isEmpty() || !matchesAny(availableStack, acceptableStacks)) {
                continue;
            }
            if (availableStack.getCount() > bestCount || (availableStack.getCount() == bestCount && bestSlot != null && entry.getKey().index < bestSlot.index)) {
                bestSlot = entry.getKey();
                bestCount = availableStack.getCount();
            }
        }
        return bestSlot;
    }

    private static boolean matchesAny(ItemStack availableStack, List<ItemStack> acceptableStacks) {
        for (ItemStack acceptableStack : acceptableStacks) {
            if (ItemStack.isSameItemSameComponents(acceptableStack, availableStack)) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> acceptableStacks(IRecipeSlotView requiredSlotView) {
        return requiredSlotView.getAllIngredientsList().stream()
                .filter(Objects::nonNull)
                .map(typedIngredient -> typedIngredient.castToItemStackType())
                .filter(Objects::nonNull)
                .map(typedIngredient -> typedIngredient.getIngredient().copy())
                .toList();
    }

    private static InventoryState buildInventoryState(Collection<Slot> craftingSlots, Collection<Slot> inventorySlots) {
        Map<Slot, ItemStack> availableItemStacks = new HashMap<>();
        int filledCraftSlotCount = 0;
        int emptySlotCount = 0;

        for (Slot slot : craftingSlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                filledCraftSlotCount++;
                availableItemStacks.put(slot, stack.copy());
            }
        }

        for (Slot slot : inventorySlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                availableItemStacks.put(slot, stack.copy());
            } else {
                emptySlotCount++;
            }
        }

        return new InventoryState(availableItemStacks, filledCraftSlotCount, emptySlotCount);
    }

    private static List<IRecipeSlotView> filterPlayerInventoryInputs(List<IRecipeSlotView> slotViews) {
        List<IRecipeSlotView> filtered = new ArrayList<>(PLAYER_GRID_INPUT_INDEXES.size());
        for (Integer index : PLAYER_GRID_INPUT_INDEXES) {
            if (index < slotViews.size()) {
                filtered.add(slotViews.get(index));
            }
        }
        return filtered;
    }

    private static List<Slot> craftingSlotsFor(AbstractContainerMenu container) {
        if (container instanceof InventoryMenu) {
            return container.slots.size() >= 5 ? container.slots.subList(1, 5) : List.of();
        }
        if (container instanceof CraftingMenu) {
            return container.slots.size() >= 10 ? container.slots.subList(1, 10) : List.of();
        }
        return List.of();
    }

    private static List<Slot> inventorySlotsFor(AbstractContainerMenu container) {
        if (container instanceof InventoryMenu) {
            return container.slots.size() >= 45 ? container.slots.subList(9, 45) : List.of();
        }
        if (container instanceof CraftingMenu) {
            return container.slots.size() >= 46 ? container.slots.subList(10, 46) : List.of();
        }
        return List.of();
    }

    record StandardTransferPlan(List<C2SRecipeTransferPacket.TransferOperation> operations,
                                List<Integer> craftingSlotIds,
                                List<Integer> inventorySlotIds,
                                boolean inventoryFull,
                                List<IRecipeSlotView> missingSlots) {
    }

    private record InventoryState(Map<Slot, ItemStack> availableItemStacks,
                                  int filledCraftSlotCount,
                                  int emptySlotCount) {
        private boolean hasRoom(int inputCount) {
            return filledCraftSlotCount - inputCount <= emptySlotCount;
        }
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
