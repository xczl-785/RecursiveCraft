package xczl.recursivecraft.core;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.material.ItemStackComponentSupport;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionCalculatorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @BeforeEach
    void resetPlannerCaches() {
        try {
            Field resultField = CraftingPlanner.class.getDeclaredField("result");
            resultField.setAccessible(true);
            resultField.set(CraftingPlanner.getInstance(), CraftingPlanner.PlanningResult.EMPTY);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset CraftingPlanner for test", e);
        }
    }

    @Test
    void calculate_withForcedRecipe_shouldNotRecordConsumedInventoryAsNeed() {
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);

        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );

        CraftingTransaction tx = calculator.calculate(Items.OAK_PLANKS, 4, true, recipe);
        Map<Item, Integer> needs = tx.getNeeds();

        assertEquals(0, needs.getOrDefault(Items.OAK_LOG, 0));
        assertEquals(1, tx.getMaterialNeeds().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(4, tx.getProvides().getOrDefault(Items.OAK_PLANKS, 0));
        assertEquals(1, tx.getResolvedOutputs().size());
        assertEquals(4, tx.getResolvedOutputs().get(0).getCount());
        assertNotNull(tx.getResolvedOutputs().get(0));
    }

    @Test
    void calculate_whenRecipeCyclesToSelf_shouldStopAtCycleAndReturnNeed() {
        Inventory inv = mockInventory();
        TransactionCalculator calculator = new TransactionCalculator(inv);

        RecipeHolder<CraftingRecipe> cycleRecipe = mockRecipe(
                Items.STICK, 1,
                Ingredient.of(Items.STICK),
                "test:stick_from_stick"
        );

        CraftingTransaction tx = calculator.calculate(Items.STICK, 1, true, cycleRecipe);
        assertEquals(1, tx.getNeeds().getOrDefault(Items.STICK, 0));
    }

    @Test
    void resolveIngredient_withMultipleOptions_shouldPreferInventoryAvailableOptionWithoutNeedBridgeLeak() {
        Inventory inv = mockInventory(new ItemStack(Items.DIORITE, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);

        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.STICK, 1,
                Ingredient.of(Items.COBBLESTONE, Items.DIORITE),
                "test:stick_from_stone_options"
        );

        CraftingTransaction tx = calculator.calculate(Items.STICK, 1, true, recipe);
        assertEquals(0, tx.getNeeds().getOrDefault(Items.DIORITE, 0));
        assertEquals(0, tx.getNeeds().getOrDefault(Items.COBBLESTONE, 0));
        assertEquals(1, tx.getMaterialNeeds().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void calculate_withoutInventoryForIngredient_shouldReturnMissingNeed() {
        Inventory inv = mockInventory();
        TransactionCalculator calculator = new TransactionCalculator(inv);

        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.STICK, 1,
                Ingredient.of(Items.OAK_LOG),
                "test:stick_from_log"
        );

        CraftingTransaction tx = calculator.calculate(Items.STICK, 1, true, recipe);
        assertEquals(1, tx.getNeeds().getOrDefault(Items.OAK_LOG, 0));
        assertFalse(tx.getNeeds().containsKey(Items.STICK));
    }

    @Test
    void calculate_shouldUseCraftedIntermediateMaterialsWithoutRequiringThemInPlayerInventory() throws Exception {
        RecipeHolder<CraftingRecipe> planksRecipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );
        RecipeHolder<CraftingRecipe> stickRecipe = mockRecipeWithIngredients(
                Items.STICK, 4,
                "test:sticks_from_planks",
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS)
        );
        RecipeHolder<CraftingRecipe> axeRecipe = mockRecipeWithIngredients(
                Items.WOODEN_AXE, 1,
                "test:wooden_axe",
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.STICK),
                Ingredient.of(Items.STICK)
        );
        setPlanningResult(
                Map.of(Items.OAK_PLANKS, planksRecipe, Items.STICK, stickRecipe),
                Map.of(Items.OAK_PLANKS, 1.0, Items.STICK, 2.0, Items.WOODEN_AXE, 4.0),
                Map.of(Items.OAK_PLANKS, List.of(planksRecipe), Items.STICK, List.of(stickRecipe))
        );
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 2));
        TransactionCalculator calculator = new TransactionCalculator(inv);

        CraftingTransaction tx = calculator.calculate(Items.WOODEN_AXE, 1, true, axeRecipe);

        assertEquals(2, tx.getMaterialNeeds().values().stream().mapToInt(Integer::intValue).sum());
        tx.getMaterialNeeds().forEach((key, amount) -> assertEquals(Items.OAK_LOG, key.item()));
        assertEquals(1, tx.getProvides().getOrDefault(Items.WOODEN_AXE, 0));
        assertFalse(tx.getMaterialNeeds().keySet().stream().anyMatch(key -> key.item() == Items.OAK_PLANKS));
        assertFalse(tx.getMaterialNeeds().keySet().stream().anyMatch(key -> key.item() == Items.STICK));
    }

    @Test
    void calculate_shouldTreatDefaultDamageTagAsPlainInventoryMaterial() {
        ItemStack plainLog = new ItemStack(Items.OAK_LOG, 1);
        ItemStack ingredientLog = stackWithDefaultDamageTag(Items.OAK_LOG);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                ingredientOfStack(ingredientLog),
                "test:planks_from_log_with_default_damage"
        );
        Inventory inv = mockInventory(plainLog);
        TransactionCalculator calculator = new TransactionCalculator(inv);

        CraftingTransaction tx = calculator.calculate(Items.OAK_PLANKS, 4, true, recipe);

        assertEquals(1, tx.getMaterialNeeds().values().stream().mapToInt(Integer::intValue).sum());
        tx.getMaterialNeeds().forEach((key, amount) -> {
            assertEquals(Items.OAK_LOG, key.item());
            assertFalse(key.payload().fields().stream().anyMatch(field -> field.key().equals("nbt:Damage")));
        });
    }

    @Test
    void diagnoseTopLevelMissing_shouldReportMissingFirstLevelBaseMaterial() {
        Inventory inv = mockInventory();
        TransactionCalculator calculator = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.TORCH, 1,
                Ingredient.of(Items.OAK_LOG),
                "test:torch_from_log"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe.value(), 1);

        assertFalse(report.unsupported());
        assertEquals(1, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.missingMaterials().keySet().stream().anyMatch(key -> key.item() == Items.OAK_LOG));
    }

    @Test
    void diagnoseTopLevelMissing_shouldReturnEmptyWhenFirstLevelBaseMaterialIsAvailable() {
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.TORCH, 1,
                Ingredient.of(Items.OAK_LOG),
                "test:torch_from_available_log"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe.value(), 1);

        assertFalse(report.unsupported());
        assertTrue(report.missingMaterials().isEmpty());
    }

    @Test
    void diagnoseTopLevelMissing_shouldNotReportFirstLevelMaterialCraftableFromInventory() throws Exception {
        RecipeHolder<CraftingRecipe> planksRecipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );
        setPlanningResult(
                Map.of(Items.OAK_PLANKS, planksRecipe),
                Map.of(Items.OAK_PLANKS, 1.0, Items.OAK_LOG, 1.0, Items.DIAMOND, Double.MAX_VALUE),
                Map.of(Items.OAK_PLANKS, List.of(planksRecipe))
        );
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipeWithIngredients(
                Items.TORCH, 1,
                "test:torch_from_planks_and_diamond",
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.DIAMOND)
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe.value(), 1);

        assertFalse(report.unsupported());
        assertEquals(1, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertFalse(report.missingMaterials().keySet().stream().anyMatch(key -> key.item() == Items.OAK_PLANKS));
        assertTrue(report.missingMaterials().keySet().stream().anyMatch(key -> key.item() == Items.DIAMOND));
    }

    @Test
    void diagnoseTopLevelMissing_shouldAttributeSharedResourceShortageToUnsatisfiedFirstLevelMaterial() throws Exception {
        RecipeHolder<CraftingRecipe> planksRecipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );
        RecipeHolder<CraftingRecipe> stickRecipe = mockRecipeWithIngredients(
                Items.STICK, 4,
                "test:sticks_from_planks",
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS)
        );
        setPlanningResult(
                Map.of(Items.OAK_PLANKS, planksRecipe, Items.STICK, stickRecipe),
                Map.of(Items.OAK_PLANKS, 1.0, Items.STICK, 2.0, Items.OAK_LOG, 1.0),
                Map.of(Items.OAK_PLANKS, List.of(planksRecipe), Items.STICK, List.of(stickRecipe))
        );
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipeWithIngredients(
                Items.WOODEN_AXE, 1,
                "test:axe_from_planks_and_sticks",
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.STICK),
                Ingredient.of(Items.STICK)
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe.value(), 1);

        assertFalse(report.unsupported());
        assertEquals(2, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.missingMaterials().keySet().stream().allMatch(key -> key.item() == Items.STICK));
    }

    @Test
    void diagnoseTopLevelMissing_shouldMultiplyMissingMaterialsByRecipeRuns() {
        Inventory inv = mockInventory();
        TransactionCalculator calculator = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.TORCH, 2,
                Ingredient.of(Items.DIAMOND),
                "test:two_torches_from_diamond"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe.value(), 5);

        assertFalse(report.unsupported());
        assertEquals(3, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.missingMaterials().keySet().stream().allMatch(key -> key.item() == Items.DIAMOND));
    }

    @Test
    void diagnoseTopLevelMissing_shouldSatisfyMultiCandidateIngredientWhenAnyOptionIsCraftable() throws Exception {
        RecipeHolder<CraftingRecipe> planksRecipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );
        setPlanningResult(
                Map.of(Items.OAK_PLANKS, planksRecipe),
                Map.of(Items.OAK_PLANKS, 1.0, Items.OAK_LOG, 1.0, Items.DIAMOND, Double.MAX_VALUE),
                Map.of(Items.OAK_PLANKS, List.of(planksRecipe))
        );
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.TORCH, 1,
                Ingredient.of(Items.DIAMOND, Items.OAK_PLANKS),
                "test:torch_from_diamond_or_planks"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe.value(), 1);

        assertFalse(report.unsupported());
        assertTrue(report.missingMaterials().isEmpty());
    }

    @Test
    void diagnoseTopLevelMissing_shouldReturnUnsupportedForUnsupportedFirstLevelIngredient() {
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        applyCustomData(unsupportedStick, tagWithEntry("unsupported", EndTag.INSTANCE));
        Inventory inv = mockInventory();
        TransactionCalculator calculator = new TransactionCalculator(inv);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.TORCH, 1,
                ingredientOfStack(unsupportedStick),
                "test:torch_from_unsupported_stick"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe.value(), 1);

        assertTrue(report.unsupported());
        assertTrue(report.missingMaterials().isEmpty());
    }

    @Test
    void diagnoseJeiDisplayedTopLevelMissing_shouldReturnUnsupportedForUnsupportedDisplayedIngredient() {
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        applyCustomData(unsupportedStick, tagWithEntry("unsupported", EndTag.INSTANCE));
        Inventory inv = mockInventory();
        TransactionCalculator calculator = new TransactionCalculator(inv);

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseJeiDisplayedTopLevelMissing(
                List.of(unsupportedStick),
                new ItemStack(Items.TORCH, 1),
                1
        );

        assertTrue(report.unsupported());
        assertTrue(report.missingMaterials().isEmpty());
    }

    @Test
    void diagnoseJeiDisplayedTopLevelMissing_shouldUseDisplayedStackCounts() throws Exception {
        RecipeHolder<CraftingRecipe> planksRecipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );
        setPlanningResult(
                Map.of(Items.OAK_PLANKS, planksRecipe),
                Map.of(Items.OAK_PLANKS, 1.0, Items.OAK_LOG, 1.0, Items.DIAMOND, Double.MAX_VALUE),
                Map.of(Items.OAK_PLANKS, List.of(planksRecipe))
        );
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseJeiDisplayedTopLevelMissing(
                List.of(new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.DIAMOND, 1)),
                new ItemStack(Items.TORCH, 1),
                1
        );

        assertFalse(report.unsupported());
        assertEquals(1, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.missingMaterials().keySet().stream().anyMatch(key -> key.item() == Items.DIAMOND));
    }

    @Test
    void calculate_shouldKeepNonZeroDamageIngredientDistinctFromUndamagedInventoryStack() {
        ItemStack undamagedPickaxe = new ItemStack(Items.DIAMOND_PICKAXE, 1);
        ItemStack damagedPickaxeRequirement = stackWithDamageValue(Items.DIAMOND_PICKAXE, 5);
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                Items.TORCH, 1,
                ingredientOfStack(damagedPickaxeRequirement),
                "test:torch_from_damaged_pickaxe"
        );
        Inventory inv = mockInventory(undamagedPickaxe);
        TransactionCalculator calculator = new TransactionCalculator(inv);

        CraftingTransaction tx = calculator.calculate(Items.TORCH, 1, true, recipe);

        assertEquals(1, tx.getMaterialNeeds().values().stream().mapToInt(Integer::intValue).sum());
        tx.getMaterialNeeds().forEach((key, amount) -> {
            assertEquals(Items.DIAMOND_PICKAXE, key.item());
            assertTrue(key.payload().fields().stream().anyMatch(field -> field.key().equals("damage") && field.value().equals("5")));
        });
        assertFalse(tx.getProvides().containsKey(Items.TORCH));
    }

    private static Inventory mockInventory(ItemStack... stacks) {
        Inventory inv = mock(Inventory.class);
        when(inv.getContainerSize()).thenReturn(stacks.length);
        for (int i = 0; i < stacks.length; i++) {
            when(inv.getItem(i)).thenReturn(stacks[i]);
        }
        return inv;
    }

    private static RecipeHolder<CraftingRecipe> mockRecipe(Item resultItem, int resultCount, Ingredient ingredient, String id) {
        return mockRecipeWithIngredients(new ItemStack(resultItem, resultCount), id, ingredient);
    }

    private static RecipeHolder<CraftingRecipe> mockRecipeWithIngredients(Item resultItem, int resultCount, String id, Ingredient... recipeIngredients) {
        return mockRecipeWithIngredients(new ItemStack(resultItem, resultCount), id, recipeIngredients);
    }

    private static ItemStack stackWithDefaultDamageTag(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(0);
        return stack;
    }

    private static ItemStack stackWithDamageValue(Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    private static RecipeHolder<CraftingRecipe> mockRecipeWithIngredients(ItemStack resultStack, String id, Ingredient... recipeIngredients) {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (Ingredient ingredient : recipeIngredients) {
            ingredients.add(ingredient);
        }
        PlacementInfo info = PlacementInfo.create(ingredients);
        SlotDisplay resultDisplay = new SlotDisplay.ItemStackSlotDisplay(resultStack.copy());
        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(List.of(), resultDisplay, SlotDisplay.Empty.INSTANCE);
        List<RecipeDisplay> displays = List.of(display);
        when(recipe.placementInfo()).thenReturn(info);
        when(recipe.display()).thenReturn(displays);
        return new RecipeHolder<>(recipeKey(id), recipe);
    }

    private static void setPlanningResult(Map<Item, RecipeHolder<CraftingRecipe>> pathMemo,
                                          Map<Item, Double> costMemo,
                                          Map<Item, List<RecipeHolder<CraftingRecipe>>> recipeLookup) throws Exception {
        Constructor<CraftingPlanner.PlanningResult> constructor =
                CraftingPlanner.PlanningResult.class.getDeclaredConstructor(Map.class, Map.class, Map.class);
        constructor.setAccessible(true);
        CraftingPlanner.PlanningResult result = constructor.newInstance(pathMemo, costMemo, recipeLookup);

        Field resultField = CraftingPlanner.class.getDeclaredField("result");
        resultField.setAccessible(true);
        resultField.set(CraftingPlanner.getInstance(), result);
    }

    private static void applyCustomData(ItemStack stack, CompoundTag tag) {
        stack.applyComponentsAndValidate(ItemStackComponentSupport.patchFromCustomData(tag));
    }

    private static CompoundTag tagWithEntry(String key, Tag value) {
        CompoundTag tag = new CompoundTag();
        tag.put(key, value);
        return tag;
    }

    private static ResourceKey<Recipe<?>> recipeKey(String id) {
        return ResourceKey.create(Registries.RECIPE, ResourceLocation.parse(id));
    }

    private static Ingredient ingredientOfStack(ItemStack stack) {
        Ingredient ingredient = mock(Ingredient.class);
        List<Holder<net.minecraft.world.item.Item>> holders = List.of(Holder.direct(stack.getItem()));
        when(ingredient.items()).thenAnswer(inv -> holders.stream());
        when(ingredient.isEmpty()).thenReturn(false);
        when(ingredient.display()).thenReturn(new SlotDisplay.ItemStackSlotDisplay(stack.copy()));
        return ingredient;
    }
}
