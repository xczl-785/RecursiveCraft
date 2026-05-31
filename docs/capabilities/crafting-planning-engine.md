# crafting-planning-engine

## Quick Read

- **id**: `crafting-planning-engine`
- **name**: 递归合成规划引擎
- **summary**: 在服务端启动阶段扫描全部普通 crafting 配方，预计算每个产物的候选配方、理论最优路径和理论成本。
- **scope**: 包含服务端启动触发、配方索引、成本收敛、规划结果快照发布；不包含玩家库存校验、事务执行、GUI 展示。
- **entry_points**:
  - `RecursiveCraft.init()` 中的 `LifecycleEvent.SERVER_STARTING`
  - `CraftingPlanner.buildOptimalPathTree(RecipeManager)`
- **shared_with**:
  - None
- **check_on_change**:
  - 服务端启动时是否仍触发后台规划线程
  - `PlanningResult` 快照是否仍为只读发布
  - `pathMemo` / `costMemo` / `recipeLookup` 是否仍保持按 `Item` 聚合
- **last_verified**: 2026-05-31

---

## Capability Summary

该能力负责在运行期合成之前，先把“全局配方空间”压缩成可查询的规划结果。当前实现采用后台线程在服务端启动阶段执行一次预计算，核心输出为 `PlanningResult` 快照，其中包含产物到最优配方的映射、理论成本表以及候选配方索引。

当前规划粒度是 `Item`，而不是 `ItemStack`。这意味着它非常适合做“理论最优路径”与“候选排序”的全局缓存，但并不负责精确表达 NBT、耐久、附魔或实例级差异。

---

## Entries

| Entry | Trigger | Evidence | Notes |
| --- | --- | --- | --- |
| 服务端启动规划触发 | 模组初始化后监听 `SERVER_STARTING` | `common/src/main/java/xczl/recursivecraft/RecursiveCraft.java:45` | 在单独线程中启动，避免阻塞主初始化流程 |
| 规划主入口 | 后台线程调用 `buildOptimalPathTree` | `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:91` | 规划的统一入口 |
| 规划结果读取 | 运行期通过 `getResult()` 读取快照 | `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:81` | 供执行链路与 GUI 使用 |

---

## Current Rules

### CR-001: 规划只在服务端启动阶段主动构建

当前实现只在 `LifecycleEvent.SERVER_STARTING` 中启动规划线程，不在客户端初始化或每次请求时重新构建全局规划。

**Evidence**: `common/src/main/java/xczl/recursivecraft/RecursiveCraft.java:45`

### CR-002: 规划结果通过不可变快照发布

规划线程内部使用局部 `Map` 计算，计算完成后一次性写入 `PlanningResult`；`PlanningResult` 对内部 `Map` 和 `List` 做只读包装。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:32`

### CR-003: 规划的核心索引粒度是 Item

`pathMemo`、`costMemo`、`recipeLookup` 都以 `Item` 为主键，因此规划能力当前表达的是“物品级”最优路径，而不是实例级物品状态。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:36`

### CR-004: 配方成本计算对多选原料取最低理论成本

在 `calculateRecipeCost()` 中，若 `Ingredient` 含多个候选项，则取其中理论成本最低的候选作为该 ingredient 的成本。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:239`

### CR-005: 规划采用“收敛-拯救-终结”的两阶段收敛流程

当前规划流程先做首轮收敛，再识别未收敛输入项作为 rescue 集合，临时赋基础成本后再做第二轮收敛。

**Evidence**: `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:104`

---

## Impact Surface

| Area | What to check | Evidence |
| --- | --- | --- |
| 启动时机 | 若改动启动事件或线程模型，需确认规划仍先于运行期递归请求可用，且失败时仍可见日志 | `common/src/main/java/xczl/recursivecraft/RecursiveCraft.java:45` |
| 结果结构 | 若改动 `PlanningResult` 字段或键类型，需同步检查执行链路、GUI 列表和 JEI 依赖 | `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:32` |
| 候选排序依据 | 若改动成本表或配方成本公式，需检查 `TransactionCalculator` 的候选排序与 ingredient 排序结果 | `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:239` |
| NBT 方案 | 若规划层尝试升级到 `ItemStack` / NBT 粒度，需重新评估启动预计算成本与索引规模 | `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:126` |

---

## Shared Rules Dependency

| Shared Rule | Dependency | Lifted |
| --- | --- | --- |
| None | 当前没有正式上提的 shared rule | no |

---

## Uncertainties

- 当前尚未验证是否需要在资源重载或数据包动态变化后重新触发全量规划。
- 当前文档未覆盖 planner 构建失败后的恢复策略，仅确认有异常日志处理。

---

## Known Consumers

| Consumer | Usage | Evidence |
| --- | --- | --- |
| `CraftingTaskExecutor` | 读取 `pathMemo` 作为默认顶层配方选择依据 | `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:99` |
| `TransactionCalculator` | 读取 `PlanningResult` 作为候选配方和成本排序依据 | `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:48` |
| `CraftableItemList` | 从 `pathMemo.keySet()` 构建可合成物品列表 | `common/src/main/java/xczl/recursivecraft/client/CraftableItemList.java:37` |

---

## Archive Pointer

- None
