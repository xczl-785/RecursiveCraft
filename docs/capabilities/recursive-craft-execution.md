# recursive-craft-execution

## Quick Read

- **id**: `recursive-craft-execution`
- **name**: 递归合成执行链路
- **summary**: 将命令、网络包和 JEI 递归触发统一路由到事务计算器，生成净变化事务并直接扣除/发放物品。
- **scope**: 包含请求校验、顶层配方解析、玩家库存快照、递归事务计算、缺料分析与事务执行；不包含全局规划预计算与 GUI 呈现细节。
- **entry_points**:
  - `/craft_recursive`
  - `C2SExecuteCraftPacket.handle()`
  - `CraftingTaskExecutor.tryExecute()`
- **shared_with**:
  - None
- **check_on_change**:
  - 所有执行入口是否仍汇聚到 `CraftingTaskExecutor.tryExecute()`
  - 库存模型是否仍为 `Map<Item, Integer>`
  - 事务执行是否仍按 `netDeltas` 直接扣给物品
- **last_verified**: 2026-05-31

---

## Capability Summary

该能力负责把玩家的递归合成请求转化为一笔可执行的事务。当前实现不是逐级真实摆放和运行每一级 recipe，而是先在内存中做“虚拟库存递归求解”，最终得到 `needs` / `provides` / `netDeltas`，再直接对玩家背包做扣减和产物投放。

该能力是当前 NBT 兼容问题的核心承压区，因为其库存快照、虚拟扣减、缺料分析和正式执行都围绕 `Map<Item, Integer>` 与 `stack.getItem() == item` 展开。

---

## Entries

| Entry | Trigger | Evidence | Notes |
| --- | --- | --- | --- |
| 命令入口 | 管理员执行 `/craft_recursive` | `common/src/main/java/xczl/recursivecraft/command/RecursiveCraftCommand.java:16` | 强制顶层不指定配方 |
| 网络包入口 | GUI 或 JEI 发送 `C2SExecuteCraftPacket` | `common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java:52` | 服务端排队后统一进入执行器 |
| 统一执行器 | `tryExecute()` | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37` | 所有递归合成路径的统一总入口 |
| 递归事务计算 | `TransactionCalculator.calculate()` | `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:64` | 生成事务而不真实逐级 craft |
| 事务落地 | `CraftingTransaction.execute()` | `common/src/main/java/xczl/recursivecraft/data/CraftingTransaction.java:85` | 对玩家真实背包执行净变化 |

---

## Current Rules

### CR-001: 递归合成入口统一汇聚到 `CraftingTaskExecutor.tryExecute()`

命令与网络包处理都通过 `CraftingTaskExecutor.tryExecute()` 进入统一的执行链路，避免不同入口维护不同执行规则。

**Evidence**: `common/src/main/java/xczl/recursivecraft/command/RecursiveCraftCommand.java:37`

### CR-002: 请求在执行前必须通过合法性与规划完成校验

执行器会先校验 `targetItem`、数量上限以及 `CraftingPlanner` 是否 ready；不满足时直接拒绝，不进入递归事务计算。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37`

### CR-003: 顶层配方优先使用强制 recipe，否则回退到 planner 的最优结果

若请求携带 `forcedRecipeId`，执行器先解析并验证该 recipe；否则取 `PlanningResult.pathMemo[targetItem]` 作为默认顶层配方。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:88`

### CR-004: 运行时递归计算基于玩家库存快照和虚拟库存推进

`TransactionCalculator` 会先把玩家背包快照成 `Map<Item, Integer>`，后续所有递归求解、候选排序和 ingredient 尝试都在虚拟库存上进行。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:64`

### CR-005: 事务执行按净变化直接扣给，不逐级真实合成

`CraftingTransaction.execute()` 只根据 `netDeltas` 从玩家背包扣除净需求并发放净产物，不会逐步真实走每一级配方的 craft grid。

**Evidence**: `common/src/main/java/xczl/recursivecraft/data/CraftingTransaction.java:85`

### CR-006: 当前执行链路按 Item 聚合库存与材料需求

`InventoryUtils.snapshot()`、`hasEnoughMaterials()` 和事务执行都围绕 `Item` + 数量建模，不保留 `ItemStack` 的 NBT/耐久等实例级信息。

**Evidence**: `common/src/main/java/xczl/recursivecraft/utils/InventoryUtils.java:17`

---

## Impact Surface

| Area | What to check | Evidence |
| --- | --- | --- |
| 入口一致性 | 若新增入口，需确认其最终仍走统一执行器而不是另写一套库存/事务逻辑 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37` |
| 配方解析 | 若改动 JEI 强制 recipe 或顶层默认选配逻辑，需同步检查命令、包和 JEI 行为 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:88` |
| 库存模型 | 若改动 `Map<Item, Integer>` 键模型，需同时检查快照、缺料判断、虚拟扣减与正式执行 | `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:32` |
| 执行语义 | 若切换为真实逐级 crafting 或容器级执行，需重写 `CraftingTransaction` 的记录结构与执行方式 | `common/src/main/java/xczl/recursivecraft/data/CraftingTransaction.java:20` |
| NBT 兼容 | 若支持 NBT，需统一修正预检、递归计算、缺料分析与实际扣除的匹配规则 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:133` |

---

## Shared Rules Dependency

| Shared Rule | Dependency | Lifted |
| --- | --- | --- |
| None | 当前没有正式上提的 shared rule | no |

---

## Uncertainties

- 当前未确认 `tryExecute()` 是否应长期保持命令仅 OP 可用、包入口不做权限限制的差异模型。
- 当前未确认未来若支持外部存储，事务执行是否仍直接以 `Player` 作为唯一落地点。

---

## Known Consumers

| Consumer | Usage | Evidence |
| --- | --- | --- |
| `RecursiveCraftCommand` | 命令路径使用统一执行器发起递归合成 | `common/src/main/java/xczl/recursivecraft/command/RecursiveCraftCommand.java:37` |
| `C2SExecuteCraftPacket` | 网络路径使用统一执行器发起递归合成 | `common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java:59` |
| `RecursiveCraftTransferHandler` | Ctrl 递归合成通过网络包复用同一执行链路 | `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:83` |

---

## Archive Pointer

- None
