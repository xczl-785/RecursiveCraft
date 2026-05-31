package xczl.recursivecraft.core;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionCalculatorNbtTest {
    @BeforeAll static void init(){ MinecraftTestBootstrap.init(); }

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
}
