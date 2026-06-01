# NBT Phase 1 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不升级 `CraftingPlanner` 为 NBT 节点图的前提下，完成第一阶段运行时 NBT 友好链路，使输入材料匹配、虚拟扣减、正式扣减和失败语义统一。

**Architecture:** 保留 `Item` 级 planner，新增共享库存抽象层与运行时材料身份模型。先完成身份归一化、matcher、虚拟库存与执行计划桥梁，再接入 `TransactionCalculator`、`CraftingTaskExecutor`、`CraftingTransaction`，最后补测试和验证文档。

**Tech Stack:** Java, Architectury, Minecraft 1.20.1, Forge/Fabric shared common module, JUnit 5, Mockito

---

## 实施前必读

1. `docs/architecture/NBT_总体方案决议.md`
2. `docs/architecture/NBT_实施路线图.md`
3. `docs/plans/2026-05-31-shared-inventory-abstraction-design.md`
4. `docs/research/NBT_机制梳理与影响分析.md`

---

## Task 1: 建立运行时材料身份基础对象

**Files:**
- Create: `common/src/main/java/xczl/recursivecraft/runtime/material/MaterialKey.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/material/NormalizedMaterialPayload.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/material/CanonicalField.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/material/NormalizationKind.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/material/NormalizationResult.java`
- Test: `common/src/test/java/xczl/recursivecraft/runtime/material/MaterialKeyTest.java`

**Step 1: Write the failing tests**

覆盖：
- 相同 `Item` + 相同规范化载荷相等
- 相同 `Item` + 不同规范化载荷不相等
- 规范化载荷顺序稳定

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.runtime.material.MaterialKeyTest`

Expected: FAIL because classes do not exist yet

**Step 3: Implement the minimal immutable value objects**

要求：
- 全部对象不可变
- `equals/hashCode/toString` 稳定
- 不直接持有 `ItemStack`

**Step 4: Run the test to verify it passes**

Run: `gradle test --tests xczl.recursivecraft.runtime.material.MaterialKeyTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `feat: add runtime material identity primitives`

---

## Task 2: 建立身份归一化器

**Files:**
- Create: `common/src/main/java/xczl/recursivecraft/runtime/material/MaterialIdentityNormalizer.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/material/DefaultMaterialIdentityNormalizer.java`
- Test: `common/src/test/java/xczl/recursivecraft/runtime/material/DefaultMaterialIdentityNormalizerTest.java`

**Step 1: Write the failing tests**

覆盖：
- 保留功能性字段
- 顺序无关的规范化稳定
- 无法稳定归一化时返回 `UNSUPPORTED_MATERIAL_SEMANTICS`

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizerTest`

Expected: FAIL because normalizer does not exist

**Step 3: Implement the normalizer**

要求：
- 第一阶段按保守策略保留功能性字段
- 不做超前的忽略规则系统

**Step 4: Run the test to verify it passes**

Run: `gradle test --tests xczl.recursivecraft.runtime.material.DefaultMaterialIdentityNormalizerTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `feat: add material identity normalizer`

---

## Task 3: 建立 matcher 语义层

**Files:**
- Create: `common/src/main/java/xczl/recursivecraft/runtime/match/IngredientRequirement.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/match/RuntimeMatchClause.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/match/CandidateMatchKind.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/match/CandidateMatchResult.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/match/RequestLevelKind.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/match/RequestLevelResult.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/match/MaterialMatcher.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/match/DefaultMaterialMatcher.java`
- Test: `common/src/test/java/xczl/recursivecraft/runtime/match/DefaultMaterialMatcherTest.java`

**Step 1: Write the failing tests**

覆盖：
- `MATCHED`
- `REJECTED_BY_IDENTITY`
- `UNSUPPORTED_MATERIAL_SEMANTICS`
- 聚合到 `MISSING`
- 聚合到 `UNSUPPORTED`

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest`

Expected: FAIL because matcher classes do not exist

**Step 3: Implement the matcher**

要求：
- `MaterialMatcher` 不负责归一化
- 聚合规则与文档保持一致

**Step 4: Run the test to verify it passes**

Run: `gradle test --tests xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `feat: add runtime material matcher semantics`

---

## Task 4: 建立库存抽象与虚拟库存模型

**Files:**
- Create: `common/src/main/java/xczl/recursivecraft/runtime/inventory/InventorySource.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/inventory/PlayerInventorySource.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/inventory/InsertionResult.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/inventory/VirtualInventorySnapshot.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/inventory/InventoryView.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/inventory/PlayerInventoryView.java`
- Test: `common/src/test/java/xczl/recursivecraft/runtime/inventory/PlayerInventoryViewTest.java`

**Step 1: Write the failing tests**

覆盖：
- 从玩家背包生成 `VirtualInventorySnapshot`
- `Item -> MaterialKey` 辅助索引正确
- `consumeAt()` 只允许精确扣减

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.runtime.inventory.PlayerInventoryViewTest`

Expected: FAIL

**Step 3: Implement the inventory abstractions**

要求：
- 第一阶段只支持玩家背包来源
- 接口保持可扩展，但不提前引入多来源复杂性

**Step 4: Run the test to verify it passes**

Run: `gradle test --tests xczl.recursivecraft.runtime.inventory.PlayerInventoryViewTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `feat: add inventory abstraction and virtual snapshot`

---

## Task 5: 建立执行计划桥梁与事务新结构

**Files:**
- Create: `common/src/main/java/xczl/recursivecraft/runtime/execution/ResolvedConsumption.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/execution/ResolvedExecutionPlan.java`
- Create: `common/src/main/java/xczl/recursivecraft/runtime/execution/ExecutionCommitResult.java`
- Modify: `common/src/main/java/xczl/recursivecraft/data/CraftingTransaction.java`
- Test: `common/src/test/java/xczl/recursivecraft/runtime/execution/ResolvedExecutionPlanTest.java`
- Test: `common/src/test/java/xczl/recursivecraft/data/CraftingTransactionTest.java`

**Step 1: Write the failing tests**

覆盖：
- `CraftingTransaction` 输入侧使用 `MaterialKey`
- 输出侧使用 `resolvedOutputs`
- `ResolvedExecutionPlan` 可表达 slot 级消耗

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.runtime.execution.ResolvedExecutionPlanTest`

Expected: FAIL

**Step 3: Implement the bridge objects and transaction model**

要求：
- 输出侧以 `resolvedOutputs` 为单一事实来源
- 不再保留 `provides: Map<MaterialKey, Integer>` 主设计

**Step 4: Run the test to verify it passes**

Run: `gradle test --tests xczl.recursivecraft.runtime.execution.ResolvedExecutionPlanTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `feat: add execution plan bridge and material transaction model`

---

## Task 6: 改造 `InventoryUtils`

**Files:**
- Modify: `common/src/main/java/xczl/recursivecraft/utils/InventoryUtils.java`
- Test: `common/src/test/java/xczl/recursivecraft/runtime/inventory/InventoryUtilsRuntimeTest.java`

**Step 1: Write the failing tests**

覆盖：
- 不再只输出 `Map<Item, Integer>`
- 能为新运行时链路生成所需的库存快照输入

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.runtime.inventory.InventoryUtilsRuntimeTest`

Expected: FAIL

**Step 3: Implement the minimal code**

要求：
- 尽量保留兼容桥接，避免一次性打断所有旧调用点

**Step 4: Run the test to verify it passes**

Run: `gradle test --tests xczl.recursivecraft.runtime.inventory.InventoryUtilsRuntimeTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `refactor: adapt inventory utils for runtime nbt flow`

---

## Task 7: 改造 `TransactionCalculator`

**Files:**
- Modify: `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java`
- Test: `common/src/test/java/xczl/recursivecraft/core/TransactionCalculatorTest.java`
- Test: `common/src/test/java/xczl/recursivecraft/core/TransactionCalculatorNbtTest.java`

**Step 1: Write the failing tests**

覆盖：
- 同一 `Item` 不同 NBT 正确区分
- ingredient 可试探，recipe 级回退
- 默认候选在 NBT 场景下可被运行时回退

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.core.TransactionCalculatorNbtTest`

Expected: FAIL

**Step 3: Implement the runtime NBT-aware recursive flow**

要求：
- 不改 planner 为 NBT 图
- 运行时用新抽象接管虚拟库存与匹配

**Step 4: Run the tests to verify they pass**

Run: `gradle test --tests xczl.recursivecraft.core.TransactionCalculatorTest --tests xczl.recursivecraft.core.TransactionCalculatorNbtTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `feat: make transaction calculator nbt-aware at runtime`

---

## Task 8: 改造 `CraftingTaskExecutor`

**Files:**
- Modify: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java`
- Test: `common/src/test/java/xczl/recursivecraft/core/CraftingTaskExecutorNbtTest.java`

**Step 1: Write the failing tests**

覆盖：
- 预检查与正式执行共享同一 matcher 语义
- `缺料` 与 `不支持` 用户可见结果区分
- 请求级失败聚合规则正确

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.core.CraftingTaskExecutorNbtTest`

Expected: FAIL

**Step 3: Implement the executor changes**

要求：
- 使用 `RequestLevelResult`
- 不再把所有失败都压成普通缺料

**Step 4: Run the test to verify it passes**

Run: `gradle test --tests xczl.recursivecraft.core.CraftingTaskExecutorNbtTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `feat: add nbt-aware execution result handling`

---

## Task 9: 接通正式执行与 fallback 行为

**Files:**
- Modify: `common/src/main/java/xczl/recursivecraft/runtime/inventory/PlayerInventoryView.java`
- Modify: `common/src/main/java/xczl/recursivecraft/data/CraftingTransaction.java`
- Test: `common/src/test/java/xczl/recursivecraft/runtime/execution/ExecutionCommitResultTest.java`

**Step 1: Write the failing tests**

覆盖：
- revalidate 失败时无部分扣减
- 输入扣减成功后，产物插入失败时走掉落 fallback
- 单来源玩家背包路径的一致性

**Step 2: Run the test to verify it fails**

Run: `gradle test --tests xczl.recursivecraft.runtime.execution.ExecutionCommitResultTest`

Expected: FAIL

**Step 3: Implement commitExecution and execute**

要求：
- 第一阶段只承诺玩家背包单来源路径
- fallback 明确为掉落到世界

**Step 4: Run the test to verify it passes**

Run: `gradle test --tests xczl.recursivecraft.runtime.execution.ExecutionCommitResultTest`

Expected: PASS

**Step 5: Commit**

Suggested commit: `feat: finalize runtime nbt execution commit path`

---

## Task 10: 回归、文档和验证清单

**Files:**
- Modify: `docs/architecture/NBT_实施路线图.md`
- Modify: `docs/architecture/NBT_总体方案决议.md`
- Modify: `docs/plans/2026-05-31-shared-inventory-abstraction-design.md`
- Create: `docs/plans/2026-05-31-phase2-manual-verification-checklist.md`

**Step 1: Add verification checklist**

写入：
- 固定测试矩阵
- 手工验证步骤
- 项目级验证命令

**Step 2: Run full phase test set**

Run: `gradle test`

Expected: PASS

**Step 3: Perform manual verification**

验证：
- 典型 NBT 输入成功
- 错误变体不误扣
- recipe 回退后无脏状态
- 缺料 / 不支持可区分

**Step 4: Commit**

Suggested commit: `docs: record phase2 verification and close implementation`

---

## 实施约束

1. 不改 `CraftingPlanner` 为 NBT 节点图
2. 不实现目标产物 NBT 主动指定
3. 不实现外部存储正式支持
4. 不做多来源跨容器补偿事务
5. 不把“可评估扩展项”偷偷混进当前实现范围

---

## 阶段完成定义

只有在以下条件同时满足时，才算完成当前实施窗口目标：

1. `Phase 2` 的自动化测试通过
2. 固定手工验证清单完成
3. 纯 `Item` 场景未退化
4. 典型 NBT 场景已打通
5. 文档与代码状态已同步
