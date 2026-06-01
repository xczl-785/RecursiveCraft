package xczl.recursivecraft.compat.jei;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

    @Test
    void createRecursivePacket_shouldUseDisplayedOutputIdentityAndRecipeId() {
        CraftingRecipe recipe = mock(CraftingRecipe.class);
        ResourceLocation recipeId = new ResourceLocation("test", "long_swiftness");
        when(recipe.getId()).thenReturn(recipeId);

        ItemStack displayedOutput = taggedStack(Items.POTION, "Potion", "minecraft:long_swiftness");

        C2SExecuteCraftPacket packet = RecursiveCraftTransferPackets.createRecursivePacket(recipe, displayedOutput, false);

        assertEquals(Items.POTION, packet.targetItem());
        assertEquals(1, packet.amount());
        assertEquals(recipeId, packet.forcedRecipeId());
        assertNotNull(packet.targetOutputSpec());
        assertEquals(Items.POTION, packet.targetOutputSpec().item());
        assertEquals(displayedOutput.getTag(), packet.targetOutputSpec().tag());
    }

    @Test
    void createRecursivePacket_shouldPreserveDistinctDebugFixtureTargetOutputSpecsForSameItem() throws IOException {
        CraftingRecipe redRecipe = mock(CraftingRecipe.class);
        when(redRecipe.getId()).thenReturn(new ResourceLocation("recursivecraft", "debug/handheld_crafter_red"));
        CraftingRecipe blueRecipe = mock(CraftingRecipe.class);
        when(blueRecipe.getId()).thenReturn(new ResourceLocation("recursivecraft", "debug/handheld_crafter_blue"));

        ItemStack redOutput = debugFixtureOutput("debug/handheld_crafter_red", Items.CRAFTING_TABLE);
        ItemStack blueOutput = debugFixtureOutput("debug/handheld_crafter_blue", Items.CRAFTING_TABLE);

        C2SExecuteCraftPacket redPacket = RecursiveCraftTransferPackets.createRecursivePacket(redRecipe, redOutput, false);
        C2SExecuteCraftPacket bluePacket = RecursiveCraftTransferPackets.createRecursivePacket(blueRecipe, blueOutput, true);

        assertEquals(1, redPacket.amount());
        assertEquals(64, bluePacket.amount());
        assertEquals(redRecipe.getId(), redPacket.forcedRecipeId());
        assertEquals(blueRecipe.getId(), bluePacket.forcedRecipeId());
        assertNotNull(redPacket.targetOutputSpec());
        assertNotNull(bluePacket.targetOutputSpec());
        assertEquals(Items.CRAFTING_TABLE, redPacket.targetItem());
        assertEquals(Items.CRAFTING_TABLE, bluePacket.targetItem());
        assertEquals(redOutput.getTag(), redPacket.targetOutputSpec().tag());
        assertEquals(blueOutput.getTag(), bluePacket.targetOutputSpec().tag());
        assertEquals("red", redPacket.targetOutputSpec().tag().getCompound("recursivecraft_debug").getString("variant"));
        assertEquals("blue", bluePacket.targetOutputSpec().tag().getCompound("recursivecraft_debug").getString("variant"));
        assertNotEquals(redPacket.targetOutputSpec().tag(), bluePacket.targetOutputSpec().tag());
    }

    private static ItemStack taggedStack(Item item, String key, String value) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(key, value);
        stack.setTag(tag);
        return stack;
    }

    private static ItemStack debugFixtureOutput(String recipePath, Item item) throws IOException {
        ItemStack stack = new ItemStack(item);
        stack.setTag(readFixtureTag(recipePath));
        return stack;
    }

    private static CompoundTag readFixtureTag(String recipePath) throws IOException {
        JsonObject recipeJson = readRecipeJson(recipePath);
        CompoundTag tag = new CompoundTag();
        CompoundTag debugTag = new CompoundTag();
        debugTag.putString("variant", GsonHelper.getAsJsonObject(
                GsonHelper.getAsJsonObject(
                        GsonHelper.getAsJsonObject(recipeJson, "result"),
                        "tag"
                ),
                "recursivecraft_debug"
        ).get("variant").getAsString());
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
