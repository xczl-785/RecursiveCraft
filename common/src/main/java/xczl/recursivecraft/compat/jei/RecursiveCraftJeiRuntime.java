package xczl.recursivecraft.compat.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.client.CraftableTarget;
import xczl.recursivecraft.runtime.material.ItemStackComponentSupport;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecursiveCraftJeiRuntime {
    private static @Nullable IJeiRuntime runtime;
    private static int generation = 0;

    private RecursiveCraftJeiRuntime() {
    }

    public static void setRuntime(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        generation++;
    }

    public static void clearRuntime() {
        runtime = null;
        generation++;
    }

    public static int generation() {
        return generation;
    }

    public static boolean isAvailable() {
        return runtime != null;
    }

    public static List<CraftableTarget> collectCraftableTargets(Set<Item> plannerItems) {
        IJeiRuntime jeiRuntime = runtime;
        if (jeiRuntime == null || plannerItems.isEmpty()) {
            return List.of();
        }

        Map<String, CraftableTarget> deduped = new LinkedHashMap<>();
        jeiRuntime.getRecipeManager()
                .createRecipeLookup(RecipeTypes.CRAFTING)
                .get()
                .forEach(recipeHolder -> collectTargetsFromRecipe(recipeHolder, plannerItems, deduped));
        return List.copyOf(deduped.values());
    }

    private static void collectTargetsFromRecipe(RecipeHolder<CraftingRecipe> recipeHolder,
                                                 Set<Item> plannerItems,
                                                 Map<String, CraftableTarget> deduped) {
        CraftingRecipe recipe = recipeHolder.value();
        ResourceLocation recipeId = recipeHolder.id();

        if (collectGroupedVariants(recipe, recipeId, plannerItems, deduped)) {
            return;
        }

        ItemStack result = safeRecipeResult(recipe);
        if (result.isEmpty() || !plannerItems.contains(result.getItem())) {
            return;
        }

        deduped.putIfAbsent(
                targetKey(result),
                new CraftableTarget(result, recipeId, toTargetOutputSpec(result), null)
        );
    }

    private static boolean collectGroupedVariants(Object recipeObject, ResourceLocation recipeId,
                                                  Set<Item> plannerItems, Map<String, CraftableTarget> deduped) {
        try {
            Method getVariants = recipeObject.getClass().getMethod("getVariants");
            @SuppressWarnings("unchecked")
            List<Object> variants = (List<Object>) getVariants.invoke(recipeObject);
            for (Object variant : variants) {
                Method resultMethod = variant.getClass().getMethod("result");
                ItemStack result = ((ItemStack) resultMethod.invoke(variant)).copy();
                if (result.isEmpty() || !plannerItems.contains(result.getItem())) {
                    continue;
                }

                Method displayedInputsMethod = variant.getClass().getMethod("displayedInputs");
                @SuppressWarnings("unchecked")
                List<ItemStack> displayedInputs = copyStacks((Collection<ItemStack>) displayedInputsMethod.invoke(variant));

                deduped.putIfAbsent(
                        targetKey(result),
                        new CraftableTarget(result, recipeId, toTargetOutputSpec(result), displayedInputs)
                );
            }
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Exception e) {
            RecursiveCraft.LOGGER.debug("Failed to collect grouped JEI crafting variants for {}", recipeId, e);
            return false;
        }
    }

    private static ItemStack safeRecipeResult(CraftingRecipe recipe) {
        try {
            return recipe.getResultItem(RegistryAccess.EMPTY).copy();
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static @Nullable TargetOutputSpec toTargetOutputSpec(ItemStack stack) {
        if (stack.getComponentsPatch().isEmpty()) {
            return null;
        }
        return TargetOutputSpec.fromStack(stack);
    }

    private static String targetKey(ItemStack stack) {
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId + "|" + ItemStackComponentSupport.componentKey(stack);
    }

    private static List<ItemStack> copyStacks(Collection<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return List.copyOf(copies);
    }
}
