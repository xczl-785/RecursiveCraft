# RecursiveCraft Phase 4A 运行时复验准备转接指令

> 目标分支：`1.20.1`
> 文档日期：2026-05-31
> 当前定位：`Phase 4A` 第一批代码与自动化验证已落地，下一窗口进入运行时复验准备 / 手工复验窗口
> 用途：供下一个窗口直接接手 `JEI Ctrl` 运行时复验准备，不再误回到实现启动阶段

---

## 一、下一窗口必须先读的文档

请严格按以下顺序阅读：

1. [docs/architecture/NBT_总体方案决议.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/architecture/NBT_总体方案决议.md:1)
2. [docs/architecture/NBT_实施路线图.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/architecture/NBT_实施路线图.md:1)
3. [docs/reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md:1)
4. [docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md:1)
5. [docs/capabilities/recursive-craft-execution.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/capabilities/recursive-craft-execution.md:1)
6. [docs/plans/2026-05-31-target-output-nbt-design.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/plans/2026-05-31-target-output-nbt-design.md:1)
7. [docs/plans/2026-05-31-target-output-nbt-implementation-plan.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/plans/2026-05-31-target-output-nbt-implementation-plan.md:1)
8. [docs/README.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/README.md:1)

说明：

- 前三份文档用于恢复当前真相与阶段位置
- 第 4、5 份文档用于直接执行本轮运行时复验准备
- 第 6、7 份文档用于在需要时回看设计边界与实现输入
- 最后一份文档用于恢复文档入口

---

## 二、整体规划总览

当前 NBT 主题的整体路线已经明确：

1. 保留 `CraftingPlanner` 的 `Item` 级粗规划
2. 运行时执行链承担 NBT 精确匹配与精确扣减
3. 共享库存抽象层作为统一底座
4. 当前代码已完成：
   - `Phase 2` 第一阶段运行时 NBT 主链
   - `Phase 3` 方案代码对照与主要桥接收口
5. 当前接下来进入：
   - `Phase 4A` 目标产物 NBT 主动指定的运行时复验准备与手工复验

---

## 三、当前阶段在整体规划中的位置

当前状态不是“还在做 `Phase 3`”或“还未开始 `Phase 4A` 实现”，而是：

- `Phase 3` 的规划内容已经完成
- `Phase 4A` 第一批代码与目标自动化验证已经完成
- 下一窗口应视为正式进入 `Phase 4A` 运行时复验窗口

但有一个前置提醒：

- `Phase 2/3` 的手工验证记录仍然是剩余收口项
- `Phase 4A` 的真实运行时 `JEI Ctrl` 复验当前仍未执行完成
- 在补齐这些记录前，不应宣称 `Phase 4A` 已整体收口

---

## 四、当前已确认的运行时复验准备事实

推荐平台：

1. 优先 `Forge`
2. `Fabric` 作为回退路径

当前环境中已验证成功的命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 forge:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 fabric:configureClientLaunch
```

已确认事实：

1. `forge:configureClientLaunch` 已成功
2. `fabric:configureClientLaunch` 已成功
3. 在当前环境中，应使用仓库根目录 wrapper jar + `GradleWrapperMain` 形式，不应把工作树内的 `.\gradlew` / `.\gradlew.bat` 作为首选入口
4. 上述结论仅说明“运行时复验准备命令可执行”，不代表 gameplay 手工复验已经完成
5. 当前仓库已补入 `recursivecraft:debug/handheld_crafter_red|blue` 作为默认 runtime verification fixture

---

## 五、手工复验场景分层

正常 gameplay / `JEI Ctrl` 可达：

1. `2.1` 旧路径不退化
2. `2.2` JEI 展示变体 A 正确产出
3. `2.3` JEI 展示变体 B 不误产出 A

其中：

1. `2.2 / 2.3` 的前提是存在“同一 `Item`、多个不同 NBT 输出变体”的 JEI 输出样例
2. 当前默认样例为：
   - `recursivecraft:debug/handheld_crafter_red`
   - `recursivecraft:debug/handheld_crafter_blue`
3. 若该样例在运行时未出现，应优先排查 recipe / serializer / JEI 装载，而不是直接判为执行链失败

仅 synthetic / debug 可达：

1. `2.4` 强制 `recipeId` 与目标身份冲突
2. `2.5` `targetItem` 与 `TargetOutputSpec.item` 冲突

组合验证：

1. `2.6` 中的 `MISSING` 子场景可能通过 gameplay 或 debug 路径观察
2. `2.6` 中的 `UNSUPPORTED` 子场景更应视为 synthetic / debug-path 复验
3. 不要把 `2.4 / 2.5 / 2.6` 整体写成“普通 JEI Ctrl 点击流即可覆盖”

---

## 六、下一窗口的直接任务

下一窗口的直接任务不是重新开做实现，而是：

1. 复核 [docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md:1) 中的场景分层
2. 先使用 `Forge` 跑运行时复验准备命令
3. 若 `Forge` 受阻，再使用 `Fabric` 回退
4. 先使用 `recursivecraft:debug/handheld_crafter_red|blue` 完成 `2.2 / 2.3`
5. 仅在具备改包、注入或 debug harness 条件时再推进 `2.4 / 2.5 / 2.6`
6. 任何写回都必须明确区分“已执行的 gameplay 复验”和“仅由 synthetic / debug 路径覆盖的场景”

下一窗口必须继续遵守的边界：

1. 不升级 `CraftingPlanner` 为 NBT 图
2. 不顺手补做命令增强版或 GUI 选择器
3. 不把“准备命令成功”写成“运行时复验已通过”
4. 不把全部 checklist 项目都描述成正常 JEI 点击流

---

## 七、远期规划

当前远期规划已经明确，不要在 `Phase 4A` 中混入：

1. `Phase 4B`：命令入口支持目标产物 NBT 主动指定
2. `Phase 4C`：自定义 GUI 支持输出变体展示与选择
3. 原版相邻容器支持
4. 第三方存储兼容
5. planner 是否需要局部 NBT 感知评估

其中存储路线已经定了优先顺序：

1. 先接官方/原版相邻容器
2. 再评估第三方 Mod 存储单元

也就是说：

- **官方存储单元接入** 是远期规划中的较近项
- **第三方 Mod 存储单元接入** 是更后面的扩展项

---

## 八、当前仓库事实

当前远端分支：

- `origin/1.20.1`

当前最新已推送提交包括：

1. `0c73c7d`
   - `refactor: route runtime request aggregation through matcher`
2. `58cda14`
   - `docs: plan target output nbt expansion`

当前工作区应保持干净后再启动下一轮运行时复验或写回。

---

## 九、下一窗口启动时的直接指令

下一窗口启动后，请直接执行：

1. 进入 `e:\Program\RecursiveCraft\.worktrees\backport-1.20.1`
2. 按本文档第一节顺序阅读
3. 先执行本文件第四节中的 `forge:configureClientLaunch`
4. 若 `Forge` 受阻，再执行 `fabric:configureClientLaunch`
5. 先尝试拿到同一 `Item` 多 NBT JEI 输出样例，再推进 checklist `2.2 / 2.3`
6. 只有在具备 synthetic / debug 手段时，才推进 checklist `2.4 / 2.5 / 2.6`
7. 写回复验结果时，明确标注 `gameplay` 与 `synthetic-debug`，且不要宣称 `Phase 4A` 已整体完成

不要在启动后回退到“从 `Task 1` 开始重新实现”；除非代码事实与现有文档出现直接冲突，否则应把重点放在运行时复验准备与证据补齐。
