package xczl.recursivecraft.runtime.inventory;

import xczl.recursivecraft.data.CraftingTransaction;
import xczl.recursivecraft.runtime.execution.ExecutionCommitResult;
import xczl.recursivecraft.runtime.execution.ResolvedExecutionPlan;
import xczl.recursivecraft.runtime.match.MaterialMatcher;
import xczl.recursivecraft.runtime.material.MaterialIdentityNormalizer;

import java.util.List;

public interface InventoryView {
    VirtualInventorySnapshot snapshot(MaterialIdentityNormalizer normalizer);
    ResolvedExecutionPlan planExecution(CraftingTransaction transaction, MaterialIdentityNormalizer normalizer, MaterialMatcher matcher);
    ExecutionCommitResult commitExecution(ResolvedExecutionPlan plan, CraftingTransaction transaction);
    List<InventorySource> sources();
}
