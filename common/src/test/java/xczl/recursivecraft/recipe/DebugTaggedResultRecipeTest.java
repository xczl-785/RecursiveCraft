package xczl.recursivecraft.recipe;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.registry.ModBlocks;
import xczl.recursivecraft.registry.ModItems;
import xczl.recursivecraft.registry.ModMenus;
import xczl.recursivecraft.registry.ModRecipeSerializers;
import xczl.recursivecraft.registry.ModTabs;
import xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.runtime.material.NormalizationKind;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class DebugTaggedResultRecipeTest {
    private static final DefaultMaterialIdentityNormalizer NORMALIZER = new DefaultMaterialIdentityNormalizer();

    @BeforeAll
    static void init() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void recipe_shouldReturnTaggedResultStack() throws Exception {
        CraftingRecipe recipe = createRecipe("red");

        ItemStack result = recipe.assemble(mock(CraftingContainer.class), RegistryAccess.EMPTY);

        assertEquals(Items.CRAFTING_TABLE, result.getItem());
        assertEquals("red", debugVariant(result));
        assertNotNull(result.getTag());
    }

    @Test
    void taggedOutputs_shouldProduceDistinctIdentities() throws Exception {
        MaterialKey red = NORMALIZER.normalize(createRecipe("red").getResultItem(RegistryAccess.EMPTY)).key();
        MaterialKey blue = NORMALIZER.normalize(createRecipe("blue").getResultItem(RegistryAccess.EMPTY)).key();

        assertNotEquals(red, blue);
    }

    @Test
    void recursiveCraft_shouldIncludeRecipeSerializerInContentRegistrationSlice() throws Exception {
        try (MockedStatic<ModBlocks> blocks = Mockito.mockStatic(ModBlocks.class);
             MockedStatic<ModItems> items = Mockito.mockStatic(ModItems.class);
             MockedStatic<ModMenus> menus = Mockito.mockStatic(ModMenus.class);
             MockedStatic<ModTabs> tabs = Mockito.mockStatic(ModTabs.class);
             MockedStatic<ModRecipeSerializers> serializers = Mockito.mockStatic(ModRecipeSerializers.class)) {
            invokeRegistrationHelper();

            blocks.verify(ModBlocks::register);
            items.verify(ModItems::register);
            menus.verify(ModMenus::register);
            tabs.verify(ModTabs::register);
            serializers.verify(ModRecipeSerializers::register);
        }
    }

    @Test
    void recipe_shouldResolveSerializerThroughRegisteredSupplierPath() throws Exception {
        RecipeSerializer<?> serializer = serializerFromRegisteredSupplierFactory();
        CraftingRecipe recipe = createRecipe("red");

        assertNotNull(serializer);
        assertInstanceOf(DebugTaggedResultRecipeSerializer.class, serializer);
        assertSame(serializer, recipe.getSerializer());
    }

    @Test
    void serializer_shouldPreserveOutputItemAndTypedTagPayloadThroughRegisteredPath() throws Exception {
        RecipeSerializer<DebugTaggedResultRecipe> serializer = serializerFromRegisteredSupplierFactory();
        JsonObject json = GsonHelper.parse("""
                {
                  "group": "debug",
                  "category": "misc",
                  "ingredients": [
                    { "item": "minecraft:stick" }
                  ],
                  "result": {
                    "item": "minecraft:crafting_table",
                    "count": 2,
                    "tag": {
                      "recursivecraft_debug": {
                        "variant": "blue",
                        "attempts": 2,
                        "ratio": 1.5,
                        "enabled": true,
                        "steps": ["alpha", "beta"],
                        "weights": [1, 3],
                        "nested": {
                          "state": "ready",
                          "priority": 7
                        }
                      }
                    }
                  }
                }
                """);

        DebugTaggedResultRecipe fromJson = serializer.fromJson(new ResourceLocation("recursivecraft", "debug_blue"), json);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        serializer.toNetwork(buf, fromJson);
        DebugTaggedResultRecipe roundTrip = serializer.fromNetwork(
                new ResourceLocation("recursivecraft", "debug_blue"),
                buf
        );

        ItemStack result = roundTrip.getResultItem(RegistryAccess.EMPTY);
        CompoundTag debug = result.getTag().getCompound("recursivecraft_debug");
        assertEquals(Items.CRAFTING_TABLE, result.getItem());
        assertEquals(2, result.getCount());
        assertNotNull(result.getTag());
        assertEquals(fromJson.getResultItem(RegistryAccess.EMPTY).getTag(), result.getTag());
        assertEquals("blue", debug.getString("variant"));
        assertEquals(2, debug.getInt("attempts"));
        assertEquals(1.5d, debug.getDouble("ratio"));
        assertEquals(true, debug.getBoolean("enabled"));
        assertEquals("alpha", debug.getList("steps", Tag.TAG_STRING).getString(0));
        assertEquals("beta", debug.getList("steps", Tag.TAG_STRING).getString(1));
        assertEquals(1, debug.getList("weights", Tag.TAG_INT).getInt(0));
        assertEquals(3, debug.getList("weights", Tag.TAG_INT).getInt(1));
        assertEquals("ready", debug.getCompound("nested").getString("state"));
        assertEquals(7, debug.getCompound("nested").getInt("priority"));
        assertEquals(NormalizationKind.NORMALIZED, NORMALIZER.normalize(result).kind());
    }

    private static CraftingRecipe createRecipe(String variant) throws Exception {
        Class<?> recipeClass = Class.forName("xczl.recursivecraft.recipe.DebugTaggedResultRecipe");
        return (CraftingRecipe) recipeClass.getConstructor(
                ResourceLocation.class,
                String.class,
                CraftingBookCategory.class,
                ItemStack.class,
                NonNullList.class
        ).newInstance(
                new ResourceLocation("recursivecraft", "debug_" + variant),
                "debug",
                CraftingBookCategory.MISC,
                taggedHandheldCrafter(variant),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK))
        );
    }

    private static ItemStack taggedHandheldCrafter(String variant) {
        ItemStack stack = new ItemStack(Items.CRAFTING_TABLE);
        CompoundTag debugTag = new CompoundTag();
        debugTag.putString("variant", variant);
        stack.getOrCreateTag().put("recursivecraft_debug", debugTag);
        return stack;
    }

    private static String debugVariant(ItemStack stack) {
        return stack.getTag().getCompound("recursivecraft_debug").getString("variant");
    }

    @SuppressWarnings("unchecked")
    private static RecipeSerializer<DebugTaggedResultRecipe> serializerFromRegisteredSupplierFactory() throws Exception {
        Field supplierField = ModRecipeSerializers.DEBUG_TAGGED_RESULT.getClass().getDeclaredField("supplier");
        supplierField.setAccessible(true);
        return ((Supplier<RecipeSerializer<DebugTaggedResultRecipe>>) supplierField.get(ModRecipeSerializers.DEBUG_TAGGED_RESULT)).get();
    }

    private static void invokeRegistrationHelper() throws Exception {
        Method helper = RecursiveCraft.class.getDeclaredMethod("registerContent");
        helper.setAccessible(true);
        helper.invoke(null);
    }
}
