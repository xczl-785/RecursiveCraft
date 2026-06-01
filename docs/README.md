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
  - 当前仍在主动路径中的方案对照与评审产物
- `capabilities/`
  - capability 体系文档
- `废弃/`
  - 已退出主动阅读路径的历史材料与退役记录

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

## 四、Phase 4A 写回 / 验证窗口推荐阅读顺序

如果目标是继续 `Phase 4A` 的目标产物身份主动指定写回、复验或收口，推荐阅读：

1. `architecture/NBT_总体方案决议.md`
2. `architecture/NBT_实施路线图.md`
3. `plans/2026-05-31-target-output-nbt-implementation-plan.md`
4. `plans/2026-05-31-phase4a-handoff.md`
5. `plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md`
6. `capabilities/recursive-craft-execution.md`

说明：

- `Phase 4A` 验证窗口应先以 `phase4a-handoff` 与 checklist 中记录的 wrapper-jar launch 准备命令为准，不要默认回到工作树内 `.\gradlew` 入口
- checklist 已明确区分正常 `JEI Ctrl` gameplay 场景与 synthetic / debug-only 场景，写回时应沿用该分层

---

## 五、评审窗口推荐阅读顺序

如果目标是做实现后评审或方案对照，推荐阅读：

1. `architecture/NBT_总体方案决议.md`
2. `architecture/NBT_实施路线图.md`
3. `plans/2026-05-31-shared-inventory-abstraction-design.md`
4. `plans/2026-05-31-nbt-phase1-implementation-plan.md`
5. `reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md`
6. `capabilities/capability-index.md`

如需查看历史代码审阅、旧 Phase 2 手工验证清单或退役重构进度，请转 `废弃/README.md`。

---

## 六、当前核心文档

### 6.1 架构与路线

- `architecture/NBT_总体方案决议.md`
- `architecture/NBT_实施路线图.md`

### 6.2 设计与实施

- `plans/2026-05-31-shared-inventory-abstraction-design.md`
- `plans/2026-05-31-nbt-phase1-implementation-plan.md`
- `plans/2026-05-31-target-output-nbt-design.md`
- `plans/2026-05-31-target-output-nbt-implementation-plan.md`
- `plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md`
- `plans/2026-05-31-phase4a-handoff.md`
- `plans/2026-05-31-phase4a-debug-fixture-design.md`
- `plans/2026-05-31-phase4a-debug-fixture-implementation-plan.md`

### 6.3 调研与背景

- `research/NBT_支持调研报告.md`
- `research/NBT_机制梳理与影响分析.md`
- `research/存储支持调研报告.md`

### 6.4 当前评审与能力索引

- `reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md`
- `capabilities/capability-index.md`

### 6.5 历史归档（按需）

- `废弃/README.md`

---

## 七、当前结论

以当前文档集状态看，`Phase 2` 第一阶段实现与 `Phase 4A` 第一批目标产物身份实现都已有明确入口；当前需要优先注意的是：

1. `Phase 4A` 自动化验证已具备固定命令与测试面
2. `JEI Ctrl` 的真实运行时手工验证仍待完成
3. 当前已提供 red / blue debug fixture 作为默认 runtime verification 样例
4. 在手工复验补齐前，不应把 `Phase 4A` 写成“已完全完成”
