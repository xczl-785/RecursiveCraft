package xczl.recursivecraft.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.EndTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionCalculatorNbtTest {
    private static final DefaultMaterialIdentityNormalizer NORMALIZER = new DefaultMaterialIdentityNormalizer();

    @BeforeAll static void init(){ MinecraftTestBootstrap.init(); }

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
        when(recipe.getIngredients()).thenReturn(ingredients);
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.OAK_PLANKS, 4));
        when(recipe.getId()).thenReturn(new ResourceLocation("test:r"));
        CraftingTransaction tx = calc.calculate(Items.OAK_PLANKS, 4, true, recipe);
        assertFalse(tx.getMaterialNeeds().isEmpty());
    }

    @Test
    void calculate_shouldNotConsumeMismatchedInventoryVariantForSingleCandidateIngredient() {
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue");
        ItemStack redStick = stackWithVariant(Items.STICK, "red");
        Inventory inv = mockInventory(blueStick);
        TransactionCalculator calc = new TransactionCalculator(inv);

        CraftingRecipe recipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(redStick), "test:torch_from_red_stick");

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

        CraftingRecipe recipe = mockRecipe(
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

        CraftingRecipe preferredBlueStickRecipe = mockRecipe(blueStick, ingredientOf(blueWool), "test:blue_stick");
        CraftingRecipe alternateRedStickRecipe = mockRecipe(redStick, ingredientOf(redWool), "test:red_stick");
        setPlanningResult(
                Map.of(Items.STICK, preferredBlueStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(Items.STICK, List.of(preferredBlueStickRecipe, alternateRedStickRecipe))
        );

        Inventory inv = mockInventory(redWool, blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe parentRecipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(redStick), "test:torch_from_red_stick");

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

        CraftingRecipe redStickRecipe = mockRecipe(redStick, ingredientOf(blueStick), "test:red_stick_from_blue_stick");
        CraftingRecipe blueStickRecipe = mockRecipe(blueStick, ingredientOf(blueWool), "test:blue_stick_from_blue_wool");
        setPlanningResult(
                Map.of(Items.STICK, redStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(Items.STICK, List.of(redStickRecipe, blueStickRecipe))
        );

        Inventory inv = mockInventory(blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe parentRecipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(redStick), "test:torch_from_red_stick");

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

        CraftingRecipe blueStickRecipe = mockRecipe(craftableStick, ingredientOf(blueWool), "test:craftable_blue_stick");
        setPlanningResult(
                Map.of(Items.STICK, blueStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(Items.STICK, List.of(blueStickRecipe))
        );

        Inventory inv = mockInventory(blueWool);
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe parentRecipe = mockRecipe(
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
        CraftingRecipe recipe = mockRecipe(redStickOutput.copyWithCount(2), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:red_stick_output");

        CraftingTransaction tx = calc.calculate(Items.STICK, 1, true, recipe);

        assertEquals(1, tx.getResolvedOutputs().size());
        ItemStack resolved = tx.getResolvedOutputs().get(0);
        assertEquals(Items.STICK, resolved.getItem());
        assertEquals(2, resolved.getCount());
        assertNotNull(resolved.getTag());
        assertEquals("red-output", resolved.getTag().getString("variant"));
    }

    @Test
    void calculate_shouldReturnUnsupportedWhenIngredientIdentityCannotBeNormalized() {
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        unsupportedStick.getOrCreateTag().put("unsupported", EndTag.INSTANCE);
        Inventory inv = mockInventory();
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe recipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(unsupportedStick), "test:unsupported_stick");

        CraftingTransaction tx = calc.calculate(Items.TORCH, 1, true, recipe);

        assertTrue(tx.isUnsupported());
        assertTrue(tx.getResolvedOutputs().isEmpty());
    }

    @Test
    void calculate_shouldAggregateIngredientCandidateMissingAndUnsupportedAsUnsupported() {
        ItemStack missingStick = stackWithVariant(Items.STICK, "missing");
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        unsupportedStick.getOrCreateTag().put("unsupported", EndTag.INSTANCE);
        Inventory inv = mockInventory();
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe recipe = mockRecipe(
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
        unsupportedStick.getOrCreateTag().put("unsupported", EndTag.INSTANCE);

        CraftingRecipe missingRecipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:missing_recipe");
        CraftingRecipe unsupportedRecipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(unsupportedStick), "test:unsupported_recipe");
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

        ResourceLocation redId = recipeId("debug/handheld_crafter_red");
        ResourceLocation blueId = recipeId("debug/handheld_crafter_blue");
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
                  "type": "recursivecraft:debug_tagged_result",
                  "ingredients": [
                    { "item": "recursivecraft:recursive_crafter" }
                  ],
                  "result": {
                    "item": "recursivecraft:handheld_crafter",
                    "count": 4,
                    "tag": {
                      "recursivecraft_debug": {
                        "variant": "count-check"
                      }
                    }
                  }
                }
                """);
        Map<Item, Double> costTable = Map.of(Items.DIAMOND, 10.0d);

        double cost = invokePlannerRecipeCost(
                recipeForPlannerCost(recipeJson, new ResourceLocation("recursivecraft", "count_check")),
                costTable
        );

        assertEquals(2.525d, cost);
    }

    @Test
    void calculate_overloadShouldAcceptDesiredOutputKey() {
        ItemStack redStick = stackWithVariant(Items.STICK, "red-output");
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG));
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe recipe = mockRecipe(redStick.copy(), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:red_stick");

        CraftingTransaction tx = calc.calculate(Items.STICK, 1, true, recipe, materialKey(redStick));

        assertEquals(1, tx.getResolvedOutputs().size());
        assertEquals("red-output", tx.getResolvedOutputs().get(0).getTag().getString("variant"));
        assertEquals(1, tx.getMaterialNeeds().values().stream().mapToInt(Integer::intValue).sum());
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_overloadShouldFilterRecipeOutputIdentityMismatch() {
        ItemStack redStick = stackWithVariant(Items.STICK, "red-output");
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue-output");
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG));
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe recipe = mockRecipe(blueStick.copy(), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:blue_stick");

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

        CraftingRecipe preferredBlueRecipe = mockRecipe(
                blueFixtureOutput.copy(),
                ingredientOf(blueWool),
                "recursivecraft:debug/handheld_crafter_blue"
        );
        CraftingRecipe alternateRedRecipe = mockRecipe(
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
        assertEquals("red", fixtureVariant(tx.getResolvedOutputs().get(0)));
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_overloadShouldSelectDesiredOutputVariantAmongRecipeCandidates() throws Exception {
        ItemStack redStick = stackWithVariant(Items.STICK, "red-output");
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue-output");
        ItemStack redWool = stackWithVariant(Items.WHITE_WOOL, "red-source");
        ItemStack blueWool = stackWithVariant(Items.WHITE_WOOL, "blue-source");

        CraftingRecipe preferredBlueRecipe = mockRecipe(blueStick.copy(), ingredientOf(blueWool), "test:blue_stick");
        CraftingRecipe alternateRedRecipe = mockRecipe(redStick.copy(), ingredientOf(redWool), "test:red_stick");
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
        assertEquals("red-output", tx.getResolvedOutputs().get(0).getTag().getString("variant"));
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

        CraftingRecipe normalRecipe = mockRecipe(new ItemStack(fixtureItem), ingredientOf(oakLog), "recursivecraft:handheld_crafter");
        CraftingRecipe redFixtureRecipe = mockRecipe(
                redFixtureOutput.copy(),
                ingredientOf(redWool),
                "recursivecraft:debug/handheld_crafter_red"
        );
        CraftingRecipe blueFixtureRecipe = mockRecipe(
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
        assertTrue(tx.getResolvedOutputs().get(0).getTag() == null);
        assertFalse(tx.isUnsupported());
    }

    @Test
    void calculate_overloadWithNullDesiredOutputKeyShouldPreserveLegacySemantics() {
        ItemStack blueStick = stackWithVariant(Items.STICK, "blue-output");
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG));
        TransactionCalculator calc = new TransactionCalculator(inv);
        CraftingRecipe recipe = mockRecipe(blueStick.copy(), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:blue_stick");

        CraftingTransaction legacy = calc.calculate(Items.STICK, 1, true, recipe);
        CraftingTransaction withNullDesired = calc.calculate(Items.STICK, 1, true, recipe, null);

        assertEquals(legacy.getNeeds(), withNullDesired.getNeeds());
        assertEquals(legacy.getMaterialNeeds(), withNullDesired.getMaterialNeeds());
        assertEquals(legacy.getProvides(), withNullDesired.getProvides());
        assertEquals(legacy.getResolvedOutputs().size(), withNullDesired.getResolvedOutputs().size());
        assertEquals(
                legacy.getResolvedOutputs().get(0).getTag().getString("variant"),
                withNullDesired.getResolvedOutputs().get(0).getTag().getString("variant")
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

    private static CraftingRecipe mockRecipe(ItemStack result, Ingredient ingredient, String id) {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ingredient);
        when(recipe.getIngredients()).thenReturn(ingredients);
        when(recipe.getResultItem(any())).thenReturn(result.copy());
        when(recipe.getId()).thenReturn(new ResourceLocation(id));
        return recipe;
    }

    private static Ingredient ingredientOf(ItemStack... options) {
        Ingredient ingredient = mock(Ingredient.class);
        ItemStack[] copies = new ItemStack[options.length];
        for (int i = 0; i < options.length; i++) {
            copies[i] = options[i].copy();
        }
        when(ingredient.getItems()).thenReturn(copies);
        when(ingredient.isEmpty()).thenReturn(options.length == 0);
        return ingredient;
    }

    private static ItemStack stackWithVariant(Item item, String variant) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("variant", variant);
        return stack;
    }

    private static ItemStack stackWithDebugVariant(Item item, String variant) {
        ItemStack stack = new ItemStack(item);
        net.minecraft.nbt.CompoundTag debugTag = new net.minecraft.nbt.CompoundTag();
        debugTag.putString("variant", variant);
        stack.getOrCreateTag().put("recursivecraft_debug", debugTag);
        return stack;
    }

    private static MaterialKey materialKey(ItemStack stack) {
        return NORMALIZER.normalize(stack).key();
    }

    private static void setPlanningResult(Map<Item, CraftingRecipe> pathMemo,
                                          Map<Item, Double> costMemo,
                                          Map<Item, List<CraftingRecipe>> recipeLookup) throws Exception {
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

    private static ResourceLocation recipeId(String recipePath) {
        return new ResourceLocation("recursivecraft", recipePath);
    }

    private static double plannerRecipeCostForResource(String recipePath, Map<Item, Double> costTable) throws Exception {
        return invokePlannerRecipeCost(recipeForPlannerCost(readRecipeJson(recipePath), recipeId(recipePath)), costTable);
    }

    private static String resultItemId(JsonObject recipeJson) {
        return GsonHelper.getAsJsonObject(recipeJson, "result").get("item").getAsString();
    }

    private static String debugFixtureVariant(JsonObject recipeJson) {
        return GsonHelper.getAsJsonObject(
                GsonHelper.getAsJsonObject(
                        GsonHelper.getAsJsonObject(recipeJson, "result"),
                        "tag"
                ),
                "recursivecraft_debug"
        ).get("variant").getAsString();
    }

    private static ItemStack debugFixtureOutput(String recipePath, Item item) throws IOException {
        return stackWithDebugVariant(item, debugFixtureVariant(readRecipeJson(recipePath)));
    }

    private static String fixtureVariant(ItemStack stack) {
        return stack.getTag().getCompound("recursivecraft_debug").getString("variant");
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

    private static CraftingRecipe recipeForPlannerCost(JsonObject recipeJson, ResourceLocation id) {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        when(recipe.getIngredients()).thenReturn(plannerCostIngredients(recipeJson));
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.STICK, plannerCostResultCount(recipeJson)));
        when(recipe.getId()).thenReturn(id);
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
        return Ingredient.of(new ItemStack(mapPlannerCostItem(ingredientJson.get("item").getAsString())));
    }

    private static Item mapPlannerCostItem(String itemId) {
        return switch (itemId) {
            case "recursivecraft:recursive_crafter" -> Items.DIAMOND;
            case "minecraft:crafting_table" -> Items.CRAFTING_TABLE;
            case "minecraft:redstone_block" -> Items.REDSTONE_BLOCK;
            case "minecraft:lapis_block" -> Items.LAPIS_BLOCK;
            default -> throw new IllegalArgumentException("No planner-cost item mapping for " + itemId);
        };
    }
}
