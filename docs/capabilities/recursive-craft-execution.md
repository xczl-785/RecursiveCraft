# recursive-craft-execution

## Quick Read

- **id**: `recursive-craft-execution`
- **name**: 递归合成执行链路
- **summary**: 将命令、网络包和 JEI 递归触发统一路由到事务计算器，基于运行时材料身份生成事务，并通过执行计划桥接到真实背包扣减与产物投放。
- **scope**: 包含请求校验、顶层配方解析、运行时库存快照、递归事务计算、请求级失败语义与正式执行桥接；不包含全局规划预计算与 GUI 呈现细节。
- **entry_points**:
  - `/craft_recursive`
  - `C2SExecuteCraftPacket.handle()`
  - `CraftingTaskExecutor.tryExecute()`
- **shared_with**:
  - None
- **check_on_change**:
  - 所有执行入口是否仍汇聚到 `CraftingTaskExecutor.tryExecute()`
  - 运行时库存模型是否仍以 `MaterialKey` 为正式身份
  - 正式执行是否仍经 `InventoryView.planExecution()/commitExecution()`
- **last_verified**: 2026-05-31

---

## Capability Summary

该能力负责把玩家的递归合成请求转化为一笔可执行的事务。当前实现不是逐级真实摆放和运行每一级 recipe，而是先在内存中做“基于 `MaterialKey` 的虚拟库存递归求解”，再把事务桥接成 `ResolvedExecutionPlan`，最后对玩家背包做 revalidate、精确扣减与产物投放。

该能力仍然是当前 NBT 兼容问题的核心承压区，但其主执行链已经从纯 `Map<Item, Integer>` 模型切换为运行时材料身份模型；当前残留问题主要集中在少量 `Item` 级桥接、matcher 预留骨架与后续扩展保留点。

---

## Entries

| Entry | Trigger | Evidence | Notes |
| --- | --- | --- | --- |
| 命令入口 | 管理员执行 `/craft_recursive` | `common/src/main/java/xczl/recursivecraft/command/RecursiveCraftCommand.java:16` | 强制顶层不指定配方 |
| 网络包入口 | GUI 或 JEI 发送 `C2SExecuteCraftPacket` | `common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java:52` | 服务端排队后统一进入执行器 |
| 统一执行器 | `tryExecute()` | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37` | 所有递归合成路径的统一总入口 |
| 递归事务计算 | `TransactionCalculator.calculate()` | `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:64` | 生成事务而不真实逐级 craft |
| 执行桥接与提交 | `PlayerInventoryView.planExecution()/commitExecution()` | `common/src/main/java/xczl/recursivecraft/runtime/inventory/PlayerInventoryView.java:42` | 对玩家真实背包做 revalidate、精确扣减与产物投放 |

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

### CR-004: 运行时递归计算基于材料身份快照和虚拟库存推进

`TransactionCalculator` 会先把玩家背包快照为 `VirtualInventorySnapshot`，并用 `MaterialKey` 作为运行时正式材料身份；后续所有递归求解、候选排序和 ingredient 尝试都在该虚拟库存上进行。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:87`

### CR-005: 正式执行通过执行计划桥接到真实背包，不逐级真实合成

`CraftingTaskExecutor` 不会逐级真实走每一级配方的 craft grid，而是先生成 `CraftingTransaction`，再通过 `PlayerInventoryView.planExecution()` 绑定真实槽位，并在 `commitExecution()` 中完成 revalidate、精确扣减和产物投放。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:137`

### CR-006: 当前执行链路主路径已按材料身份建模，但仍保留少量 `Item` 级桥接

当前正式主路径中的快照、材料匹配、虚拟扣减与正式执行已经围绕 `MaterialKey` 工作；但顶层目标产出检查和部分事务字段仍保留 `Item` 级桥接，用于支撑第一阶段范围内的行为。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:103`

### CR-007: 玩家背包快照构造已统一收拢到 `VirtualInventorySnapshot.fromInventory(...)`

`TransactionCalculator` 与 `PlayerInventoryView` 不再各自维护一套背包快照构造逻辑，而是统一调用 `VirtualInventorySnapshot.fromInventory(...)` 生成运行时快照。

**Evidence**: `common/src/main/java/xczl/recursivecraft/runtime/inventory/VirtualInventorySnapshot.java:26`

---

## Impact Surface

| Area | What to check | Evidence |
| --- | --- | --- |
| 入口一致性 | 若新增入口，需确认其最终仍走统一执行器而不是另写一套库存/事务逻辑 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37` |
| 配方解析 | 若改动 JEI 强制 recipe 或顶层默认选配逻辑，需同步检查命令、包和 JEI 行为 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:88` |
| 库存模型 | 若改动运行时身份模型，需同时检查共享快照构造、缺料判断、虚拟扣减、执行桥接与请求级失败语义 | `common/src/main/java/xczl/recursivecraft/runtime/inventory/VirtualInventorySnapshot.java:26` |
| 执行语义 | 若切换为多来源或容器级执行，需重写 `InventoryView`、`ResolvedExecutionPlan` 与事务桥接边界 | `common/src/main/java/xczl/recursivecraft/runtime/inventory/PlayerInventoryView.java:42` |
| NBT 兼容 | 若继续推进 NBT，需统一修正残留 `Item` 级桥接、缺料分析与目标产物语义 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:103` |

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
