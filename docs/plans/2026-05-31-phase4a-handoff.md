# RecursiveCraft NBT 主题下一窗口转接指令

> 目标分支：`1.20.1`
> 文档日期：2026-05-31
> 当前定位：从 `Phase 3` 收口完成，切入 `Phase 4A`
> 用途：供下一个窗口直接接手，不再重复整理上下文

---

## 一、下一窗口必须先读的文档

请严格按以下顺序阅读：

1. [docs/architecture/NBT_总体方案决议.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/architecture/NBT_总体方案决议.md:1)
2. [docs/architecture/NBT_实施路线图.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/architecture/NBT_实施路线图.md:1)
3. [docs/reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md:1)
4. [docs/plans/2026-05-31-target-output-nbt-design.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/plans/2026-05-31-target-output-nbt-design.md:1)
5. [docs/plans/2026-05-31-target-output-nbt-implementation-plan.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/plans/2026-05-31-target-output-nbt-implementation-plan.md:1)
6. [docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md:1)
7. [docs/capabilities/recursive-craft-execution.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/capabilities/recursive-craft-execution.md:1)
8. [docs/README.md](e:/Program/RecursiveCraft/.worktrees/backport-1.20.1/docs/README.md:1)

说明：

- 前三份文档用于恢复当前真相与阶段位置
- 中间三份文档用于直接执行 `Phase 4A`
- 最后两份文档用于恢复 capability 规则与文档入口

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
   - `Phase 4A` 目标产物 NBT 主动指定

---

## 三、当前阶段在整体规划中的位置

当前状态不是“还在做 Phase 3”，而是：

- `Phase 3` 的规划内容已经完成
- 代码侧主要收口也已经完成
- 下一窗口应视为正式进入 `Phase 4A` 实施窗口

但有一个前置提醒：

- `Phase 2/3` 的手工验证记录仍然是剩余收口项
- 若执行 `Phase 4A` 时需要严格遵守前置门槛，应优先补齐记录或至少同步建立可执行复验记录机制

---

## 四、下阶段任务内容

下一窗口的直接任务是：

- **执行 `Phase 4A：后端契约 + JEI 递归入口`**

按实施计划，优先顺序应为：

1. 定义 `TargetOutputSpec`
2. 扩展 `C2SExecuteCraftPacket`
3. 扩展 `CraftingTaskExecutor`
4. 扩展 `TransactionCalculator` 公开入口
5. 接入 `RecursiveCraftTransferHandler`
6. 补自动化测试
7. 跑 `Phase 4A` 手工验证清单
8. 文档写回

必须遵守的核心约束：

1. 不升级 `CraftingPlanner` 为 NBT 图
2. 不顺手做命令增强版
3. 不顺手做自定义 GUI 变体选择器
4. 不碰外部存储

---

## 五、下一窗口的工作模式

下一窗口不要单线程硬推，必须继续采用当前已验证的推进模式：

1. **主窗口负责调度**
   - 负责控制阶段边界
   - 负责校验实现是否仍与规划一致
   - 负责整合测试、文档与 push

2. **实施窗口独立推进**
   - 使用受限写面
   - 严格按 TDD
   - 每轮只改一个明确子任务

3. **Review 窗口独立审阅**
   - 使用 `gpt-5.4`
   - 只审当前未提交 diff
   - findings-first

4. **保持上下文的连续、隔离与纯粹**
   - 连续：主窗口维护总体进度与文档真相
   - 隔离：实施与 review 不混在同一条推理链
   - 纯粹：每一轮只解决当前任务，不顺手扩张范围

推荐执行节奏：

1. 主窗口先读计划并锁定当前子任务
2. 开实施窗口做该子任务
3. 主窗口本地复跑最小相关测试
4. 开 review 窗口审该轮 diff
5. 主窗口修补 review finding
6. 再做文档写回、commit、push

---

## 六、远期规划

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

## 七、当前仓库事实

当前远端分支：

- `origin/1.20.1`

当前最新已推送提交包括：

1. `0c73c7d`
   - `refactor: route runtime request aggregation through matcher`
2. `58cda14`
   - `docs: plan target output nbt expansion`

当前工作区应保持干净后再启动下一轮实施。

---

## 八、下一窗口启动时的直接指令

下一窗口启动后，请直接执行：

1. 进入 `e:\Program\RecursiveCraft\.worktrees\backport-1.20.1`
2. 按本文档第一节顺序阅读
3. 以 `docs/plans/2026-05-31-target-output-nbt-implementation-plan.md` 为唯一实施计划基线
4. 从 `Task 1: 定义目标输出契约对象` 开始
5. 使用“主窗口调度 + 实施窗口 + review 窗口”模式推进

不要在启动后重新讨论大方向，除非代码事实与现有文档出现直接冲突。
