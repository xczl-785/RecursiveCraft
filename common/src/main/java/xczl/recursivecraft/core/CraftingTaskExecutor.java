package xczl.recursivecraft.core;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.config.ModConfig;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.execution.ExecutionCommitResult;
import xczl.recursivecraft.runtime.inventory.PlayerInventoryView;
import xczl.recursivecraft.runtime.match.DefaultMaterialMatcher;
import xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.MaterialKey;
import xczl.recursivecraft.runtime.material.NormalizationKind;
import xczl.recursivecraft.runtime.material.NormalizationResult;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class CraftingTaskExecutor {
    private static class NetChanges {
        final Map<MaterialKey, Integer> needs;
        final Map<Item, Integer> provides;
        final Map<MaterialKey, Integer> normalizedProvides;
        final boolean hasUnsupportedProvides;

        private NetChanges(Map<MaterialKey, Integer> needs, Map<Item, Integer> provides,
                           Map<MaterialKey, Integer> normalizedProvides, boolean hasUnsupportedProvides) {
            this.needs = needs;
            this.provides = provides;
            this.normalizedProvides = normalizedProvides;
            this.hasUnsupportedProvides = hasUnsupportedProvides;
        }
    }

    public static boolean tryExecute(ServerPlayer player, Item targetItem, int amount, ResourceLocation forcedRecipeId, Consumer<Component> msgSender) {
        return tryExecute(player, targetItem, amount, forcedRecipeId, null, msgSender);
    }

    public static boolean tryExecute(ServerPlayer player, Item targetItem, int amount,
                                     @Nullable ResourceLocation forcedRecipeId,
                                     @Nullable TargetOutputSpec targetOutputSpec,
                                     Consumer<Component> msgSender) {
        if (!isValidRequest(targetItem, amount, targetOutputSpec, msgSender)) {
            return false;
        }

        if (!CraftingPlanner.getInstance().isReady()) {
            msgSender.accept(Component.translatable("recursivecraft.msg.crafting_init"));
            return false;
        }

        MaterialKey desiredOutputKey = resolveDesiredOutputKey(targetOutputSpec, msgSender);
        if (targetOutputSpec != null && desiredOutputKey == null) {
            return false;
        }

        CraftingRecipe usedRecipe = resolveRecipe(player, targetItem, forcedRecipeId);
        CraftingTransaction transaction = calculateTransaction(player, targetItem, amount, forcedRecipeId, usedRecipe, desiredOutputKey);

        NetChanges netChanges = splitNetChanges(transaction, desiredOutputKey);
        if (transaction.isUnsupported()) {
            msgSender.accept(Component.translatable("recursivecraft.msg.craft_fail", "UNSUPPORTED"));
            return false;
        }

        if (!hasEnoughTargetProvide(targetItem, amount, desiredOutputKey, netChanges)) {
            msgSender.accept(Component.translatable("recursivecraft.msg.craft_fail", "MISSING"));
            return false;
        }

        if (!hasEnoughMaterials(player, netChanges.needs)) {
            msgSender.accept(Component.translatable("recursivecraft.msg.craft_fail", "MISSING"));
            return false;
        }

        printDebugLog(msgSender, netChanges.needs, netChanges.provides);
        return executeTransaction(player, targetItem, amount, transaction, msgSender);
    }

    private static boolean isValidRequest(Item targetItem, int amount,
                                          @Nullable TargetOutputSpec targetOutputSpec,
                                          Consumer<Component> msgSender) {
        if (targetItem == Items.AIR || amount <= 0) {
            msgSender.accept(Component.translatable("recursivecraft.msg.invalid_request"));
            return false;
        }
        if (targetOutputSpec != null && targetOutputSpec.item() != targetItem) {
            msgSender.accept(Component.translatable("recursivecraft.msg.invalid_request"));
            return false;
        }
        int maxCraftAmount = configuredMaxCraftAmount();
        if (amount > maxCraftAmount) {
            msgSender.accept(Component.translatable("recursivecraft.msg.amount_over_limit", maxCraftAmount));
            return false;
        }
        return true;
    }

    private static @Nullable MaterialKey resolveDesiredOutputKey(@Nullable TargetOutputSpec targetOutputSpec,
                                                                 Consumer<Component> msgSender) {
        if (targetOutputSpec == null) {
            return null;
        }
        DefaultMaterialIdentityNormalizer normalizer = new DefaultMaterialIdentityNormalizer();
        NormalizationResult result = normalizer.normalize(targetOutputSpec.toTemplateStack());
        if (result.kind() != NormalizationKind.NORMALIZED) {
            msgSender.accept(Component.translatable("recursivecraft.msg.craft_fail", "UNSUPPORTED"));
            return null;
        }
        return result.key();
    }

    private static CraftingRecipe resolveRecipe(ServerPlayer player, Item targetItem, ResourceLocation forcedRecipeId) {
        CraftingRecipe usedRecipe = null;
        if (forcedRecipeId != null) {
            Optional<? extends Recipe<?>> opt = player.level().getRecipeManager().byKey(forcedRecipeId);
            if (opt.isPresent() && opt.get() instanceof CraftingRecipe cr) {
                if (!cr.isSpecial() && cr.getResultItem(player.level().registryAccess()).getItem() == targetItem) {
                    usedRecipe = cr;
                }
            }
        }
        if (usedRecipe == null) {
            usedRecipe = CraftingPlanner.getInstance().getResult().getPathMemo().get(targetItem);
        }
        return usedRecipe;
    }

    private static CraftingTransaction calculateTransaction(ServerPlayer player, Item targetItem, int amount,
                                                            ResourceLocation forcedRecipeId, CraftingRecipe usedRecipe,
                                                            @Nullable MaterialKey desiredOutputKey) {
        TransactionCalculator calculator = new TransactionCalculator(player.getInventory());
        CraftingRecipe recipeForCalc = (forcedRecipeId != null) ? usedRecipe : null;
        return calculator.calculate(targetItem, amount, true, recipeForCalc, desiredOutputKey);
    }

    private static NetChanges splitNetChanges(CraftingTransaction transaction, @Nullable MaterialKey desiredOutputKey) {
        Map<MaterialKey, Integer> netNeeds = new HashMap<>(transaction.getMaterialNeeds());
        Map<Item, Integer> netProvides = new HashMap<>();
        Map<MaterialKey, Integer> normalizedProvides = new HashMap<>();
        DefaultMaterialIdentityNormalizer normalizer = desiredOutputKey == null ? null : new DefaultMaterialIdentityNormalizer();
        boolean hasUnsupportedProvides = false;
        for (ItemStack output : transaction.getResolvedOutputs()) {
            netProvides.merge(output.getItem(), output.getCount(), Integer::sum);
            if (normalizer != null) {
                NormalizationResult result = normalizer.normalize(output.copy());
                if (result.kind() != NormalizationKind.NORMALIZED) {
                    continue;
                }
                normalizedProvides.merge(result.key(), output.getCount(), Integer::sum);
            }
        }
        return new NetChanges(netNeeds, netProvides, normalizedProvides, hasUnsupportedProvides);
    }

    private static int configuredMaxCraftAmount() {
        try {
            return ModConfig.maxCraftAmount;
        } catch (Throwable ignored) {
            return 2304;
        }
    }

    private static boolean hasEnoughTargetProvide(Item targetItem, int amount,
                                                  @Nullable MaterialKey desiredOutputKey,
                                                  NetChanges netChanges) {
        if (desiredOutputKey != null) {
            int actualProvide = netChanges.normalizedProvides.getOrDefault(desiredOutputKey, 0);
            return actualProvide >= amount;
        }
        return hasEnoughTargetProvide(targetItem, amount, netChanges.provides);
    }

    private static boolean hasEnoughTargetProvide(Item targetItem, int amount, Map<Item, Integer> netProvides) {
        int actualProvide = netProvides.getOrDefault(targetItem, 0);
        return actualProvide >= amount;
    }

    private static boolean hasEnoughMaterials(ServerPlayer player, Map<MaterialKey, Integer> netNeeds) {
        var normalizer = new DefaultMaterialIdentityNormalizer();
        var view = new PlayerInventoryView(player);
        var snap = view.snapshot(normalizer);
        for (Map.Entry<MaterialKey, Integer> e : netNeeds.entrySet()) {
            if (snap.totals().getOrDefault(e.getKey(), 0) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean executeTransaction(ServerPlayer player, Item targetItem, int amount,
                                              CraftingTransaction transaction, Consumer<Component> msgSender) {
        try {
            DefaultMaterialIdentityNormalizer normalizer = new DefaultMaterialIdentityNormalizer();
            DefaultMaterialMatcher matcher = new DefaultMaterialMatcher(normalizer);
            PlayerInventoryView view = new PlayerInventoryView(player);
            var plan = view.planExecution(transaction, normalizer, matcher);
            if (plan.consumptions().isEmpty() && !transaction.getMaterialNeeds().isEmpty()) {
                msgSender.accept(Component.translatable("recursivecraft.msg.craft_fail", visibleFailureCode(ExecutionCommitResult.Status.FAILED_REVALIDATION)));
                return false;
            }
            ExecutionCommitResult result = view.commitExecution(plan, transaction);
            if (!result.success()) {
                msgSender.accept(Component.translatable("recursivecraft.msg.craft_fail", visibleFailureCode(result.status())));
                return false;
            }
            msgSender.accept(Component.translatable("recursivecraft.msg.craft_success", amount, targetItem.getDescription().getString()));
            return true;
        } catch (Exception e) {
            msgSender.accept(Component.translatable("recursivecraft.msg.craft_fail", e.getMessage()));
            return false;
        }
    }

    static String visibleFailureCode(ExecutionCommitResult.Status status) {
        if (status == ExecutionCommitResult.Status.FAILED_REVALIDATION || status == ExecutionCommitResult.Status.FAILED_CONSUME) {
            return "MISSING";
        }
        return status.name();
    }

    private static void printDebugLog(Consumer<Component> msgSender, Map<MaterialKey, Integer> netNeeds, Map<Item, Integer> netProvides) {
        msgSender.accept(Component.literal("§8--- [RecursiveCraft Transaction] ---"));
        if (!netNeeds.isEmpty()) {
            msgSender.accept(Component.translatable("recursivecraft.msg.debug_consumes"));
            netNeeds.forEach((key, itemAmount) -> msgSender.accept(Component.literal("  - " + itemAmount + "x " + key)));
        }
        if (!netProvides.isEmpty()) {
            msgSender.accept(Component.translatable("recursivecraft.msg.debug_produces"));
            netProvides.forEach((item, itemAmount) -> msgSender.accept(Component.literal("  - " + itemAmount + "x " + item.getDescription().getString())));
        }
        msgSender.accept(Component.literal("§8---------------------------------"));
    }
}
