package xczl.recursivecraft.compat.jei;

import com.google.gson.JsonObject;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.inventory.CraftingMenu;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.networking.C2SRecipeTransferPacket;
import xczl.recursivecraft.runtime.material.ItemStackComponentSupport;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecursiveCraftTransferHandlerTest {
    @BeforeAll
    static void init() {
        MinecraftTestBootstrap.init();
    }

    private static RecipeHolder<CraftingRecipe> recipeHolder(String id, CraftingRecipe recipe) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, ResourceLocation.parse(id));
        return new RecipeHolder<>(key, recipe);
    }

    @Test
    void createRecursivePacket_shouldUseDisplayedOutputIdentityAndRecipeId() {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        RecipeHolder<CraftingRecipe> recipeHolder = recipeHolder("test:long_swiftness", recipe);

        ItemStack displayedOutput = taggedStack(Items.POTION, "Potion", "minecraft:long_swiftness");
        List<ItemStack> displayedInputs = List.of(new ItemStack(Items.SUGAR), new ItemStack(Items.GLASS_BOTTLE));

        C2SExecuteCraftPacket packet = RecursiveCraftTransferPackets.createRecursivePacket(recipeHolder, displayedInputs, displayedOutput, false);

        assertEquals(Items.POTION, packet.targetItem());
        assertEquals(1, packet.amount());
        assertEquals(recipeHolder.id().location(), packet.forcedRecipeId());
        assertNotNull(packet.targetOutputSpec());
        assertEquals(Items.POTION, packet.targetOutputSpec().item());
        assertEquals(ItemStackComponentSupport.copyCustomData(displayedOutput), packet.targetOutputSpec().tag());
        assertNotNull(packet.displayedIngredients());
        assertEquals(2, packet.displayedIngredients().size());
        assertEquals(Items.SUGAR, packet.displayedIngredients().get(0).getItem());
        assertEquals(Items.GLASS_BOTTLE, packet.displayedIngredients().get(1).getItem());
    }

    @Test
    void createRecursivePacket_shouldPreserveDistinctDebugFixtureTargetOutputSpecsForSameItem() throws IOException {
        CraftingRecipe redRecipe = mock(CraftingRecipe.class);
        RecipeHolder<CraftingRecipe> redRecipeHolder = recipeHolder("recursivecraft:debug/handheld_crafter_red", redRecipe);
        CraftingRecipe blueRecipe = mock(CraftingRecipe.class);
        RecipeHolder<CraftingRecipe> blueRecipeHolder = recipeHolder("recursivecraft:debug/handheld_crafter_blue", blueRecipe);

        ItemStack redOutput = debugFixtureOutput("debug/handheld_crafter_red", Items.CRAFTING_TABLE);
        ItemStack blueOutput = debugFixtureOutput("debug/handheld_crafter_blue", Items.CRAFTING_TABLE);
        List<ItemStack> redInputs = List.of(taggedStack(Items.WHITE_WOOL, "variant", "red-source"));
        List<ItemStack> blueInputs = List.of(taggedStack(Items.WHITE_WOOL, "variant", "blue-source"));

        C2SExecuteCraftPacket redPacket = RecursiveCraftTransferPackets.createRecursivePacket(redRecipeHolder, redInputs, redOutput, false);
        C2SExecuteCraftPacket bluePacket = RecursiveCraftTransferPackets.createRecursivePacket(blueRecipeHolder, blueInputs, blueOutput, true);

        assertEquals(1, redPacket.amount());
        assertEquals(64, bluePacket.amount());
        assertEquals(redRecipeHolder.id().location(), redPacket.forcedRecipeId());
        assertEquals(blueRecipeHolder.id().location(), bluePacket.forcedRecipeId());
        assertNotNull(redPacket.targetOutputSpec());
        assertNotNull(bluePacket.targetOutputSpec());
        assertEquals(Items.CRAFTING_TABLE, redPacket.targetItem());
        assertEquals(Items.CRAFTING_TABLE, bluePacket.targetItem());
        assertEquals(ItemStackComponentSupport.copyCustomData(redOutput), redPacket.targetOutputSpec().tag());
        assertEquals(ItemStackComponentSupport.copyCustomData(blueOutput), bluePacket.targetOutputSpec().tag());
        assertEquals("red", redPacket.targetOutputSpec().tag().getCompoundOrEmpty("recursivecraft_debug").getString("variant").orElse(""));
        assertEquals("blue", bluePacket.targetOutputSpec().tag().getCompoundOrEmpty("recursivecraft_debug").getString("variant").orElse(""));
        assertNotEquals(redPacket.targetOutputSpec().tag(), bluePacket.targetOutputSpec().tag());
        assertEquals("red-source", ItemStackComponentSupport.copyCustomData(redPacket.displayedIngredients().get(0)).getString("variant").orElse(""));
        assertEquals("blue-source", ItemStackComponentSupport.copyCustomData(bluePacket.displayedIngredients().get(0)).getString("variant").orElse(""));
    }

    @Test
    void createStandardTransferPlan_shouldPreserveDistinctComponentVariantsForSameItem() {
        CraftingMenu menu = new CraftingMenu(0, new Inventory(mock(Player.class), mock(EntityEquipment.class)));
        ItemStack redSource = taggedStack(Items.WHITE_WOOL, "variant", "red-source");
        ItemStack blueSource = taggedStack(Items.WHITE_WOOL, "variant", "blue-source");
        menu.getSlot(10).set(blueSource.copy());
        menu.getSlot(11).set(redSource.copy());

        IRecipeSlotView recipeSlotView = slotView(redSource);
        IRecipeSlotView emptyRecipeSlotView = emptySlotView();
        IRecipeSlotsView recipeSlotsView = mock(IRecipeSlotsView.class);
        when(recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT)).thenReturn(List.of(
                recipeSlotView,
                emptyRecipeSlotView,
                emptyRecipeSlotView,
                emptyRecipeSlotView,
                emptyRecipeSlotView,
                emptyRecipeSlotView,
                emptyRecipeSlotView,
                emptyRecipeSlotView,
                emptyRecipeSlotView
        ));

        RecursiveCraftTransferHandler.StandardTransferPlan plan =
                RecursiveCraftTransferHandler.createStandardTransferPlan(menu, recipeSlotsView, null);

        assertFalse(plan.inventoryFull());
        assertTrue(plan.missingSlots().isEmpty());
        assertEquals(1, plan.operations().size());
        C2SRecipeTransferPacket.TransferOperation operation = plan.operations().get(0);
        assertEquals(11, operation.inventorySlotId());
        assertEquals(1, operation.craftingSlotId());
    }

    private static ItemStack taggedStack(Item item, String key, String value) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack debugFixtureOutput(String recipePath, Item item) throws IOException {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(readFixtureTag(recipePath)));
        return stack;
    }

    private static CompoundTag readFixtureTag(String recipePath) throws IOException {
        readRecipeJson(recipePath);
        CompoundTag tag = new CompoundTag();
        CompoundTag debugTag = new CompoundTag();
        debugTag.putString("variant", recipePath.endsWith("_blue") ? "blue" : "red");
        tag.put("recursivecraft_debug", debugTag);
        return tag;
    }

    private static JsonObject readRecipeJson(String recipePath) throws IOException {
        String resourcePath = "data/recursivecraft/recipes/" + recipePath + ".json";
        try (InputStream input = RecursiveCraftTransferHandlerTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(input, "Missing recipe resource: " + resourcePath);
            return GsonHelper.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static IRecipeSlotView slotView(ItemStack... stacks) {
        IRecipeSlotView slotView = mock(IRecipeSlotView.class);
        when(slotView.isEmpty()).thenReturn(stacks.length == 0);
        when(slotView.getDisplayedItemStack()).thenReturn(stacks.length == 0 ? Optional.empty() : Optional.of(stacks[0].copy()));
        List<ITypedIngredient<?>> ingredients = new java.util.ArrayList<>();
        for (ItemStack stack : stacks) {
            ingredients.add(typedIngredient(stack));
        }
        when(slotView.getAllIngredientsList()).thenReturn(ingredients);
        return slotView;
    }

    private static IRecipeSlotView emptySlotView() {
        return slotView();
    }

    private static ITypedIngredient<ItemStack> typedIngredient(ItemStack stack) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<ItemStack> ingredient = mock(ITypedIngredient.class);
        when(ingredient.castToItemStackType()).thenReturn(ingredient);
        when(ingredient.getIngredient()).thenReturn(stack.copy());
        return ingredient;
    }
}
