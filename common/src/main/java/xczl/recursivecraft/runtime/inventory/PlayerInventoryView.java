package xczl.recursivecraft.runtime.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.execution.ExecutionCommitResult;
import xczl.recursivecraft.runtime.execution.ResolvedConsumption;
import xczl.recursivecraft.runtime.execution.ResolvedExecutionPlan;
import xczl.recursivecraft.runtime.match.MaterialMatcher;
import xczl.recursivecraft.runtime.material.MaterialIdentityNormalizer;
import xczl.recursivecraft.runtime.material.NormalizationKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerInventoryView implements InventoryView {
    private final Player player;

    public PlayerInventoryView(Player player) {
        this.player = player;
    }

    @Override
    public VirtualInventorySnapshot snapshot(MaterialIdentityNormalizer normalizer) {
        Map<xczl.recursivecraft.runtime.material.MaterialKey, Integer> totals = new HashMap<>();
        Map<Item, List<xczl.recursivecraft.runtime.material.MaterialKey>> index = new HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            var n = normalizer.normalize(stack);
            if (n.kind() != NormalizationKind.NORMALIZED) continue;
            totals.merge(n.key(), stack.getCount(), Integer::sum);
            index.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(n.key());
        }
        return new VirtualInventorySnapshot(totals, index);
    }

    @Override
    public ResolvedExecutionPlan planExecution(CraftingTransaction transaction, MaterialIdentityNormalizer normalizer, MaterialMatcher matcher) {
        List<ResolvedConsumption> consumptions = new ArrayList<>();
        PlayerInventorySource src = new PlayerInventorySource(player, normalizer);
        for (Map.Entry<xczl.recursivecraft.runtime.material.MaterialKey, Integer> e : transaction.getMaterialNeeds().entrySet()) {
            int remain = e.getValue();
            for (int i = 0; i < player.getInventory().getContainerSize() && remain > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                var n = normalizer.normalize(stack);
                if (n.kind() != NormalizationKind.NORMALIZED) continue;
                if (matcher.match(n.key(), new xczl.recursivecraft.runtime.match.IngredientRequirement(List.of(e.getKey()), List.of(), false)).kind()
                        != xczl.recursivecraft.runtime.match.CandidateMatchKind.MATCHED) continue;
                int take = Math.min(remain, stack.getCount());
                consumptions.add(new ResolvedConsumption(src, i, n.key(), take));
                remain -= take;
            }
            if (remain > 0) return new ResolvedExecutionPlan(List.of());
        }
        return new ResolvedExecutionPlan(consumptions);
    }

    @Override
    public ExecutionCommitResult commitExecution(ResolvedExecutionPlan plan, CraftingTransaction transaction) {
        if (plan.consumptions().isEmpty() && !transaction.getMaterialNeeds().isEmpty()) {
            return ExecutionCommitResult.failedRevalidate();
        }
        // revalidate first
        for (ResolvedConsumption c : plan.consumptions()) {
            ItemStack stack = player.getInventory().getItem(c.slotIndex());
            if (stack.isEmpty() || stack.getCount() < c.amount()) {
                return ExecutionCommitResult.failedRevalidate();
            }
        }
        // simulate grouped consume to avoid partial commit on same slot
        Map<Integer, Integer> consumeBySlot = new HashMap<>();
        for (ResolvedConsumption c : plan.consumptions()) {
            consumeBySlot.merge(c.slotIndex(), c.amount(), Integer::sum);
        }
        for (Map.Entry<Integer, Integer> e : consumeBySlot.entrySet()) {
            if (player.getInventory().getItem(e.getKey()).getCount() < e.getValue()) {
                return ExecutionCommitResult.failedRevalidate();
            }
        }
        for (ResolvedConsumption c : plan.consumptions()) {
            if (!c.source().consumeAt(c.slotIndex(), c.key(), c.amount())) {
                return ExecutionCommitResult.failedConsume();
            }
        }
        boolean fallback = false;
        for (ItemStack out : transaction.getResolvedOutputs()) {
            ItemStack toInsert = out.copy();
            InsertionResult r = ((PlayerInventorySource) sources().get(0)).insert(toInsert);
            if (!r.inserted()) {
                player.drop(toInsert, false);
                fallback = true;
            }
        }
        return fallback ? ExecutionCommitResult.okWithFallback() : ExecutionCommitResult.ok();
    }

    @Override
    public List<InventorySource> sources() {
        return List.of(new PlayerInventorySource(player, new xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizer()));
    }
}
