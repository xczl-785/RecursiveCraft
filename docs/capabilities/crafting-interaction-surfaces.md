# crafting-interaction-surfaces

## Quick Read

- **id**: `crafting-interaction-surfaces`
- **name**: 合成交互入口与界面
- **summary**: 提供方块模式、手持模式、菜单、客户端界面与 JEI 触发面，让玩家能够浏览可合成目标并发送递归合成请求。
- **scope**: 包含方块与手持物品打开菜单、菜单上下文、客户端物品列表与分页、JEI 的递归/原版分流；不包含全局规划算法和最终事务执行规则。
- **entry_points**:
  - `RecursiveCrafterBlock.use()`
  - `HandheldCrafterItem.use()`
  - `RecursiveCrafterMenu`
  - `RecursiveCrafterScreen.onExecutePressed()`
  - `RecursiveCraftTransferHandler.transferRecipe()`
- **shared_with**:
  - None
- **check_on_change**:
  - 方块模式和手持模式是否仍区分上下文
  - GUI 列表是否仍以 planner 结果为基底，并在 JEI runtime 可用时补充变体目标
  - JEI Ctrl 与非 Ctrl 的分流是否仍保持
- **last_verified**: 2026-06-03

---

## Capability Summary

该能力负责暴露玩家与递归合成系统交互的表面。当前项目提供两种原生入口：方块模式与手持模式；两者共用同一菜单与屏幕，但通过 `isHandheld` / `BlockPos` 区分上下文。同时，JEI 集成提供了一个特殊入口：按住 Ctrl 时接管为递归合成，不按 Ctrl 时继续走原版配方摆放与材料检查。

当前界面层不维护自己的库存模型，只负责选择目标、输入数量，并把目标身份相关元数据发送给网络/原版容器逻辑。

---

## Entries

| Entry | Trigger | Evidence | Notes |
| --- | --- | --- | --- |
| 方块模式 | 右键递归合成方块 | `common/src/main/java/xczl/recursivecraft/block/RecursiveCrafterBlock.java:32` | 打开菜单并传入 `BlockPos` |
| 手持模式 | 使用手持合成器物品 | `common/src/main/java/xczl/recursivecraft/item/HandheldCrafterItem.java:25` | 打开菜单并传入 `null` 位置 |
| 菜单上下文 | 客户端/服务端构造 `RecursiveCrafterMenu` | `common/src/main/java/xczl/recursivecraft/menu/RecursiveCrafterMenu.java:23` | 用 `isHandheld` 区分上下文 |
| GUI 执行按钮 | 点击屏幕执行按钮 | `common/src/main/java/xczl/recursivecraft/client/RecursiveCrafterScreen.java:113` | 发送递归合成网络包，可携带目标身份与顶层展示输入 |
| JEI 递归入口 | 在 JEI 中按住 Ctrl 传输配方 | `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:75` | 发送带 `recipeId` 的递归请求 |

---

## Current Rules

### CR-001: 方块模式与手持模式共用同一菜单，但上下文不同

方块模式通过 `BlockPos` 构造菜单，手持模式通过 `null` 位置构造菜单；`RecursiveCrafterMenu` 内部以 `isHandheld` 区分有效性检查语义。

**Evidence**: `common/src/main/java/xczl/recursivecraft/menu/RecursiveCrafterMenu.java:40`

### CR-002: GUI 的可合成物品列表以 planner 结果为基底，并可在 JEI runtime 可用时补充变体目标

`RecursiveCrafterScreen` 初始化时调用 `CraftableItemList.tryLoadFromPlanner()`；该列表仍以 `pathMemo.keySet()` 作为基础可合成物品源，但在 JEI runtime 可用时，会从 JEI 当前 crafting recipe 视图补充同一 `Item` 的具体展示变体与顶层展示输入。

**Evidence**: `common/src/main/java/xczl/recursivecraft/client/CraftableItemList.java:40`

### CR-003: GUI 执行按钮发送的是当前选中目标，而不只是不带身份的 `Item + amount`

客户端屏幕不会在本地模拟递归事务；点击后会把当前选中的 `CraftableTarget` 转成 `C2SExecuteCraftPacket`，其中可选携带 `forcedRecipeId`、`TargetOutputSpec` 和 `displayedIngredients`。

**Evidence**: `common/src/main/java/xczl/recursivecraft/client/RecursiveCrafterScreen.java:123`

### CR-004: JEI 入口区分“原版摆放”和“递归合成接管”

按住 Ctrl 时，JEI 发送递归合成请求并附带 `recipeId`；不按 Ctrl 且容器为原版 2x2/3x3 时，继续走原版 `ServerboundPlaceRecipePacket`。

**Evidence**: `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:73`

### CR-005: 当前界面层不维护外部容器或方块库存

菜单只布局玩家背包槽位，界面也只围绕目标物品选择与数量输入展开，未建立方块内部库存或外部存储可视模型。

**Evidence**: `common/src/main/java/xczl/recursivecraft/menu/RecursiveCrafterMenu.java:56`

---

## Impact Surface

| Area | What to check | Evidence |
| --- | --- | --- |
| 上下文区分 | 若改动方块模式/手持模式，需确认 `isHandheld`、`stillValid` 与菜单打开路径保持一致 | `common/src/main/java/xczl/recursivecraft/menu/RecursiveCrafterMenu.java:80` |
| 可合成列表来源 | 若 planner 结果结构或 JEI runtime 变体采集逻辑变更，需同时检查 `CraftableItemList`、屏幕刷新与变体去重逻辑 | `common/src/main/java/xczl/recursivecraft/client/CraftableItemList.java:40` |
| JEI 分流 | 若改动 JEI 逻辑，需确认 Ctrl 递归合成仍不破坏原版摆放路径 | `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:91` |
| 存储支持 | 若未来方块模式接入外部容器，菜单与屏幕上下文很可能需要新增来源展示或模式提示 | `common/src/main/java/xczl/recursivecraft/block/RecursiveCrafterBlock.java:32` |

---

## Shared Rules Dependency

| Shared Rule | Dependency | Lifted |
| --- | --- | --- |
| None | 当前没有正式上提的 shared rule | no |

---

## Uncertainties

- 当前未确认未来是否需要让手持模式也接入外部存储来源。
- 当前未确认 JEI 递归入口是否应暴露数量控制，而不只是 `1` / `64` 两档。

---

## Known Consumers

| Consumer | Usage | Evidence |
| --- | --- | --- |
| `RecursiveCrafterBlock` | 方块模式使用菜单与界面能力 | `common/src/main/java/xczl/recursivecraft/block/RecursiveCrafterBlock.java:63` |
| `HandheldCrafterItem` | 手持模式使用菜单与界面能力 | `common/src/main/java/xczl/recursivecraft/item/HandheldCrafterItem.java:31` |
| `RecursiveCraftTransferHandler` | JEI 集成通过该能力与执行链路衔接 | `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:58` |

---

## Archive Pointer

- None
