package xczl.recursivecraft.compat.jei;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.runtime.material.ItemStackComponentSupport;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        assertEquals(redRecipeHolder.id(), redPacket.forcedRecipeId());
        assertEquals(blueRecipeHolder.id(), bluePacket.forcedRecipeId());
        assertNotNull(redPacket.targetOutputSpec());
        assertNotNull(bluePacket.targetOutputSpec());
        assertEquals(Items.CRAFTING_TABLE, redPacket.targetItem());
        assertEquals(Items.CRAFTING_TABLE, bluePacket.targetItem());
        assertEquals(ItemStackComponentSupport.copyCustomData(redOutput), redPacket.targetOutputSpec().tag());
        assertEquals(ItemStackComponentSupport.copyCustomData(blueOutput), bluePacket.targetOutputSpec().tag());
        assertEquals("red", redPacket.targetOutputSpec().tag().getCompoundOrEmpty("recursivecraft_debug").getString("variant").orElse(""));
        assertEquals("blue", bluePacket.targetOutputSpec().tag().getCompoundOrEmpty("recursivecraft_debug").getString("variant").orElse(""));
        assertNotEquals(redPacket.targetOutputSpec().tag(), bluePacket.targetOutputSpec().tag());
        assertEquals("red-source", ItemStackComponentSupport.copyCustomData(redPacket.displayedIngredients().get(0)).getString("variant"));
        assertEquals("blue-source", ItemStackComponentSupport.copyCustomData(bluePacket.displayedIngredients().get(0)).getString("variant"));
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
}
