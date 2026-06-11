package xczl.recursivecraft.core;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
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
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionCalculatorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @BeforeEach
    void resetPlannerCaches() {
        // PlanningResult 现在是不可变的，测试中通过反射重置为 EMPTY
        try {
            java.lang.reflect.Field resultField = CraftingPlanner.class.getDeclaredField("result");
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

        CraftingRecipe recipe = mockRecipe(
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

        CraftingRecipe cycleRecipe = mockRecipe(
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

        CraftingRecipe recipe = mockRecipe(
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

        CraftingRecipe recipe = mockRecipe(
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
        CraftingRecipe planksRecipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );
        CraftingRecipe stickRecipe = mockRecipeWithIngredients(
                Items.STICK, 4,
                "test:sticks_from_planks",
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS)
        );
        CraftingRecipe axeRecipe = mockRecipeWithIngredients(
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
        CraftingRecipe recipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(ingredientLog),
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
        CraftingRecipe recipe = mockRecipe(
                Items.TORCH, 1,
                Ingredient.of(Items.OAK_LOG),
                "test:torch_from_log"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe, 1);

        assertFalse(report.unsupported());
        assertEquals(1, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.missingMaterials().keySet().stream().anyMatch(key -> key.item() == Items.OAK_LOG));
    }


    @Test
    void diagnoseTopLevelMissing_shouldReturnEmptyWhenFirstLevelBaseMaterialIsAvailable() {
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);
        CraftingRecipe recipe = mockRecipe(
                Items.TORCH, 1,
                Ingredient.of(Items.OAK_LOG),
                "test:torch_from_available_log"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe, 1);

        assertFalse(report.unsupported());
        assertTrue(report.missingMaterials().isEmpty());
    }

    @Test
    void diagnoseTopLevelMissing_shouldNotReportFirstLevelMaterialCraftableFromInventory() throws Exception {
        CraftingRecipe planksRecipe = mockRecipe(
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
        CraftingRecipe recipe = mockRecipeWithIngredients(
                Items.TORCH, 1,
                "test:torch_from_planks_and_diamond",
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.DIAMOND)
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe, 1);

        assertFalse(report.unsupported());
        assertEquals(1, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertFalse(report.missingMaterials().keySet().stream().anyMatch(key -> key.item() == Items.OAK_PLANKS));
        assertTrue(report.missingMaterials().keySet().stream().anyMatch(key -> key.item() == Items.DIAMOND));
    }

    @Test
    void diagnoseTopLevelMissing_shouldAttributeSharedResourceShortageToUnsatisfiedFirstLevelMaterial() throws Exception {
        CraftingRecipe planksRecipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );
        CraftingRecipe stickRecipe = mockRecipeWithIngredients(
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
        CraftingRecipe recipe = mockRecipeWithIngredients(
                Items.WOODEN_AXE, 1,
                "test:axe_from_planks_and_sticks",
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.OAK_PLANKS),
                Ingredient.of(Items.STICK),
                Ingredient.of(Items.STICK)
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe, 1);

        assertFalse(report.unsupported());
        assertEquals(2, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.missingMaterials().keySet().stream().allMatch(key -> key.item() == Items.STICK));
    }


    @Test
    void diagnoseTopLevelMissing_shouldMultiplyMissingMaterialsByRecipeRuns() {
        Inventory inv = mockInventory();
        TransactionCalculator calculator = new TransactionCalculator(inv);
        CraftingRecipe recipe = mockRecipe(
                Items.TORCH, 2,
                Ingredient.of(Items.DIAMOND),
                "test:two_torches_from_diamond"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe, 5);

        assertFalse(report.unsupported());
        assertEquals(3, report.missingMaterials().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.missingMaterials().keySet().stream().allMatch(key -> key.item() == Items.DIAMOND));
    }

    @Test
    void diagnoseTopLevelMissing_shouldSatisfyMultiCandidateIngredientWhenAnyOptionIsCraftable() throws Exception {
        CraftingRecipe planksRecipe = mockRecipe(
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
        CraftingRecipe recipe = mockRecipe(
                Items.TORCH, 1,
                Ingredient.of(Items.DIAMOND, Items.OAK_PLANKS),
                "test:torch_from_diamond_or_planks"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe, 1);

        assertFalse(report.unsupported());
        assertTrue(report.missingMaterials().isEmpty());
    }

    @Test
    void diagnoseTopLevelMissing_shouldReturnUnsupportedForUnsupportedFirstLevelIngredient() {
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        unsupportedStick.getOrCreateTag().put("unsupported", EndTag.INSTANCE);
        Inventory inv = mockInventory();
        TransactionCalculator calculator = new TransactionCalculator(inv);
        CraftingRecipe recipe = mockRecipe(
                Items.TORCH, 1,
                Ingredient.of(unsupportedStick),
                "test:torch_from_unsupported_stick"
        );

        TransactionCalculator.TopLevelMissingReport report = calculator.diagnoseTopLevelMissing(recipe, 1);

        assertTrue(report.unsupported());
        assertTrue(report.missingMaterials().isEmpty());
    }

    @Test
    void diagnoseJeiDisplayedTopLevelMissing_shouldReturnUnsupportedForUnsupportedDisplayedIngredient() {
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        unsupportedStick.getOrCreateTag().put("unsupported", EndTag.INSTANCE);
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
        CraftingRecipe planksRecipe = mockRecipe(
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
        CraftingRecipe recipe = mockRecipe(
                Items.TORCH, 1,
                Ingredient.of(damagedPickaxeRequirement),
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

    private static CraftingRecipe mockRecipe(Item resultItem, int resultCount, Ingredient ingredient, String id) {
        return mockRecipeWithIngredients(resultItem, resultCount, id, ingredient);
    }

    private static ItemStack stackWithDefaultDamageTag(Item item) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Damage", 0);
        stack.setTag(tag);
        return stack;
    }

    private static ItemStack stackWithDamageValue(Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    private static CraftingRecipe mockRecipeWithIngredients(Item resultItem, int resultCount, String id, Ingredient... recipeIngredients) {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (Ingredient ingredient : recipeIngredients) {
            ingredients.add(ingredient);
        }

        when(recipe.getIngredients()).thenReturn(ingredients);
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(resultItem, resultCount));
        when(recipe.getId()).thenReturn(new ResourceLocation(id));

        return recipe;
    }

    private static void setPlanningResult(Map<Item, CraftingRecipe> pathMemo,
                                          Map<Item, Double> costMemo,
                                          Map<Item, List<CraftingRecipe>> recipeLookup) throws Exception {
        Constructor<CraftingPlanner.PlanningResult> constructor =
                CraftingPlanner.PlanningResult.class.getDeclaredConstructor(Map.class, Map.class, Map.class);
        constructor.setAccessible(true);
        CraftingPlanner.PlanningResult result = constructor.newInstance(pathMemo, costMemo, recipeLookup);

        Field resultField = CraftingPlanner.class.getDeclaredField("result");
        resultField.setAccessible(true);
        resultField.set(CraftingPlanner.getInstance(), result);
    }
}
