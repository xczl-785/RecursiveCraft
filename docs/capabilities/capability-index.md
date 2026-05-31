# Capability Index

> 用途：能力发现层。仅用于帮助后续窗口快速定位 capability，不承载当前规则或影响面正文。

| id | name | summary | entry_points | shared_with | path |
| --- | --- | --- | --- | --- | --- |
| `crafting-planning-engine` | 递归合成规划引擎 | 在服务端启动阶段扫描 crafting 配方并预计算每个产物的理论最优路径与成本。 | `RecursiveCraft.init()` -> `LifecycleEvent.SERVER_STARTING` -> `CraftingPlanner.buildOptimalPathTree()` | None | `docs/capabilities/crafting-planning-engine.md` |
| `recursive-craft-execution` | 递归合成执行链路 | 将命令、网络包和 JEI 递归触发统一路由到事务计算与净变化执行。 | `/craft_recursive`、`C2SExecuteCraftPacket.handle()`、`CraftingTaskExecutor.tryExecute()` | None | `docs/capabilities/recursive-craft-execution.md` |
| `crafting-interaction-surfaces` | 合成交互入口与界面 | 负责方块模式、手持模式、菜单、客户端界面与 JEI 触发面的交互行为。 | `RecursiveCrafterBlock.use()`、`HandheldCrafterItem.use()`、`RecursiveCrafterScreen.onExecutePressed()`、`RecursiveCraftTransferHandler.transferRecipe()` | None | `docs/capabilities/crafting-interaction-surfaces.md` |
