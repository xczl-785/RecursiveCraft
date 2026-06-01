# recursive-craft-execution

## Quick Read

- **id**: `recursive-craft-execution`
- **name**: 递归合成执行链路
- **summary**: 将命令、网络包和 JEI 递归触发统一路由到事务计算器，支持可选 `TargetOutputSpec` 目标产物身份，并通过执行计划桥接到真实背包扣减与产物投放。
- **scope**: 包含请求校验、顶层配方解析、目标产物身份解析、运行时库存快照、递归事务计算、请求级失败语义与正式执行桥接；不包含全局规划预计算与 GUI 呈现细节。
- **entry_points**:
  - `/craft_recursive`
  - `C2SExecuteCraftPacket.handle()`
  - `CraftingTaskExecutor.tryExecute()`
- **shared_with**:
  - None
- **check_on_change**:
  - 所有执行入口是否仍汇聚到 `CraftingTaskExecutor.tryExecute()`
  - `targetItem` 与 `TargetOutputSpec.item` 的不变量是否仍在入口层被拒绝
  - 指定目标产物身份时，成功判定是否仍按规范化后的目标身份而非仅按 `Item` 数量
  - 运行时库存模型是否仍以 `MaterialKey` 为正式身份
  - 正式执行是否仍经 `InventoryView.planExecution()/commitExecution()`
- **last_verified**: 2026-05-31

---

## Capability Summary

该能力负责把玩家的递归合成请求转化为一笔可执行的事务。当前实现不是逐级真实摆放和运行每一级 recipe，而是先在内存中做“基于 `MaterialKey` 的虚拟库存递归求解”，再把事务桥接成 `ResolvedExecutionPlan`，最后对玩家背包做 revalidate、精确扣减与产物投放。

该能力仍然是当前 NBT 兼容问题的核心承压区，但其主执行链已经从纯 `Map<Item, Integer>` 模型切换为运行时材料身份模型；截至 `2026-05-31`，它还新增了“可选目标产物身份指定”能力，可把 JEI 当前展示的输出身份沿包体、执行器和事务计算一路传递到成功判定。当前残留问题主要集中在少量 `Item` 级桥接、matcher 预留骨架，以及真实运行时 JEI / gameplay 复验尚未完成。

截至 `2026-06-01`，为闭合 `Phase 4A` checklist `2.2 / 2.3` 的运行时复验，还额外补入了一组最小 debug fixture：

- 两条真实、非 special 的 debug recipe
- 同一 `Item` 输出，两个不同的 `recursivecraft_debug.variant`
- 仅用于给 `JEI Ctrl` runtime verification 提供稳定样例

该 fixture 不是正式玩法承诺，而是当前阶段为补证据引入的 shipped debug content。

---

## Entries

| Entry | Trigger | Evidence | Notes |
| --- | --- | --- | --- |
| 命令入口 | 管理员执行 `/craft_recursive` | `common/src/main/java/xczl/recursivecraft/command/RecursiveCraftCommand.java:16` | 强制顶层不指定配方 |
| 网络包入口 | GUI 或 JEI 发送 `C2SExecuteCraftPacket` | `common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java:52` | 包体可选携带 `forcedRecipeId + TargetOutputSpec`，服务端排队后统一进入执行器 |
| 统一执行器 | `tryExecute()` | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37` | 所有递归合成路径的统一总入口 |
| 递归事务计算 | `TransactionCalculator.calculate()` | `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:64` | 生成事务而不真实逐级 craft |
| 执行桥接与提交 | `PlayerInventoryView.planExecution()/commitExecution()` | `common/src/main/java/xczl/recursivecraft/runtime/inventory/PlayerInventoryView.java:42` | 对玩家真实背包做 revalidate、精确扣减与产物投放 |

---

## Current Rules

### CR-001: 递归合成入口统一汇聚到 `CraftingTaskExecutor.tryExecute()`

命令与网络包处理都通过 `CraftingTaskExecutor.tryExecute()` 进入统一的执行链路，避免不同入口维护不同执行规则。

**Evidence**: `common/src/main/java/xczl/recursivecraft/command/RecursiveCraftCommand.java:37`

### CR-002: 请求在执行前必须通过合法性与规划完成校验

执行器会先校验 `targetItem`、数量上限、`targetItem == TargetOutputSpec.item` 不变量，以及 `CraftingPlanner` 是否 ready；不满足时直接拒绝，不进入递归事务计算。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37`

### CR-003: 可选 `TargetOutputSpec` 会沿包体与公共重载进入统一执行链，缺省时保持旧路径形状

`C2SExecuteCraftPacket` 现在支持可选 `TargetOutputSpec` 序列化/反序列化；`CraftingTaskExecutor.tryExecute(...)` 与 `TransactionCalculator.calculate(...)` 也都公开支持该目标身份参数。若该参数为空，则继续走旧的物品级请求语义，不要求调用方升级协议。

**Evidence**: `common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java:20`

### CR-004: 指定目标产物身份时，成功判定改为检查规范化后的目标身份，而不是只看 `Item` 数量

执行器会先把 `TargetOutputSpec` 规范化为 `desiredOutputKey`，再把它传入 `TransactionCalculator`。当目标身份存在时，成功判定检查的是 `normalizedProvides[desiredOutputKey]`；只有在未指定目标身份时，才回退到旧的 `Map<Item, Integer>` 成功判定。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:61`

### CR-005: `JEI Ctrl` 递归构包必须绑定当前展示输出身份和 `recipeId`

JEI 递归路径不再只发送“这个 `Item` 要做几个”，而是会从当前展示输出提取 `ItemStack` 身份，构造 `TargetOutputSpec`，并同时携带 `recipeId`。这保证了“同一 `Item`、不同 NBT 输出变体”会形成不同请求。

**Evidence**: `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:204`

### CR-005A: `Phase 4A` debug fixture 为 `JEI Ctrl` runtime verification 提供同一 `Item` 多身份样例

仓库当前存在两条 debug fixture recipe，它们共享同一个输出 `Item`，但携带不同的 `recursivecraft_debug.variant`。其目的不是扩展正式玩法，而是确保 `2.2 / 2.3` 可以在真实运行时验证“JEI 展示什么身份，递归就按什么身份执行”。

**Evidence**: `common/src/main/resources/data/recursivecraft/recipes/debug/handheld_crafter_red.json:1`

### CR-006: 顶层配方优先使用强制 recipe，否则回退到 planner 的最优结果

若请求携带 `forcedRecipeId`，执行器先解析并验证该 recipe；否则取 `PlanningResult.pathMemo[targetItem]` 作为默认顶层配方。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:88`

### CR-007: 运行时递归计算基于材料身份快照和虚拟库存推进

`TransactionCalculator` 会先把玩家背包快照为 `VirtualInventorySnapshot`，并用 `MaterialKey` 作为运行时正式材料身份；后续所有递归求解、候选排序和 ingredient 尝试都在该虚拟库存上进行。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:87`

### CR-008: 正式执行通过执行计划桥接到真实背包，不逐级真实合成

`CraftingTaskExecutor` 不会逐级真实走每一级配方的 craft grid，而是先生成 `CraftingTransaction`，再通过 `PlayerInventoryView.planExecution()` 绑定真实槽位，并在 `commitExecution()` 中完成 revalidate、精确扣减和产物投放。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:137`

### CR-009: 当前执行链路主路径已按材料身份建模，但仍保留少量 `Item` 级桥接

当前正式主路径中的快照、材料匹配、虚拟扣减与正式执行已经围绕 `MaterialKey` 工作；但顶层目标产出检查和部分事务字段仍保留 `Item` 级桥接，用于支撑第一阶段范围内的行为。其中 `CraftingTransaction.needs` 现已收紧为“仅表示 unresolved 的 item-level missing deficits”，不再记录已经被虚拟库存满足的消费。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:103`

### CR-010: 玩家背包快照构造已统一收拢到 `VirtualInventorySnapshot.fromInventory(...)`

`TransactionCalculator` 与 `PlayerInventoryView` 不再各自维护一套背包快照构造逻辑，而是统一调用 `VirtualInventorySnapshot.fromInventory(...)` 生成运行时快照。

**Evidence**: `common/src/main/java/xczl/recursivecraft/runtime/inventory/VirtualInventorySnapshot.java:26`

### CR-011: 请求级 `MISSING / UNSUPPORTED` 收口已统一复用 `MaterialMatcher.aggregate(...)`

`TransactionCalculator` 不再在 ingredient 级和 recipe 级手写 `sawUnsupported` 聚合，而是将候选尝试结果映射为 `CandidateMatchResult` 后统一交给 `MaterialMatcher.aggregate(...)` 形成最终 `RequestLevelKind`。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:181`

---

## Impact Surface

| Area | What to check | Evidence |
| --- | --- | --- |
| 入口一致性 | 若新增入口，需确认其最终仍走统一执行器而不是另写一套库存/事务逻辑 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37` |
| 包体兼容 | 若改动 `C2SExecuteCraftPacket` 字段形状，需同时检查旧无 `TargetOutputSpec` 路径仍可 decode / execute，以及新路径的 `targetItem/spec.item` 不变量仍会拒绝冲突请求 | `common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java:58` |
| 配方解析 | 若改动 JEI 强制 recipe 或顶层默认选配逻辑，需同步检查命令、包和 JEI 行为 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:88` |
| 目标产物成功判定 | 若改动目标产物身份流，需同时检查 `desiredOutputKey` 解析、`normalizedProvides` 统计、旧 item-level 成功路径回退，以及 `MISSING / UNSUPPORTED` 可见语义 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:61` |
| 库存模型 | 若改动运行时身份模型，需同时检查共享快照构造、缺料判断、虚拟扣减、执行桥接与请求级失败语义 | `common/src/main/java/xczl/recursivecraft/runtime/inventory/VirtualInventorySnapshot.java:26` |
| 执行语义 | 若切换为多来源或容器级执行，需重写 `InventoryView`、`ResolvedExecutionPlan` 与事务桥接边界 | `common/src/main/java/xczl/recursivecraft/runtime/inventory/PlayerInventoryView.java:42` |
| JEI 展示输出绑定 | 若改动 JEI 输出槽读取或 Ctrl 路径构包，需确认展示变体身份仍被原样带入网络请求，并与 `recipeId` 成对出现 | `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:74` |
| Runtime verification fixture | 若改动 `debug/handheld_crafter_red|blue` 或 tagged recipe serializer，需同时检查 recipe id、输出 tag、planner 默认成本、JEI 构包测试与目标身份测试 | `common/src/main/resources/data/recursivecraft/recipes/debug/handheld_crafter_red.json:1` |
| NBT 兼容 | 若继续推进 NBT，需统一修正残留 `Item` 级桥接、缺料分析与目标产物语义 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:103` |

---

## Verification Status

- 自动化验证已覆盖：
  - `TargetOutputSpec` 模板栈导出与构造约束
  - `C2SExecuteCraftPacket` 的旧形状兼容、新字段 round-trip、`targetItem/spec.item` 冲突拒绝
  - `CraftingTaskExecutor` 的旧成功判定保留、目标身份 `MISSING / UNSUPPORTED` 结果、入口不变量拒绝
  - `TransactionCalculator` 的目标身份重载、目标输出 identity 过滤、旧空 spec 语义保留
  - `JEI Ctrl` 递归构包的 `recipeId + TargetOutputSpec` 绑定
  - debug fixture recipe / serializer 的 tagged output 与 planner 成本约束
  - debug fixture 在执行器 / JEI 测试中的 red/blue sibling identity 路径
- 固定验证命令：
  - `java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft :common:test --tests "xczl.recursivecraft.runtime.material.TargetOutputSpecTest" --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest" --tests "xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest" --tests "xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest"`
- 真实运行时待验证：
  - `JEI Ctrl` gameplay 端到端手工复验仍未完成，当前不能据此宣称 `Phase 4A` 整体完成
- 当前主动复验文档：
  - `docs/Phase4A_运行时验证.md`

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
| `RecursiveCraftTransferHandler` | Ctrl 递归合成通过网络包复用同一执行链路，并把展示输出身份写入请求 | `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:83` |

---

## Archive Pointer

- 历史实施计划与旧 handoff 已迁入 `docs/废弃/plans/`
