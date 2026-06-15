package xczl.recursivecraft.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.ItemStackComponentSupport;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionCalculatorNbtTest {
    private static final DefaultMaterialIdentityNormalizer NORMALIZER = new DefaultMaterialIdentityNormalizer();

    @BeforeAll
    static void init() {
        MinecraftTestBootstrap.init();
    }

    @BeforeEach
    void resetPlanner() throws Exception {
        setPlanningResult(Map.of(), Map.of(), Map.of());
    }

    @Test
    void calculate_shouldProduceMaterialNeeds() {
        Inventory inv = mock(Inventory.class);
        when(inv.getContainerSize()).thenReturn(1);
        when(inv.getItem(0)).thenReturn(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.OAK_LOG));
        PlacementInfo info = PlacementInfo.create(ingredients);
        SlotDisplay resultDisplay = new SlotDisplay.ItemSlotDisplay(Items.OAK_PLANKS);
        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(List.of(), resultDisplay, SlotDisplay.Empty.INSTANCE);
        List<RecipeDisplay> displays = List.of(display);
        when(recipe.placementInfo()).thenReturn(info);
        when(recipe.display()).thenReturn(displays);

        CraftingTransaction tx = calc.calculate(Items.OAK_PLANKS, 4, true, new RecipeHolder<>(recipeKey("test:r"), recipe));
        assertFalse(tx.getMaterialNeeds().isEmpty());
    }

    @Test
    void calculate_shouldNotConsumeMismatchedInventoryVariantForSingleCandidateIngredient() {
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue");
        ItemStack redStick = stackWithVariant(Items.STICK, "red");
        Inventory inv = mockInventory(blueStick);
        TransactionCalculator calc = new TransactionCalculator(inv);

        RecipeHolder<CraftingRecipe> recipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(redStick), "test:torch_from_red_stick");

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true, recipe);

        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(redStick), 0));
        assertFalse(tx.getMaterialNeeds().containsKey(materialKey(blueStick)));
    }

    @Test
    void calculate_shouldPreferExactInventoryMatchWhenIngredientOptionsShareSameItem() {
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue");
        ItemStack redStick = stackWithVariant(Items.STICK, "red");
        Inventory inv = mockInventory(blueStick);
        TransactionCalculator calc = new TransactionCalculator(inv);

        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                new ItemStack(Items.TORCH, 1),
                ingredientOf(redStick, blueStick),
                "test:torch_from_two_stick_variants"
        );

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true, recipe);

        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(blueStick), 0));
        assertFalse(tx.getMaterialNeeds().containsKey(materialKey(redStick)));
    }

    @Test
    void calculate_shouldRollbackTheoreticalRecipeWhenItsOutputIdentityDoesNotMatchNeededVariant() throws Exception {
        ItemStack redStick = stackWithVariant(Items.STICK, "red");
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue");
        ItemStack redWool = stackWithVariant(Items.WHITE_WOOL, "red-source");
        ItemStack blueWool = stackWithVariant(Items.WHITE_WOOL, "blue-source");

        RecipeHolder<CraftingRecipe> preferredBlueStickRecipe = mockRecipe(blueStick, ingredientOf(blueWool), "test:blue_stick");
        RecipeHolder<CraftingRecipe> alternateRedStickRecipe = mockRecipe(redStick, ingredientOf(redWool), "test:red_stick");
        setPlanningResult(
                Map.of(Items.STICK, preferredBlueStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(Items.STICK, List.of(preferredBlueStickRecipe, alternateRedStickRecipe))
        );

        Inventory inv = mockInventory(redWool, blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> parentRecipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(redStick), "test:torch_from_red_stick");

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true, parentRecipe);

        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(redWool), 0));
        assertFalse(tx.getMaterialNeeds().containsKey(materialKey(blueWool)));
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_shouldAllowRecursingIntoSameItemWhenDesiredMaterialKeyDiffers() throws Exception {
        ItemStack redStick = stackWithVariant(Items.STICK, "red");
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue");
        ItemStack blueWool = stackWithVariant(Items.WHITE_WOOL, "blue-source");

        RecipeHolder<CraftingRecipe> redStickRecipe = mockRecipe(redStick, ingredientOf(blueStick), "test:red_stick_from_blue_stick");
        RecipeHolder<CraftingRecipe> blueStickRecipe = mockRecipe(blueStick, ingredientOf(blueWool), "test:blue_stick_from_blue_wool");
        setPlanningResult(
                Map.of(Items.STICK, redStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(Items.STICK, List.of(redStickRecipe, blueStickRecipe))
        );

        Inventory inv = mockInventory(blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> parentRecipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(redStick), "test:torch_from_red_stick");

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true, parentRecipe);

        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(blueWool), 0));
        assertFalse(tx.getNeeds().containsKey(Items.STICK));
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_shouldNotLetFailedMaterialKeyCachePoisonSiblingMaterialKey() throws Exception {
        ItemStack missingStick = stackWithVariant(Items.STICK, "a-missing");
        ItemStack craftableStick = stackWithVariant(Items.STICK, "z-craftable");
        ItemStack blueWool = stackWithVariant(Items.WHITE_WOOL, "blue-source");

        RecipeHolder<CraftingRecipe> blueStickRecipe = mockRecipe(craftableStick, ingredientOf(blueWool), "test:craftable_blue_stick");
        setPlanningResult(
                Map.of(Items.STICK, blueStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(Items.STICK, List.of(blueStickRecipe))
        );

        Inventory inv = mockInventory(blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> parentRecipe = mockRecipe(
                new ItemStack(Items.TORCH, 1),
                ingredientOf(missingStick, craftableStick),
                "test:torch_from_optional_stick_variants"
        );

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true, parentRecipe);

        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(blueWool), 0));
        assertFalse(tx.getMaterialNeeds().containsKey(materialKey(missingStick)));
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_shouldPreserveRecipeOutputIdentityInResolvedOutputs() {
        ItemStack redStickOutput = stackWithVariant(Items.STICK, "red-output");
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(redStickOutput.copyWithCount(2), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:red_stick_output");

        CraftingTransaction tx = calc.calculate(Items.STICK, 1, true, recipe);

        assertEquals(1, tx.getResolvedOutputs().size());
        ItemStack resolved = tx.getResolvedOutputs().get(0);
        assertEquals(Items.STICK, resolved.getItem());
        assertEquals(2, resolved.getCount());
        assertNotNull(customData(resolved));
        assertEquals("red-output", customData(resolved).getString("variant").orElse(""));
    }

    @Test
    void calculate_shouldReturnUnsupportedWhenIngredientIdentityCannotBeNormalized() {
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        applyCustomData(unsupportedStick, tagWithEntry("unsupported", EndTag.INSTANCE));
        Inventory inv = mockInventory();
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(unsupportedStick), "test:unsupported_stick");

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true, recipe);

        assertTrue(tx.isUnsupported());
        assertTrue(tx.getResolvedOutputs().isEmpty());
    }

    @Test
    void calculate_shouldAggregateIngredientCandidateMissingAndUnsupportedAsUnsupported() {
        ItemStack missingStick = stackWithVariant(Items.STICK, "missing");
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        applyCustomData(unsupportedStick, tagWithEntry("unsupported", EndTag.INSTANCE));
        Inventory inv = mockInventory();
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                new ItemStack(Items.TORCH, 1),
                ingredientOf(missingStick, unsupportedStick),
                "test:torch_from_missing_or_unsupported_stick"
        );

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true, recipe);

        assertTrue(tx.isUnsupported());
    }

    @Test
    void calculate_shouldAggregateRecipeCandidateMissingAndUnsupportedAsUnsupported() throws Exception {
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        applyCustomData(unsupportedStick, tagWithEntry("unsupported", EndTag.INSTANCE));

        RecipeHolder<CraftingRecipe> missingRecipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:missing_recipe");
        RecipeHolder<CraftingRecipe> unsupportedRecipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(unsupportedStick), "test:unsupported_recipe");
        setPlanningResult(
                Map.of(Items.TORCH, missingRecipe),
                Map.of(Items.TORCH, 1.0d, Items.OAK_LOG, 1.0d, Items.STICK, 1.0d),
                Map.of(Items.TORCH, List.of(missingRecipe, unsupportedRecipe))
        );

        Inventory inv = mockInventory();
        TransactionCalculator calc = new TransactionCalculator(inv);

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true);

        assertTrue(tx.isUnsupported());
    }

    @Test
    void debugFixtureRecipes_shouldExposeDistinctMaterialKeysWithoutChangingItemIdentity() throws IOException {
        JsonObject redRecipeJson = readRecipeJson("debug/handheld_crafter_red");
        JsonObject blueRecipeJson = readRecipeJson("debug/handheld_crafter_blue");
        JsonObject normalRecipeJson = readRecipeJson("handheld_crafter");

        ResourceLocation redId = ResourceLocation.parse("recursivecraft:debug/handheld_crafter_red");
        ResourceLocation blueId = ResourceLocation.parse("recursivecraft:debug/handheld_crafter_blue");
        assertNotEquals(redId, blueId);

        assertEquals("recursivecraft:handheld_crafter", resultItemId(redRecipeJson));
        assertEquals(resultItemId(redRecipeJson), resultItemId(blueRecipeJson));
        assertNotEquals(debugFixtureVariant(redRecipeJson), debugFixtureVariant(blueRecipeJson));
        assertNotEquals(
                materialKey(debugFixtureOutput("debug/handheld_crafter_red", Items.CRAFTING_TABLE)),
                materialKey(debugFixtureOutput("debug/handheld_crafter_blue", Items.CRAFTING_TABLE))
        );

        assertEquals(1, countPatternSymbolUses(normalRecipeJson, "recursivecraft:recursive_crafter"));
        assertEquals(8, countIngredientItem(redRecipeJson, "recursivecraft:recursive_crafter"));
        assertEquals(8, countIngredientItem(blueRecipeJson, "recursivecraft:recursive_crafter"));
    }

    @Test
    void debugFixtureRecipes_shouldRemainMoreExpensiveThanNormalHandheldRecipeUnderPlannerCosting() throws Exception {
        Map<Item, Double> costTable = Map.of(
                Items.DIAMOND, 10.0d,
                Items.CRAFTING_TABLE, 1.0d,
                Items.REDSTONE_BLOCK, 1.0d,
                Items.LAPIS_BLOCK, 1.0d
        );

        double normalCost = plannerRecipeCostForResource("handheld_crafter", costTable);
        double redCost = plannerRecipeCostForResource("debug/handheld_crafter_red", costTable);
        double blueCost = plannerRecipeCostForResource("debug/handheld_crafter_blue", costTable);

        assertTrue(normalCost < redCost);
        assertTrue(normalCost < blueCost);
    }

    @Test
    void plannerRecipeCostHelper_shouldPreserveResultCountFromRecipeJson() throws Exception {
        JsonObject recipeJson = GsonHelper.parse("""
                {
                  "type": "minecraft:crafting_shapeless",
                  "ingredients": [
                    { "item": "recursivecraft:recursive_crafter" }
                  ],
                  "result": {
                    "item": "recursivecraft:handheld_crafter",
                    "count": 4
                  }
                }
                """);
        Map<Item, Double> costTable = Map.of(Items.DIAMOND, 10.0d);

        double cost = invokePlannerRecipeCost(
                recipeForPlannerCost(recipeJson, recipeKey("recursivecraft:count_check")),
                costTable
        );

        assertEquals(2.525d, cost);
    }

    @Test
    void calculate_overloadShouldAcceptDesiredOutputKey() {
        ItemStack redStick = stackWithVariant(Items.STICK, "red-output");
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG));
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(redStick.copy(), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:red_stick");

        CraftingTransaction tx = calc.calculate(Items.STICK, 1, true, recipe, materialKey(redStick));

        assertEquals(1, tx.getResolvedOutputs().size());
        assertEquals("red-output", customData(tx.getResolvedOutputs().get(0)).getString("variant").orElse(""));
        assertEquals(1, tx.getMaterialNeeds().values().stream().mapToInt(Integer::intValue).sum());
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_overloadShouldFilterRecipeOutputIdentityMismatch() {
        ItemStack redStick = stackWithVariant(Items.STICK, "red-output");
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue-output");
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG));
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(blueStick.copy(), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:blue_stick");

        CraftingTransaction tx = calc.calculate(Items.STICK, 1, true, recipe, materialKey(redStick));

        assertEquals(1, tx.getNeeds().getOrDefault(Items.STICK, 0));
        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(redStick), 0));
        assertTrue(tx.getResolvedOutputs().isEmpty());
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_overloadShouldSelectDesiredDebugFixtureVariantAmongSameItemCandidates() throws Exception {
        Item fixtureItem = Items.CRAFTING_TABLE;
        ItemStack redFixtureOutput = debugFixtureOutput("debug/handheld_crafter_red", fixtureItem);
        ItemStack blueFixtureOutput = debugFixtureOutput("debug/handheld_crafter_blue", fixtureItem);
        ItemStack redWool = stackWithVariant(Items.WHITE_WOOL, "red-source");
        ItemStack blueWool = stackWithVariant(Items.WHITE_WOOL, "blue-source");

        RecipeHolder<CraftingRecipe> preferredBlueRecipe = mockRecipe(
                blueFixtureOutput.copy(),
                ingredientOf(blueWool),
                "recursivecraft:debug/handheld_crafter_blue"
        );
        RecipeHolder<CraftingRecipe> alternateRedRecipe = mockRecipe(
                redFixtureOutput.copy(),
                ingredientOf(redWool),
                "recursivecraft:debug/handheld_crafter_red"
        );
        setPlanningResult(
                Map.of(fixtureItem, preferredBlueRecipe),
                Map.of(fixtureItem, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(fixtureItem, List.of(preferredBlueRecipe, alternateRedRecipe))
        );

        Inventory inv = mockInventory(redWool, blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);

        CraftingTransaction tx = calc.calculate(fixtureItem, 1, true, null, materialKey(redFixtureOutput));

        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(redWool), 0));
        assertFalse(tx.getMaterialNeeds().containsKey(materialKey(blueWool)));
        assertEquals("red", fixtureVariant(tx.getResolvedOutputs().get(0)).orElse(""));
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_overloadShouldSelectDesiredOutputVariantAmongRecipeCandidates() throws Exception {
        ItemStack redStick = stackWithVariant(Items.STICK, "red-output");
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue-output");
        ItemStack redWool = stackWithVariant(Items.WHITE_WOOL, "red-source");
        ItemStack blueWool = stackWithVariant(Items.WHITE_WOOL, "blue-source");

        RecipeHolder<CraftingRecipe> preferredBlueRecipe = mockRecipe(blueStick.copy(), ingredientOf(blueWool), "test:blue_stick");
        RecipeHolder<CraftingRecipe> alternateRedRecipe = mockRecipe(redStick.copy(), ingredientOf(redWool), "test:red_stick");
        setPlanningResult(
                Map.of(Items.STICK, preferredBlueRecipe),
                Map.of(Items.STICK, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(Items.STICK, List.of(preferredBlueRecipe, alternateRedRecipe))
        );

        Inventory inv = mockInventory(redWool, blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);

        CraftingTransaction tx = calc.calculate(Items.STICK, 1, true, null, materialKey(redStick));

        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(redWool), 0));
        assertFalse(tx.getMaterialNeeds().containsKey(materialKey(blueWool)));
        assertEquals("red-output", customData(tx.getResolvedOutputs().get(0)).getString("variant").orElse(""));
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_withoutDesiredOutputKeyShouldPreserveNormalPlannerChoiceWhenDebugFixturesExist() throws Exception {
        Item fixtureItem = Items.CRAFTING_TABLE;
        ItemStack redFixtureOutput = debugFixtureOutput("debug/handheld_crafter_red", fixtureItem);
        ItemStack blueFixtureOutput = debugFixtureOutput("debug/handheld_crafter_blue", fixtureItem);
        ItemStack redWool = stackWithVariant(Items.WHITE_WOOL, "red-source");
        ItemStack blueWool = stackWithVariant(Items.WHITE_WOOL, "blue-source");
        ItemStack oakLog = new ItemStack(Items.OAK_LOG);

        RecipeHolder<CraftingRecipe> normalRecipe = mockRecipe(new ItemStack(fixtureItem), ingredientOf(oakLog), "recursivecraft:handheld_crafter");
        RecipeHolder<CraftingRecipe> redFixtureRecipe = mockRecipe(
                redFixtureOutput.copy(),
                ingredientOf(redWool),
                "recursivecraft:debug/handheld_crafter_red"
        );
        RecipeHolder<CraftingRecipe> blueFixtureRecipe = mockRecipe(
                blueFixtureOutput.copy(),
                ingredientOf(blueWool),
                "recursivecraft:debug/handheld_crafter_blue"
        );
        setPlanningResult(
                Map.of(fixtureItem, normalRecipe),
                Map.of(fixtureItem, 1.0d, Items.OAK_LOG, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(fixtureItem, List.of(redFixtureRecipe, blueFixtureRecipe, normalRecipe))
        );

        Inventory inv = mockInventory(oakLog, redWool, blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);

        CraftingTransaction tx = calc.calculate(fixtureItem, 1, true);

        assertEquals(1, tx.getMaterialNeeds().getOrDefault(materialKey(oakLog), 0));
        assertFalse(tx.getMaterialNeeds().containsKey(materialKey(redWool)));
        assertFalse(tx.getMaterialNeeds().containsKey(materialKey(blueWool)));
        assertEquals(1, tx.getResolvedOutputs().size());
        assertTrue(customData(tx.getResolvedOutputs().get(0)) == null);
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_overloadWithNullDesiredOutputKeyShouldPreserveLegacySemantics() {
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue-output");
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG));
        TransactionCalculator calc = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(blueStick.copy(), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:blue_stick");

        CraftingTransaction legacy = calc.calculate(Items.STICK, 1, true, recipe);
        CraftingTransaction withNullDesired = calc.calculate(Items.STICK, 1, true, recipe, null);

        assertEquals(legacy.getNeeds(), withNullDesired.getNeeds());
        assertEquals(legacy.getMaterialNeeds(), withNullDesired.getMaterialNeeds());
        assertEquals(legacy.getProvides(), withNullDesired.getProvides());
        assertEquals(legacy.getResolvedOutputs().size(), withNullDesired.getResolvedOutputs().size());
        assertEquals(
                customData(legacy.getResolvedOutputs().get(0)).getString("variant").orElse(""),
                customData(withNullDesired.getResolvedOutputs().get(0)).getString("variant").orElse("")
        );
        assertEquals(legacy.isUnsupported(), withNullDesired.isUnsupported());
    }

    private static Inventory mockInventory(ItemStack... stacks) {
        Inventory inv = mock(Inventory.class);
        when(inv.getContainerSize()).thenReturn(stacks.length);
        for (int i = 0; i < stacks.length; i++) {
            when(inv.getItem(i)).thenReturn(stacks[i]);
        }
        return inv;
    }

    private static RecipeHolder<CraftingRecipe> mockRecipe(ItemStack result, Ingredient ingredient, String id) {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ingredient);
        PlacementInfo info = PlacementInfo.create(ingredients);
        SlotDisplay resultDisplay = new SlotDisplay.ItemStackSlotDisplay(result.copy());
        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(List.of(), resultDisplay, SlotDisplay.Empty.INSTANCE);
        List<RecipeDisplay> displays = List.of(display);
        when(recipe.placementInfo()).thenReturn(info);
        when(recipe.display()).thenReturn(displays);
        return new RecipeHolder<>(recipeKey(id), recipe);
    }

    private static Ingredient ingredientOf(ItemStack... options) {
        Ingredient ingredient = mock(Ingredient.class);
        ItemStack[] copies = new ItemStack[options.length];
        for (int i = 0; i < options.length; i++) {
            copies[i] = options[i].copy();
        }
        List<Holder<net.minecraft.world.item.Item>> holders = Stream.of(copies).map(s -> Holder.direct(s.getItem())).toList();
        when(ingredient.items()).thenAnswer(inv -> holders.stream());
        when(ingredient.isEmpty()).thenReturn(options.length == 0);
        if (options.length == 1) {
            when(ingredient.display()).thenReturn(new SlotDisplay.ItemStackSlotDisplay(copies[0]));
        } else if (options.length > 1) {
            List<SlotDisplay> displays = new ArrayList<>();
            for (ItemStack copy : copies) {
                displays.add(new SlotDisplay.ItemStackSlotDisplay(copy));
            }
            when(ingredient.display()).thenReturn(new SlotDisplay.Composite(displays));
        }
        return ingredient;
    }

    private static ItemStack stackWithVariant(Item item, String variant) {
        ItemStack stack = new ItemStack(item);
        applyCustomData(stack, tagWithString("variant", variant));
        return stack;
    }

    private static ItemStack stackWithDebugVariant(Item item, String variant) {
        ItemStack stack = new ItemStack(item);
        CompoundTag debugTag = new CompoundTag();
        debugTag.putString("variant", variant);
        CompoundTag tag = new CompoundTag();
        tag.put("recursivecraft_debug", debugTag);
        applyCustomData(stack, tag);
        return stack;
    }

    private static MaterialKey materialKey(ItemStack stack) {
        return NORMALIZER.normalize(stack).key();
    }

    private static void setPlanningResult(Map<Item, RecipeHolder<CraftingRecipe>> pathMemo,
                                          Map<Item, Double> costMemo,
                                          Map<Item, List<RecipeHolder<CraftingRecipe>>> recipeLookup) throws Exception {
        Constructor<CraftingPlanner.PlanningResult> constructor =
                CraftingPlanner.PlanningResult.class.getDeclaredConstructor(Map.class, Map.class, Map.class);
        constructor.setAccessible(true);
        CraftingPlanner.PlanningResult result = constructor.newInstance(
                new HashMap<>(pathMemo),
                new HashMap<>(costMemo),
                new HashMap<>(recipeLookup)
        );
        Field resultField = CraftingPlanner.class.getDeclaredField("result");
        resultField.setAccessible(true);
        resultField.set(CraftingPlanner.getInstance(), result);
    }

    private static JsonObject readRecipeJson(String recipePath) throws IOException {
        String resourcePath = "data/recursivecraft/recipes/" + recipePath + ".json";
        try (InputStream input = TransactionCalculatorNbtTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(input, "Missing recipe resource: " + resourcePath);
            return GsonHelper.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static ResourceKey<Recipe<?>> recipeKey(String id) {
        return ResourceKey.create(Registries.RECIPE, ResourceLocation.parse(id));
    }

    private static double plannerRecipeCostForResource(String recipePath, Map<Item, Double> costTable) throws Exception {
        return invokePlannerRecipeCost(recipeForPlannerCost(readRecipeJson(recipePath), recipeKey("recursivecraft:" + recipePath)), costTable);
    }

    private static String resultItemId(JsonObject recipeJson) {
        return GsonHelper.getAsJsonObject(recipeJson, "result").get("item").getAsString();
    }

    private static String debugFixtureVariant(JsonObject recipeJson) {
        int redstoneCount = countIngredientItem(recipeJson, "minecraft:redstone_block");
        return redstoneCount > 0 ? "red" : "blue";
    }

    private static ItemStack debugFixtureOutput(String recipePath, Item item) throws IOException {
        return stackWithDebugVariant(item, debugFixtureVariant(readRecipeJson(recipePath)));
    }

    private static java.util.Optional<String> fixtureVariant(ItemStack stack) {
        return customData(stack).getCompoundOrEmpty("recursivecraft_debug").getString("variant");
    }

    private static int countIngredientItem(JsonObject recipeJson, String itemId) {
        int count = 0;
        JsonArray ingredients = GsonHelper.getAsJsonArray(recipeJson, "ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            JsonObject ingredient = ingredients.get(i).getAsJsonObject();
            if (itemId.equals(ingredient.get("item").getAsString())) {
                count++;
            }
        }
        return count;
    }

    private static int countPatternSymbolUses(JsonObject recipeJson, String itemId) {
        JsonObject key = GsonHelper.getAsJsonObject(recipeJson, "key");
        String targetSymbol = null;
        for (String symbol : key.keySet()) {
            JsonObject value = key.getAsJsonObject(symbol);
            if (value.has("item") && itemId.equals(value.get("item").getAsString())) {
                targetSymbol = symbol;
                break;
            }
        }
        assertNotNull(targetSymbol, "Missing shaped key for " + itemId);

        int count = 0;
        JsonArray pattern = GsonHelper.getAsJsonArray(recipeJson, "pattern");
        for (int i = 0; i < pattern.size(); i++) {
            String row = pattern.get(i).getAsString();
            for (int j = 0; j < row.length(); j++) {
                if (targetSymbol.charAt(0) == row.charAt(j)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static double invokePlannerRecipeCost(CraftingRecipe recipe, Map<Item, Double> costTable) throws Exception {
        Method method = CraftingPlanner.class.getDeclaredMethod("calculateRecipeCost", CraftingRecipe.class, Map.class);
        method.setAccessible(true);
        return (double) method.invoke(null, recipe, costTable);
    }

    private static CraftingRecipe recipeForPlannerCost(JsonObject recipeJson, ResourceKey<Recipe<?>> id) {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        PlacementInfo info = PlacementInfo.create(plannerCostIngredients(recipeJson));
        ItemStack resultStack = new ItemStack(mapPlannerCostItem(resultItemId(recipeJson)), plannerCostResultCount(recipeJson));
        SlotDisplay resultDisplay = new SlotDisplay.ItemStackSlotDisplay(resultStack);
        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(List.of(), resultDisplay, SlotDisplay.Empty.INSTANCE);
        List<RecipeDisplay> displays = List.of(display);
        when(recipe.placementInfo()).thenReturn(info);
        when(recipe.display()).thenReturn(displays);
        return recipe;
    }

    private static int plannerCostResultCount(JsonObject recipeJson) {
        return GsonHelper.getAsInt(GsonHelper.getAsJsonObject(recipeJson, "result"), "count", 1);
    }

    private static NonNullList<Ingredient> plannerCostIngredients(JsonObject recipeJson) {
        if (recipeJson.has("ingredients")) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            JsonArray ingredientArray = GsonHelper.getAsJsonArray(recipeJson, "ingredients");
            for (int i = 0; i < ingredientArray.size(); i++) {
                ingredients.add(plannerCostIngredient(ingredientArray.get(i).getAsJsonObject()));
            }
            return ingredients;
        }

        NonNullList<Ingredient> ingredients = NonNullList.create();
        JsonObject key = GsonHelper.getAsJsonObject(recipeJson, "key");
        JsonArray pattern = GsonHelper.getAsJsonArray(recipeJson, "pattern");
        for (int i = 0; i < pattern.size(); i++) {
            String row = pattern.get(i).getAsString();
            for (int j = 0; j < row.length(); j++) {
                char symbol = row.charAt(j);
                if (symbol == ' ') {
                    continue;
                }
                ingredients.add(plannerCostIngredient(key.getAsJsonObject(String.valueOf(symbol))));
            }
        }
        return ingredients;
    }

    private static Ingredient plannerCostIngredient(JsonObject ingredientJson) {
        return Ingredient.of(mapPlannerCostItem(ingredientJson.get("item").getAsString()));
    }

    private static Item mapPlannerCostItem(String itemId) {
        return switch (itemId) {
            case "recursivecraft:recursive_crafter" -> Items.DIAMOND;
            case "recursivecraft:handheld_crafter" -> Items.CRAFTING_TABLE;
            case "minecraft:crafting_table" -> Items.CRAFTING_TABLE;
            case "minecraft:redstone_block" -> Items.REDSTONE_BLOCK;
            case "minecraft:lapis_block" -> Items.LAPIS_BLOCK;
            default -> throw new IllegalArgumentException("No planner-cost item mapping for " + itemId);
        };
    }

    private static void applyCustomData(ItemStack stack, CompoundTag tag) {
        stack.applyComponentsAndValidate(ItemStackComponentSupport.patchFromCustomData(tag));
    }

    private static CompoundTag customData(ItemStack stack) {
        return ItemStackComponentSupport.copyCustomData(stack);
    }

    private static CompoundTag tagWithString(String key, String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString(key, value);
        return tag;
    }

    private static CompoundTag tagWithEntry(String key, Tag value) {
        CompoundTag tag = new CompoundTag();
        tag.put(key, value);
        return tag;
    }
}
