# RecursiveCraft 文档索引

> 当前分支：`1.20.1`
> 用途：为后续规划窗口、实施窗口、评审窗口提供统一文档入口

---

## 一、分类说明

`docs/` 目录当前按用途分为以下类别：

- `architecture/`
  - 高层决议、阶段边界、实施路线图
- `plans/`
  - 面向实现的正式设计与实施计划
- `research/`
  - 调研、机制梳理、问题分析
- `reviews/`
  - 代码审阅类产物
- `refactoring/`
  - 既有重构记录与历史进展
- `capabilities/`
  - capability 体系文档

---

## 二、实施窗口推荐阅读顺序

如果目标是直接进入 NBT 第一阶段实现，推荐按以下顺序阅读：

1. `architecture/NBT_总体方案决议.md`
2. `architecture/NBT_实施路线图.md`
3. `plans/2026-05-31-shared-inventory-abstraction-design.md`
4. `plans/2026-05-31-nbt-phase1-implementation-plan.md`
5. `research/NBT_机制梳理与影响分析.md`

说明：

- 前三份文档用于确定目标、边界与设计输入
- 第四份文档用于直接指导实施窗口分任务落地
- 第五份文档用于补运行机制与影响背景

---

## 三、规划窗口推荐阅读顺序

如果目标是继续扩展方案、讨论后续阶段或评估外部存储路线，推荐阅读：

1. `architecture/NBT_总体方案决议.md`
2. `architecture/NBT_实施路线图.md`
3. `research/NBT_支持调研报告.md`
4. `research/存储支持调研报告.md`
5. `research/NBT_机制梳理与影响分析.md`

---

## 四、评审窗口推荐阅读顺序

如果目标是做实现后评审或方案对照，推荐阅读：

1. `architecture/NBT_总体方案决议.md`
2. `architecture/NBT_实施路线图.md`
3. `plans/2026-05-31-shared-inventory-abstraction-design.md`
4. `plans/2026-05-31-nbt-phase1-implementation-plan.md`
5. `reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md`
6. `reviews/2026-05-31-NBT_Phase2_手工验证清单.md`
7. `capabilities/capability-index.md`

---

## 五、当前核心文档

### 5.1 架构与路线

- `architecture/NBT_总体方案决议.md`
- `architecture/NBT_实施路线图.md`

### 5.2 设计与实施

- `plans/2026-05-31-shared-inventory-abstraction-design.md`
- `plans/2026-05-31-nbt-phase1-implementation-plan.md`
- `plans/2026-05-31-target-output-nbt-design.md`
- `plans/2026-05-31-target-output-nbt-implementation-plan.md`
- `plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md`

### 5.3 调研与背景

- `research/NBT_支持调研报告.md`
- `research/NBT_机制梳理与影响分析.md`
- `research/存储支持调研报告.md`

### 5.4 代码现状与历史

- `reviews/Code_Review_Report.md`
- `reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md`
- `reviews/2026-05-31-NBT_Phase2_手工验证清单.md`
- `refactoring/Refactoring_Progress.md`

---

## 六、当前结论

以当前文档集状态看，实施窗口已经不需要再自行组织上下文；只要按上面的推荐顺序读取，就可以直接进入第一阶段 NBT 方案实现。
