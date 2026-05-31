# 目标产物 NBT 主动指定 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为递归合成链路增加“目标产物 NBT 主动指定”能力，并优先打通后端契约与 JEI 递归入口。

**Architecture:** 保持 `CraftingPlanner` 为 `Item` 级粗规划，不升级 planner。后端增加 `TargetOutputSpec` 入口契约，服务端归一化为 `desiredOutputKey` 后交给运行时链路。第一批只落后端与 JEI，命令和自定义 GUI 后续分阶段补入。

**Tech Stack:** Minecraft 1.20.1, Architectury, Forge/Fabric shared common module, existing runtime material identity pipeline

---

## 范围说明

本计划分为三层：

1. `Phase 4A`
   - 本次推荐直接实施
   - 后端契约 + JEI
2. `Phase 4B`
   - 命令入口扩展
3. `Phase 4C`
   - 自定义 GUI 扩展

本计划默认先执行 `Phase 4A`。

执行前置条件：

1. `Phase 3` 方案代码对照与收口报告已完成
2. `Phase 3` 固定自动化验证已重新执行
3. `Phase 2/3` 手工验证清单与复验记录已补齐

若以上条件未满足，本计划不应直接开工，而应先完成 `Phase 3` 收口。

---

### Task 1: 定义目标输出契约对象

**Files:**
- Create: `common/src/main/java/xczl/recursivecraft/runtime/material/TargetOutputSpec.java`
- Create: `common/src/test/java/xczl/recursivecraft/runtime/material/TargetOutputSpecTest.java`

**Step 1: Write the failing test**

覆盖：

- 可从 `Item + CompoundTag` 构造
- 空 item / 非法参数拒绝
- 可导出目标模板栈

**Step 2: Run test to verify it fails**

Run:

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.runtime.material.TargetOutputSpecTest"
```

Expected:

- `BUILD FAILED`

**Step 3: Write minimal implementation**

实现：

- `item`
- `@Nullable CompoundTag tag`
- `toTemplateStack()`

**Step 4: Run test to verify it passes**

Run the same command.

**Step 5: Commit**

```powershell
git add common/src/main/java/xczl/recursivecraft/runtime/material/TargetOutputSpec.java common/src/test/java/xczl/recursivecraft/runtime/material/TargetOutputSpecTest.java
git commit -m "feat: add target output spec primitive"
```

---

### Task 2: 升级网络包契约

**Files:**
- Modify: `common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java`
- Modify: `common/src/main/java/xczl/recursivecraft/networking/PacketHandler.java`
- Create: `common/src/test/java/xczl/recursivecraft/networking/C2SExecuteCraftPacketTest.java`

**Step 1: Write the failing test**

覆盖：

- 旧格式兼容：仅 `item + amount + recipeId`
- 新格式：携带 `TargetOutputSpec`
- 编解码往返后结构一致
- `targetItem` 与 `TargetOutputSpec.item` 冲突时服务端拒绝

**Step 2: Run test to verify it fails**

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest"
```

Expected:

- `BUILD FAILED`

**Step 3: Write minimal implementation**

实现：

- 在 packet 中增加可选 `TargetOutputSpec`
- 编码顺序显式加入 presence flag
- 解码保持向后兼容同版本协议

**Step 4: Run test to verify it passes**

Run the same command.

**Step 5: Commit**

```powershell
git add common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java common/src/main/java/xczl/recursivecraft/networking/PacketHandler.java common/src/test/java/xczl/recursivecraft/networking/C2SExecuteCraftPacketTest.java
git commit -m "feat: extend craft packet with target output spec"
```

---

### Task 3: 升级执行器入口与成功判定

**Files:**
- Modify: `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java`
- Create: `common/src/test/java/xczl/recursivecraft/core/CraftingTaskExecutorTargetOutputTest.java`

**Step 1: Write the failing test**

覆盖：

- 无 `TargetOutputSpec` 时旧行为不变
- 有 `TargetOutputSpec` 时错误 NBT 输出不得误判为成功
- `UNSUPPORTED` 与 `MISSING` 路径正确
- `targetItem` 与 `TargetOutputSpec.item` 冲突时返回 `invalid_request`

**Step 2: Run test to verify it fails**

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest"
```

Expected:

- `BUILD FAILED`

**Step 3: Write minimal implementation**

实现：

- 扩展 `tryExecute(...)` 参数
- 将 `TargetOutputSpec` 归一化为 `desiredOutputKey`
- 成功判定从纯 `Item` 数量升级为：
  - 无 spec 时：旧逻辑
  - 有 spec 时：按 `resolvedOutputs` 身份统计

**Step 4: Run test to verify it passes**

Run the same command.

**Step 5: Commit**

```powershell
git add common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java common/src/test/java/xczl/recursivecraft/core/CraftingTaskExecutorTargetOutputTest.java
git commit -m "feat: enforce target output identity in executor"
```

---

### Task 4: 升级 `TransactionCalculator` 公开入口

**Files:**
- Modify: `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java`
- Modify: `common/src/test/java/xczl/recursivecraft/core/TransactionCalculatorNbtTest.java`

**Step 1: Write the failing test**

覆盖：

- 公开入口能够接受 `desiredOutputKey`
- recipe 输出身份不匹配时被过滤
- 多 recipe 候选时能选中正确变体

**Step 2: Run test to verify it fails**

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest"
```

Expected:

- 与目标输出相关的新测试失败

**Step 3: Write minimal implementation**

实现：

- 给 `calculate(...)` 增加新的 overload
- 把 `desiredOutputKey` 从公开入口传到 `calculateRecursive(...)`
- 保持无 spec 的旧入口语义不变

**Step 4: Run test to verify it passes**

Run the same command.

**Step 5: Commit**

```powershell
git add common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java common/src/test/java/xczl/recursivecraft/core/TransactionCalculatorNbtTest.java
git commit -m "feat: expose desired output identity to calculator"
```

---

### Task 5: 打通 JEI 递归入口

**Files:**
- Modify: `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java`
- Modify: `common/src/test/java/xczl/recursivecraft/core/CraftingTaskExecutorTargetOutputTest.java`

**Step 1: Write the failing test**

覆盖：

- JEI 递归路径能够把展示输出身份传下去
- 同 `Item` 多 NBT 输出 recipe 不误落到错误变体

**Step 2: Run test to verify it fails**

用相关目标测试命令验证红灯。

**Step 3: Write minimal implementation**

实现：

- JEI Ctrl 递归分支在发包时附带 `TargetOutputSpec`
- 仍保留 `recipeId`

**Step 4: Run test to verify it passes**

执行相关测试。

**Step 5: Commit**

```powershell
git add common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java common/src/test/java/xczl/recursivecraft/core/CraftingTaskExecutorTargetOutputTest.java
git commit -m "feat: send exact JEI output identity for recursive crafting"
```

---

### Task 6: Phase 4A 收口验证与文档写回

**Files:**
- Modify: `docs/architecture/NBT_实施路线图.md`
- Modify: `docs/architecture/NBT_总体方案决议.md`
- Modify: `docs/capabilities/recursive-craft-execution.md`
- Modify: `docs/README.md`
- Optionally modify: `docs/reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md`

**Step 1: Add verification checklist**

写回：

- 目标输出指定的成功/失败矩阵
- JEI 路径覆盖
- 与旧行为兼容边界
- `targetItem/spec.item` 不变量

**Step 2: Run focused verification**

建议命令：

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest" --tests "xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest"
```

Expected:

- `BUILD SUCCESSFUL`

**Step 3: Run manual / integration verification**

至少执行并记录：

1. JEI 展示输出变体 A，递归执行产出 A
2. JEI 展示输出变体 B，递归执行不得误产出 A
3. 强制 `recipeId` 与目标身份冲突时不误判成功
4. 旧无 spec 路径不退化
5. `targetItem/spec.item` 冲突时服务端直接拒绝

**Step 4: Write back current truth**

更新：

- 路线图中该扩展的状态
- capability 当前规则
- docs 索引

**Step 5: Commit**

```powershell
git add docs/architecture/NBT_实施路线图.md docs/architecture/NBT_总体方案决议.md docs/capabilities/recursive-craft-execution.md docs/README.md
git commit -m "docs: record target output nbt phase4a plan"
```

---

## 后续阶段

### Phase 4B: 命令入口

建议单独立项，不混入 `Phase 4A`。

最小任务：

1. 增加可选 SNBT 参数
2. 服务端解析与错误提示
3. 命令测试

### Phase 4C: 自定义 GUI

建议单独立项，不混入 `Phase 4A`。

最小任务：

1. 设计输出变体发现机制
2. 设计变体展示与选择 UI
3. 处理搜索、收藏与分页

---

## 收口标准

若只执行 `Phase 4A`，达到以下条件即可判定该批次完成：

1. 后端契约已支持目标输出身份
2. JEI 递归入口已能传递精确输出身份
3. 成功判定不再只按 `Item` 数量
4. `UNSUPPORTED / MISSING` 行为矩阵可验证
5. 旧无 spec 路径不退化
6. `targetItem/spec.item` 不变量已被自动化和手工验证覆盖
