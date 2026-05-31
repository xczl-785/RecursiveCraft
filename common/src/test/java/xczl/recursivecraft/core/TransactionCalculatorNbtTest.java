package xczl.recursivecraft.core;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.EndTag;
import net.minecraft.resources.ResourceLocation;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
