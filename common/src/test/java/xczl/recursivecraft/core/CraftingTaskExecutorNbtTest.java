package xczl.recursivecraft.core;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.EndTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;
import xczl.recursivecraft.runtime.execution.ExecutionCommitResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CraftingTaskExecutorNbtTest {
    @BeforeAll
    static void init() {
        MinecraftTestBootstrap.init();
    }

    @BeforeEach
    void resetPlanner() throws Exception {
        setPlanningResult(Map.of(), Map.of(), Map.of());
    }

    @Test
    void tryExecute_shouldSurfaceMissingToUserWhenPlannerRecipeCannotBeSatisfied() throws Exception {
        CraftingRecipe recipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:missing_log");
        setPlanningResult(
                Map.of(Items.TORCH, recipe),
                Map.of(Items.TORCH, 1.0d, Items.OAK_LOG, 1.0d),
                Map.of(Items.TORCH, List.of(recipe))
        );

        Inventory inventory = mockInventory();
        ServerPlayer player = mockPlayer(inventory);
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.TORCH, 1, null, messages::add);

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.missing_materials") && msg.toString().contains(Items.OAK_LOG.getDescription().getString())));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_fail") && msg.toString().contains("MISSING")));
    }


    @Test
    void tryExecute_shouldReportOnlyUnsatisfiedTopLevelMaterialWhenSiblingCanBeCrafted() throws Exception {
        CraftingRecipe planksRecipe = mockRecipe(new ItemStack(Items.OAK_PLANKS, 4), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:planks_from_log");
        CraftingRecipe torchRecipe = mockRecipeWithIngredients(
                new ItemStack(Items.TORCH, 1),
                "test:torch_from_planks_and_diamond",
                ingredientOf(new ItemStack(Items.OAK_PLANKS)),
                ingredientOf(new ItemStack(Items.DIAMOND))
        );
        setPlanningResult(
                Map.of(Items.TORCH, torchRecipe, Items.OAK_PLANKS, planksRecipe),
                Map.of(Items.TORCH, 1.0d, Items.OAK_PLANKS, 1.0d, Items.OAK_LOG, 1.0d, Items.DIAMOND, 1.0d),
                Map.of(Items.TORCH, List.of(torchRecipe), Items.OAK_PLANKS, List.of(planksRecipe))
        );

        ServerPlayer player = mockPlayer(mockInventory(new ItemStack(Items.OAK_LOG, 1)));
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.TORCH, 1, null, messages::add);

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.missing_materials") && msg.toString().contains(Items.DIAMOND.getDescription().getString())));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.missing_materials") && msg.toString().contains(Items.OAK_PLANKS.getDescription().getString())));
    }

    @Test
    void tryExecute_shouldReportSharedResourceShortageAsUnsatisfiedTopLevelMaterial() throws Exception {
        CraftingRecipe planksRecipe = mockRecipe(new ItemStack(Items.OAK_PLANKS, 4), ingredientOf(new ItemStack(Items.OAK_LOG)), "test:planks_from_log");
        CraftingRecipe stickRecipe = mockRecipeWithIngredients(
                new ItemStack(Items.STICK, 4),
                "test:sticks_from_planks",
                ingredientOf(new ItemStack(Items.OAK_PLANKS)),
                ingredientOf(new ItemStack(Items.OAK_PLANKS))
        );
        CraftingRecipe axeRecipe = mockRecipeWithIngredients(
                new ItemStack(Items.WOODEN_AXE, 1),
                "test:axe_from_planks_and_sticks",
                ingredientOf(new ItemStack(Items.OAK_PLANKS)),
                ingredientOf(new ItemStack(Items.OAK_PLANKS)),
                ingredientOf(new ItemStack(Items.OAK_PLANKS)),
                ingredientOf(new ItemStack(Items.STICK)),
                ingredientOf(new ItemStack(Items.STICK))
        );
        setPlanningResult(
                Map.of(Items.WOODEN_AXE, axeRecipe, Items.OAK_PLANKS, planksRecipe, Items.STICK, stickRecipe),
                Map.of(Items.WOODEN_AXE, 4.0d, Items.OAK_PLANKS, 1.0d, Items.STICK, 2.0d, Items.OAK_LOG, 1.0d),
                Map.of(Items.WOODEN_AXE, List.of(axeRecipe), Items.OAK_PLANKS, List.of(planksRecipe), Items.STICK, List.of(stickRecipe))
        );

        ServerPlayer player = mockPlayer(mockInventory(new ItemStack(Items.OAK_LOG, 1)));
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.WOODEN_AXE, 1, null, messages::add);

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.missing_materials") && msg.toString().contains("2x") && msg.toString().contains(Items.STICK.getDescription().getString())));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.missing_materials") && msg.toString().contains(Items.OAK_PLANKS.getDescription().getString())));
    }

    @Test
    void tryExecute_shouldSurfaceUnsupportedToUserWhenMaterialIdentityIsUnsupported() throws Exception {
        ItemStack unsupportedStick = new ItemStack(Items.STICK);
        unsupportedStick.getOrCreateTag().put("unsupported", EndTag.INSTANCE);
        CraftingRecipe recipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(unsupportedStick), "test:unsupported_stick");
        setPlanningResult(
                Map.of(Items.TORCH, recipe),
                Map.of(Items.TORCH, 1.0d, Items.STICK, 1.0d),
                Map.of(Items.TORCH, List.of(recipe))
        );

        Inventory inventory = mockInventory();
        ServerPlayer player = mockPlayer(inventory);
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.TORCH, 1, null, messages::add);

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_fail") && msg.toString().contains("UNSUPPORTED")));
    }

    @Test
    void visibleFailureCode_shouldCollapseInternalCommitFailuresToMissing() {
        assertEquals("MISSING", CraftingTaskExecutor.visibleFailureCode(ExecutionCommitResult.Status.FAILED_REVALIDATION));
        assertEquals("MISSING", CraftingTaskExecutor.visibleFailureCode(ExecutionCommitResult.Status.FAILED_CONSUME));
    }

    @Test
    void visibleFailureCode_shouldKeepNonFailureStatusAsIs() {
        assertEquals("SUCCESS", CraftingTaskExecutor.visibleFailureCode(ExecutionCommitResult.Status.SUCCESS));
    }

    @Test
    void tryExecute_shouldSurfaceMissingToUserWhenRevalidateFailsAfterPlanning() throws Exception {
        ItemStack redStick = new ItemStack(Items.STICK, 1);
        redStick.getOrCreateTag().putString("variant", "red");
        CraftingRecipe recipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(redStick), "test:red_stick_revalidate");
        setPlanningResult(
                Map.of(Items.TORCH, recipe),
                Map.of(Items.TORCH, 1.0d, Items.STICK, 1.0d),
                Map.of(Items.TORCH, List.of(recipe))
        );

        Inventory inventory = mock(Inventory.class);
        when(inventory.getContainerSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(
                redStick.copy(),
                redStick.copy(),
                redStick.copy(),
                ItemStack.EMPTY
        );
        ServerPlayer player = mockPlayer(inventory);
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.TORCH, 1, null, messages::add);

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_fail") && msg.toString().contains("MISSING")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("FAILED_REVALIDATION")));
    }

    @Test
    void tryExecute_shouldSurfaceMissingToUserWhenConsumeFailsAfterRevalidate() throws Exception {
        ItemStack redStick = new ItemStack(Items.STICK, 1);
        redStick.getOrCreateTag().putString("variant", "red");
        CraftingRecipe recipe = mockRecipe(new ItemStack(Items.TORCH, 1), ingredientOf(redStick), "test:red_stick_consume");
        setPlanningResult(
                Map.of(Items.TORCH, recipe),
                Map.of(Items.TORCH, 1.0d, Items.STICK, 1.0d),
                Map.of(Items.TORCH, List.of(recipe))
        );

        Inventory inventory = mock(Inventory.class);
        when(inventory.getContainerSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(
                redStick.copy(),
                redStick.copy(),
                redStick.copy(),
                redStick.copy(),
                redStick.copy(),
                ItemStack.EMPTY
        );
        ServerPlayer player = mockPlayer(inventory);
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.TORCH, 1, null, messages::add);

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_fail") && msg.toString().contains("MISSING")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("FAILED_CONSUME")));
    }

    private static ServerPlayer mockPlayer(Inventory inventory) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getInventory()).thenReturn(inventory);
        return player;
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
        return mockRecipeWithIngredients(result, id, ingredient);
    }

    private static CraftingRecipe mockRecipeWithIngredients(ItemStack result, String id, Ingredient... recipeIngredients) {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (Ingredient ingredient : recipeIngredients) {
            ingredients.add(ingredient);
        }
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
