package xczl.recursivecraft.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

public class DebugTaggedResultRecipeSerializer implements RecipeSerializer<DebugTaggedResultRecipe> {
    private static final int MAX_INGREDIENTS = 9;

    @Override
    public DebugTaggedResultRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
        String group = GsonHelper.getAsString(json, "group", "");
        CraftingBookCategory category = CraftingBookCategory.CODEC.byName(
                GsonHelper.getAsString(json, "category", null),
                CraftingBookCategory.MISC
        );
        NonNullList<Ingredient> ingredients = readIngredients(GsonHelper.getAsJsonArray(json, "ingredients"));
        ItemStack result = readTaggedResult(GsonHelper.getAsJsonObject(json, "result"));
        return new DebugTaggedResultRecipe(recipeId, group, category, result, ingredients);
    }

    @Override
    public DebugTaggedResultRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buf) {
        String group = buf.readUtf();
        CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
        int ingredientCount = buf.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(ingredientCount, Ingredient.EMPTY);
        for (int i = 0; i < ingredientCount; i++) {
            ingredients.set(i, Ingredient.fromNetwork(buf));
        }
        return new DebugTaggedResultRecipe(recipeId, group, category, buf.readItem(), ingredients);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, DebugTaggedResultRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeEnum(recipe.category());
        buf.writeVarInt(recipe.getIngredients().size());
        for (Ingredient ingredient : recipe.getIngredients()) {
            ingredient.toNetwork(buf);
        }
        buf.writeItem(recipe.getResultItem(RegistryAccess.EMPTY));
    }

    private static NonNullList<Ingredient> readIngredients(JsonArray ingredientArray) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (JsonElement ingredientElement : ingredientArray) {
            Ingredient ingredient = Ingredient.fromJson(ingredientElement, false);
            if (!ingredient.isEmpty()) {
                ingredients.add(ingredient);
            }
        }
        if (ingredients.isEmpty()) {
            throw new JsonParseException("No ingredients for debug tagged result recipe");
        }
        if (ingredients.size() > MAX_INGREDIENTS) {
            throw new JsonParseException("Too many ingredients for debug tagged result recipe");
        }
        return ingredients;
    }

    private static ItemStack readTaggedResult(JsonObject resultJson) {
        Item item = ShapedRecipe.itemFromJson(resultJson);
        int count = GsonHelper.getAsInt(resultJson, "count", 1);
        ItemStack stack = new ItemStack(item, count);
        if (resultJson.has("tag")) {
            JsonElement tagElement = resultJson.get("tag");
            if (!tagElement.isJsonObject()) {
                throw new JsonParseException("Expected result.tag to be an object");
            }
            stack.setTag(readCompoundTag(tagElement.getAsJsonObject()));
        }
        return stack;
    }

    private static CompoundTag readCompoundTag(JsonObject json) {
        CompoundTag tag = new CompoundTag();
        for (String key : json.keySet()) {
            tag.put(key, readTag(json.get(key)));
        }
        return tag;
    }

    private static Tag readTag(JsonElement element) {
        if (element.isJsonObject()) {
            return readCompoundTag(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            return readListTag(element.getAsJsonArray());
        }
        if (element.isJsonPrimitive()) {
            return readPrimitiveTag(element.getAsJsonPrimitive());
        }
        throw new JsonParseException("Unsupported result.tag value: " + element);
    }

    private static Tag readPrimitiveTag(JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return ByteTag.valueOf(primitive.getAsBoolean());
        }
        if (primitive.isString()) {
            return StringTag.valueOf(primitive.getAsString());
        }
        if (primitive.isNumber()) {
            String raw = primitive.getAsString();
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return DoubleTag.valueOf(primitive.getAsDouble());
            }
            long value = primitive.getAsLong();
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return IntTag.valueOf((int) value);
            }
            return LongTag.valueOf(value);
        }
        throw new JsonParseException("Unsupported primitive value in result.tag: " + primitive);
    }

    private static ListTag readListTag(JsonArray json) {
        ListTag tag = new ListTag();
        byte elementType = 0;
        for (JsonElement element : json) {
            Tag child = readTag(element);
            if (elementType == 0) {
                elementType = child.getId();
            } else if (child.getId() != elementType) {
                throw new JsonParseException("Mixed-type arrays are not supported in result.tag");
            }
            if (!tag.addTag(tag.size(), child)) {
                throw new JsonParseException("Unsupported list element in result.tag");
            }
        }
        return tag;
    }
}
