# RecursiveCraft 文档索引

> 当前分支：`1.20.1`
> 用途：为后续规划窗口、实施窗口、评审窗口提供统一文档入口

---

## 一、当前主动路径

`docs/` 目录当前按用途分为以下类别：

- `README.md`
  - 主入口与阅读路由
- `HANDOFF.md`
  - 当前转接、阶段状态与直接任务
- `architecture/`
  - 高层决议、共享架构、实施路线图、存储适配路线
- `Phase4A_运行时验证.md`
  - 当前 `Phase 4A` 正式复验文档
- `capabilities/`
  - capability 体系文档
- `reviews/`
  - 当前仍保留在主动路径中的方案-代码对照材料
- `research/`
  - 调研、机制梳理、背景证据
- `废弃/`
  - 已退出主动阅读路径的历史材料与退役记录

`docs/plans/` 已退出主动路径；原计划型文档已统一迁入 `docs/废弃/plans/` 作为历史档案。

---

## 二、Agent-First 阅读顺序

### 2.1 继续当前 NBT / `Phase 4A` 主线

1. `README.md`
2. `HANDOFF.md`
3. `architecture/NBT_总体方案决议.md`
4. `architecture/NBT_实施路线图.md`
5. `architecture/共享库存抽象层.md`
6. `capabilities/recursive-craft-execution.md`
7. `Phase4A_运行时验证.md`

### 2.2 规划后续存储适配

1. `architecture/NBT_总体方案决议.md`
2. `architecture/NBT_实施路线图.md`
3. `architecture/共享库存抽象层.md`
4. `architecture/存储支持与适配路线图.md`
5. `capabilities/crafting-interaction-surfaces.md`
6. `research/存储支持调研报告.md`

### 2.3 回看证据与背景

1. `architecture/NBT_总体方案决议.md`
2. `architecture/NBT_实施路线图.md`
3. `architecture/共享库存抽象层.md`
4. `reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md`
5. `research/NBT_支持调研报告.md`
6. `research/NBT_机制梳理与影响分析.md`

如需查看历史计划、旧 handoff、旧实现计划或历史验证材料，请转 `废弃/README.md`。

---

## 三、当前核心文档

### 3.1 架构与路线

- `architecture/NBT_总体方案决议.md`
- `architecture/NBT_实施路线图.md`
- `architecture/共享库存抽象层.md`
- `architecture/存储支持与适配路线图.md`

### 3.2 转接与运行时验证

- `HANDOFF.md`
- `Phase4A_运行时验证.md`

### 3.3 能力文档

- `capabilities/capability-index.md`
- `capabilities/recursive-craft-execution.md`
- `capabilities/crafting-interaction-surfaces.md`
- `capabilities/crafting-planning-engine.md`

### 3.4 调研与背景

- `research/NBT_支持调研报告.md`
- `research/NBT_机制梳理与影响分析.md`
- `research/存储支持调研报告.md`

### 3.5 当前评审

- `reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md`

### 3.6 历史归档（按需）

- `废弃/README.md`

---

## 四、当前结论

当前应按以下结论理解仓库文档状态：

1. `1.20.1` 是当前真实主线，不再需要从计划文档恢复这个事实。
2. `Phase 2` 第一阶段运行时 NBT 主链已是当前实现真相。
3. `Phase 4A` 的代码与自动化验证已到位，但真实运行时手工复验仍待完成。
4. 当前默认 runtime verification 样例是 red / blue debug fixture。
5. 官方存储适配路线已经提升为架构文档：
   - 先方块模式相邻原版容器
   - 后第三方存储接口适配
