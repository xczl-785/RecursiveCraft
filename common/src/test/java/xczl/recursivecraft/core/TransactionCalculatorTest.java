package xczl.recursivecraft.core;

import net.minecraft.core.NonNullList;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void calculate_withForcedRecipe_shouldConsumeRequiredMaterialFromInventory() {
        Inventory inv = mockInventory(new ItemStack(Items.OAK_LOG, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);

        CraftingRecipe recipe = mockRecipe(
                Items.OAK_PLANKS, 4,
                Ingredient.of(Items.OAK_LOG),
                "test:planks_from_log"
        );

        CraftingTransaction tx = calculator.calculate(Items.OAK_PLANKS, 4, true, recipe);
        Map<Item, Integer> needs = tx.getNeeds();

        assertEquals(1, needs.getOrDefault(Items.OAK_LOG, 0));
        assertEquals(0, tx.getProvides().getOrDefault(Items.OAK_PLANKS, 0));
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
    void resolveIngredient_withMultipleOptions_shouldPreferInventoryAvailableOption() {
        Inventory inv = mockInventory(new ItemStack(Items.DIORITE, 1));
        TransactionCalculator calculator = new TransactionCalculator(inv);

        CraftingRecipe recipe = mockRecipe(
                Items.STICK, 1,
                Ingredient.of(Items.COBBLESTONE, Items.DIORITE),
                "test:stick_from_stone_options"
        );

        CraftingTransaction tx = calculator.calculate(Items.STICK, 1, true, recipe);
        assertEquals(1, tx.getNeeds().getOrDefault(Items.DIORITE, 0));
        assertEquals(0, tx.getNeeds().getOrDefault(Items.COBBLESTONE, 0));
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

    private static Inventory mockInventory(ItemStack... stacks) {
        Inventory inv = mock(Inventory.class);
        when(inv.getContainerSize()).thenReturn(stacks.length);
        for (int i = 0; i < stacks.length; i++) {
            when(inv.getItem(i)).thenReturn(stacks[i]);
        }
        return inv;
    }

    private static CraftingRecipe mockRecipe(Item resultItem, int resultCount, Ingredient ingredient, String id) {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ingredient);

        when(recipe.getIngredients()).thenReturn(ingredients);
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(resultItem, resultCount));
        when(recipe.getId()).thenReturn(new ResourceLocation(id));

        return recipe;
    }
}

