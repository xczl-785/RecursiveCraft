package xczl.recursivecraft.core;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
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
import xczl.recursivecraft.runtime.material.TargetOutputSpec;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CraftingTaskExecutorTargetOutputTest {
    @BeforeAll
    static void init() {
        MinecraftTestBootstrap.init();
    }

    @BeforeEach
    void resetPlanner() throws Exception {
        setPlanningResult(Map.of(), Map.of(), Map.of());
    }

    @Test
    void splitNetChanges_shouldIgnoreUnrelatedUnsupportedOutputsWhenTargetOutputSpecIsPresent() throws Exception {
        ItemStack desiredOutput = stackWithVariant(Items.STICK, "red-output");
        MaterialKey desiredKey = new DefaultMaterialIdentityNormalizer().normalize(desiredOutput).key();
        ItemStack unsupportedSideOutput = new ItemStack(Items.TORCH);
        setCustomData(unsupportedSideOutput, tag -> tag.put("unsupported", EndTag.INSTANCE));

        CraftingTransaction transaction = new CraftingTransaction();
        transaction.addResolvedOutput(desiredOutput);
        transaction.addResolvedOutput(unsupportedSideOutput);

        Object netChanges = splitNetChanges(transaction, desiredKey);
        @SuppressWarnings("unchecked")
        Map<MaterialKey, Integer> normalizedProvides = (Map<MaterialKey, Integer>) readField(netChanges, "normalizedProvides");
        boolean enoughTargetProvide = hasEnoughTargetProvide(Items.STICK, 1, desiredKey, netChanges);

        assertFalse((boolean) readField(netChanges, "hasUnsupportedProvides"));
        assertEquals(1, normalizedProvides.getOrDefault(desiredKey, 0));
        assertTrue(enoughTargetProvide);
    }

    @Test
    void tryExecute_shouldKeepLegacySuccessCheckWhenTargetOutputSpecIsNull() throws Exception {
        RecipeHolder<CraftingRecipe> blueStickRecipe = mockRecipe(
                stackWithVariant(Items.STICK, "blue-output"),
                ingredientOf(new ItemStack(Items.OAK_LOG)),
                "test:blue_stick"
        );
        setPlanningResult(
                Map.of(Items.STICK, blueStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.OAK_LOG, 1.0d),
                Map.of(Items.STICK, List.of(blueStickRecipe))
        );

        ServerPlayer player = mockPlayer(mockInventory(new ItemStack(Items.OAK_LOG)));
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.STICK, 1, null, null, messages::add);

        assertTrue(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_success")));
    }

    @Test
    void tryExecute_shouldReportMissingWhenResolvedOutputsDoNotMatchTargetOutputSpecIdentity() throws Exception {
        RecipeHolder<CraftingRecipe> blueStickRecipe = mockRecipe(
                stackWithVariant(Items.STICK, "blue-output"),
                ingredientOf(new ItemStack(Items.OAK_LOG)),
                "test:blue_stick"
        );
        setPlanningResult(
                Map.of(Items.STICK, blueStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.OAK_LOG, 1.0d),
                Map.of(Items.STICK, List.of(blueStickRecipe))
        );

        ServerPlayer player = mockPlayer(mockInventory(new ItemStack(Items.OAK_LOG)));
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(
                player,
                Items.STICK,
                1,
                null,
                TargetOutputSpec.fromStack(stackWithVariant(Items.STICK, "red-output")),
                messages::add
        );

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_fail") && msg.toString().contains("recursivecraft.msg.failure.missing")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_success")));
    }

    @Test
    void tryExecute_shouldSelectRedDebugFixtureIdentityInsteadOfBlueSiblingRecipe() throws Exception {
        Item fixtureItem = Items.CRAFTING_TABLE;
        ItemStack redFixtureOutput = debugFixtureOutput("debug/handheld_crafter_red", fixtureItem);
        ItemStack blueFixtureOutput = debugFixtureOutput("debug/handheld_crafter_blue", fixtureItem);
        ItemStack redSource = stackWithVariant(Items.WHITE_WOOL, "red-source");
        ItemStack blueSource = stackWithVariant(Items.WHITE_WOOL, "blue-source");

        RecipeHolder<CraftingRecipe> preferredBlueRecipe = mockRecipe(
                blueFixtureOutput.copy(),
                ingredientOf(blueSource),
                "recursivecraft:debug/handheld_crafter_blue"
        );
        RecipeHolder<CraftingRecipe> alternateRedRecipe = mockRecipe(
                redFixtureOutput.copy(),
                ingredientOf(redSource),
                "recursivecraft:debug/handheld_crafter_red"
        );
        setPlanningResult(
                Map.of(fixtureItem, preferredBlueRecipe),
                Map.of(fixtureItem, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(fixtureItem, List.of(preferredBlueRecipe, alternateRedRecipe))
        );

        ServerPlayer player = mockPlayer(mockInventory(redSource, blueSource));
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(
                player,
                fixtureItem,
                1,
                null,
                TargetOutputSpec.fromStack(redFixtureOutput),
                messages::add
        );

        assertTrue(ok);
        assertEquals(0, redSource.getCount());
        assertEquals(1, blueSource.getCount());
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_success")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_fail")));
    }

    @Test
    void tryExecute_shouldSelectBlueDebugFixtureIdentityInsteadOfRedSiblingRecipe() throws Exception {
        Item fixtureItem = Items.CRAFTING_TABLE;
        ItemStack redFixtureOutput = debugFixtureOutput("debug/handheld_crafter_red", fixtureItem);
        ItemStack blueFixtureOutput = debugFixtureOutput("debug/handheld_crafter_blue", fixtureItem);
        ItemStack redSource = stackWithVariant(Items.WHITE_WOOL, "red-source");
        ItemStack blueSource = stackWithVariant(Items.WHITE_WOOL, "blue-source");

        RecipeHolder<CraftingRecipe> preferredRedRecipe = mockRecipe(
                redFixtureOutput.copy(),
                ingredientOf(redSource),
                "recursivecraft:debug/handheld_crafter_red"
        );
        RecipeHolder<CraftingRecipe> alternateBlueRecipe = mockRecipe(
                blueFixtureOutput.copy(),
                ingredientOf(blueSource),
                "recursivecraft:debug/handheld_crafter_blue"
        );
        setPlanningResult(
                Map.of(fixtureItem, preferredRedRecipe),
                Map.of(fixtureItem, 1.0d, Items.WHITE_WOOL, 1.0d),
                Map.of(fixtureItem, List.of(preferredRedRecipe, alternateBlueRecipe))
        );

        ServerPlayer player = mockPlayer(mockInventory(redSource, blueSource));
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(
                player,
                fixtureItem,
                1,
                null,
                TargetOutputSpec.fromStack(blueFixtureOutput),
                messages::add
        );

        assertTrue(ok);
        assertEquals(1, redSource.getCount());
        assertEquals(0, blueSource.getCount());
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_success")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_fail")));
    }

    @Test
    void tryExecute_shouldReportUnsupportedWhenTargetOutputSpecCannotBeNormalized() throws Exception {
        RecipeHolder<CraftingRecipe> plainStickRecipe = mockRecipe(
                new ItemStack(Items.STICK),
                ingredientOf(new ItemStack(Items.OAK_LOG)),
                "test:plain_stick"
        );
        setPlanningResult(
                Map.of(Items.STICK, plainStickRecipe),
                Map.of(Items.STICK, 1.0d, Items.OAK_LOG, 1.0d),
                Map.of(Items.STICK, List.of(plainStickRecipe))
        );

        ItemStack unsupportedTarget = new ItemStack(Items.STICK);
        setCustomData(unsupportedTarget, tag -> tag.put("unsupported", EndTag.INSTANCE));

        ServerPlayer player = mockPlayer(mockInventory(new ItemStack(Items.OAK_LOG)));
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(
                player,
                Items.STICK,
                1,
                null,
                TargetOutputSpec.fromStack(unsupportedTarget),
                messages::add
        );

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_fail") && msg.toString().contains("recursivecraft.msg.failure.unsupported")));
    }

    @Test
    void tryExecute_shouldRejectConflictingTargetItemAndTargetOutputSpecItem() {
        ServerPlayer player = mockPlayer(mockInventory());
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(
                player,
                Items.TORCH,
                1,
                null,
                new TargetOutputSpec(Items.STICK, (CompoundTag) null),
                messages::add
        );

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.invalid_request")));
    }

    @Test
    void tryExecute_shouldNotSilentlyFallbackWhenForcedRecipeIsSpecial() throws Exception {
        RecipeHolder<CraftingRecipe> fallbackRecipe = mockRecipe(
                new ItemStack(Items.STICK),
                ingredientOf(new ItemStack(Items.OAK_LOG)),
                "test:fallback_stick"
        );
        RecipeHolder<CraftingRecipe> specialRecipe = mockRecipe(
                new ItemStack(Items.STICK),
                ingredientOf(new ItemStack(Items.BIRCH_LOG)),
                "test:special_stick"
        );
        when(specialRecipe.value().isSpecial()).thenReturn(true);

        setPlanningResult(
                Map.of(Items.STICK, fallbackRecipe),
                Map.of(Items.STICK, 1.0d, Items.OAK_LOG, 1.0d),
                Map.of(Items.STICK, List.of(fallbackRecipe))
        );

        RecipeManager recipeManager = mock(RecipeManager.class);
        ResourceKey<Recipe<?>> specialStickKey = ResourceKey.create(Registries.RECIPE, ResourceLocation.parse("test:special_stick"));
        org.mockito.Mockito.doReturn(java.util.Optional.of(specialRecipe))
                .when(recipeManager).byKey(specialStickKey);
        ServerLevel level = mock(ServerLevel.class);
        when(level.recipeAccess()).thenReturn(recipeManager);

        ServerPlayer player = mockPlayer(mockInventory(new ItemStack(Items.OAK_LOG)));
        when(player.level()).thenReturn(level);
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(
                player,
                Items.STICK,
                1,
                ResourceLocation.parse("test:special_stick"),
                null,
                messages::add
        );

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.invalid_recipe")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_success")));
    }

    @Test
    void tryExecute_shouldUseDisplayedIngredientsForSpecialJeiRecipeWhenTargetOutputSpecIsPresent() {
        ItemStack redDisplayedOutput = stackWithVariant(Items.STICK, "red-output");
        RecipeHolder<CraftingRecipe> specialRecipe = mockRecipe(
                redDisplayedOutput.copy(),
                ingredientOf(new ItemStack(Items.BIRCH_LOG)),
                "test:special_red_stick"
        );
        when(specialRecipe.value().isSpecial()).thenReturn(true);

        RecipeManager recipeManager = mock(RecipeManager.class);
        ResourceKey<Recipe<?>> specialRedKey = ResourceKey.create(Registries.RECIPE, ResourceLocation.parse("test:special_red_stick"));
        org.mockito.Mockito.doReturn(java.util.Optional.of(specialRecipe))
                .when(recipeManager).byKey(specialRedKey);
        ServerLevel level = mock(ServerLevel.class);
        when(level.recipeAccess()).thenReturn(recipeManager);

        ItemStack oakLog = new ItemStack(Items.OAK_LOG);
        ServerPlayer player = mockPlayer(mockInventory(oakLog));
        when(player.level()).thenReturn(level);
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(
                player,
                Items.STICK,
                1,
                ResourceLocation.parse("test:special_red_stick"),
                TargetOutputSpec.fromStack(redDisplayedOutput),
                List.of(new ItemStack(Items.OAK_LOG)),
                messages::add
        );

        assertTrue(ok);
        assertEquals(0, oakLog.getCount());
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.craft_success")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.invalid_recipe")));
    }


    @Test
    void tryExecute_shouldReportDisplayedMissingMaterialsForSpecialJeiRecipe() throws Exception {
        ItemStack redDisplayedOutput = stackWithVariant(Items.STICK, "red-output");
        RecipeHolder<CraftingRecipe> specialRecipe = mockRecipe(
                redDisplayedOutput.copy(),
                ingredientOf(new ItemStack(Items.BIRCH_LOG)),
                "test:special_red_stick_missing"
        );
        when(specialRecipe.value().isSpecial()).thenReturn(true);

        RecipeManager recipeManager = mock(RecipeManager.class);
        ResourceKey<Recipe<?>> specialRedMissingKey = ResourceKey.create(Registries.RECIPE, ResourceLocation.parse("test:special_red_stick_missing"));
        org.mockito.Mockito.doReturn(java.util.Optional.of(specialRecipe))
                .when(recipeManager).byKey(specialRedMissingKey);
        ServerLevel level = mock(ServerLevel.class);
        when(level.recipeAccess()).thenReturn(recipeManager);

        RecipeHolder<CraftingRecipe> planksRecipe = mockRecipe(
                new ItemStack(Items.OAK_PLANKS, 4),
                ingredientOf(new ItemStack(Items.OAK_LOG)),
                "test:planks_from_log"
        );
        setPlanningResult(
                Map.of(Items.OAK_PLANKS, planksRecipe),
                Map.of(Items.OAK_PLANKS, 1.0d, Items.OAK_LOG, 1.0d, Items.DIAMOND, 1.0d),
                Map.of(Items.OAK_PLANKS, List.of(planksRecipe))
        );

        ServerPlayer player = mockPlayer(mockInventory(new ItemStack(Items.OAK_LOG)));
        when(player.level()).thenReturn(level);
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(
                player,
                Items.STICK,
                1,
                ResourceLocation.parse("test:special_red_stick_missing"),
                TargetOutputSpec.fromStack(redDisplayedOutput),
                List.of(new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.DIAMOND)),
                messages::add
        );

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.missing_materials") && msg.toString().contains(Items.DIAMOND.getName(new ItemStack(Items.DIAMOND)).getString())));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("recursivecraft.msg.missing_materials") && msg.toString().contains(Items.OAK_PLANKS.getName(new ItemStack(Items.OAK_PLANKS)).getString())));
    }

    @Test
    void tryExecute_shouldDescribeNbtMissingMaterialWithoutRawMaterialKey() throws Exception {
        ItemStack variantStick = stackWithVariant(Items.STICK, "red-input");
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                new ItemStack(Items.TORCH),
                ingredientOf(variantStick),
                "test:torch_from_red_stick"
        );
        setPlanningResult(
                Map.of(Items.TORCH, recipe),
                Map.of(Items.TORCH, 1.0d, Items.STICK, 1.0d),
                Map.of(Items.TORCH, List.of(recipe))
        );

        ServerPlayer player = mockPlayer(mockInventory());
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.TORCH, 1, null, null, messages::add);

        assertFalse(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("recursivecraft.msg.missing_materials") && msg.toString().contains("variant=red-input")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("MaterialKey{")));
    }

    @Test
    void tryExecute_shouldPrintFriendlyMaterialNamesInsteadOfRawMaterialKey() throws Exception {
        ItemStack namedOutput = new ItemStack(Items.STICK);
        namedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("Crimson Debug Stick"));
        RecipeHolder<CraftingRecipe> recipe = mockRecipe(
                namedOutput,
                ingredientOf(new ItemStack(Items.RED_DYE)),
                "test:red_dye_stick"
        );
        setPlanningResult(
                Map.of(Items.STICK, recipe),
                Map.of(Items.STICK, 1.0d, Items.RED_DYE, 1.0d),
                Map.of(Items.STICK, List.of(recipe))
        );

        ServerPlayer player = mockPlayer(mockInventory(new ItemStack(Items.RED_DYE)));
        List<Component> messages = new ArrayList<>();

        boolean ok = CraftingTaskExecutor.tryExecute(player, Items.STICK, 1, null, null, messages::add);

        assertTrue(ok);
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains(Items.RED_DYE.getName(new ItemStack(Items.RED_DYE)).getString())));
        assertTrue(messages.stream().anyMatch(msg -> msg.toString().contains("Crimson Debug Stick")));
        assertTrue(messages.stream().noneMatch(msg -> msg.toString().contains("MaterialKey{")));
    }

    @Test
    void describeMaterialKeyForPlayer_shouldSummarizeRelevantNbtFields() {
        MaterialKey key = new DefaultMaterialIdentityNormalizer().normalize(stackWithVariant(Items.STICK, "red-output")).key();

        String display = CraftingTaskExecutor.describeMaterialKeyForPlayer(key);

        assertTrue(display.contains(Items.STICK.getName(new ItemStack(Items.STICK)).getString()));
        assertTrue(display.contains("variant=red-output"));
        assertFalse(display.contains("MaterialKey{"));
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
        when(inv.add(any(ItemStack.class))).thenReturn(true);
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
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, ResourceLocation.parse(id)), recipe);
    }

    private static Ingredient ingredientOf(ItemStack... options) {
        Ingredient ingredient = mock(Ingredient.class);
        ItemStack[] copies = new ItemStack[options.length];
        for (int i = 0; i < options.length; i++) {
            copies[i] = options[i].copy();
        }
        List<Holder<net.minecraft.world.item.Item>> holders = new ArrayList<>();
        for (ItemStack copy : copies) {
            holders.add(Holder.direct(copy.getItem()));
        }
        when(ingredient.items()).thenAnswer(inv -> holders.stream());
        when(ingredient.isEmpty()).thenReturn(options.length == 0);
        // Stub display() to return ItemStackSlotDisplay with full component data
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
        setCustomData(stack, tag -> tag.putString("variant", variant));
        return stack;
    }

    private static ItemStack debugFixtureOutput(String recipePath, Item item) throws IOException {
        ItemStack stack = new ItemStack(item);
        CompoundTag debugTag = readDebugFixtureTag(recipePath);
        setCustomData(stack, tag -> tag.put("recursivecraft_debug", debugTag));
        return stack;
    }

    private static net.minecraft.nbt.CompoundTag readDebugFixtureTag(String recipePath) throws IOException {
        readRecipeJson(recipePath);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("variant", recipePath.endsWith("_blue") ? "blue" : "red");
        return tag;
    }

    private static JsonObject readRecipeJson(String recipePath) throws IOException {
        String resourcePath = "data/recursivecraft/recipes/" + recipePath + ".json";
        try (InputStream input = CraftingTaskExecutorTargetOutputTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(input, "Missing recipe resource: " + resourcePath);
            return GsonHelper.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Object splitNetChanges(CraftingTransaction transaction, MaterialKey desiredKey) throws Exception {
        Method method = CraftingTaskExecutor.class.getDeclaredMethod("splitNetChanges", CraftingTransaction.class, MaterialKey.class);
        method.setAccessible(true);
        return method.invoke(null, transaction, desiredKey);
    }

    private static boolean hasEnoughTargetProvide(Item item, int amount, MaterialKey desiredKey, Object netChanges) throws Exception {
        Method method = CraftingTaskExecutor.class.getDeclaredMethod(
                "hasEnoughTargetProvide",
                Item.class,
                int.class,
                MaterialKey.class,
                netChanges.getClass()
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, item, amount, desiredKey, netChanges);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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

    private static void setCustomData(ItemStack stack, java.util.function.Consumer<CompoundTag> mutator) {
        CompoundTag tag = ItemStackComponentSupport.copyCustomData(stack);
        if (tag == null) {
            tag = new CompoundTag();
        }
        mutator.accept(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
