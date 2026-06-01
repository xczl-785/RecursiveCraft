# Phase 4A Debug Fixture Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 `Phase 4A` 的 `JEI Ctrl` 运行时复验补一条最小可验证的 debug fixture 样例链，闭合 checklist `2.2 / 2.3`。

**Architecture:** 复用 `recursivecraft:handheld_crafter` 作为输出物，新增一个最小自定义 crafting recipe/serializer，使两条真实 recipe 能输出同一 `Item` 的两个稳定 tag 变体。保持 planner 仍为 `Item` 级，通过提高 debug recipe 成本来避免默认路径漂移。

**Tech Stack:** Minecraft 1.20.1, Architectury, Forge/Fabric shared common module, custom `CraftingRecipe` serializer, existing TargetOutputSpec runtime pipeline

---

## 范围说明

本计划只覆盖：

1. `Phase 4A` runtime verification fixture
2. 两条真实 recipe 的输出身份样例
3. 对应自动化验证与文档写回

本计划不覆盖：

1. dev-only 条件注册机制
2. 命令入口扩展
3. GUI 变体选择
4. `Phase 4B / 4C`

---

### Task 1: 增加带 tag 输出的 debug recipe 基元

**Files:**
- Create: `common/src/main/java/xczl/recursivecraft/recipe/DebugTaggedResultRecipe.java`
- Create: `common/src/main/java/xczl/recursivecraft/recipe/DebugTaggedResultRecipeSerializer.java`
- Create: `common/src/main/java/xczl/recursivecraft/registry/ModRecipeSerializers.java`
- Modify: `common/src/main/java/xczl/recursivecraft/RecursiveCraft.java`
- Create: `common/src/test/java/xczl/recursivecraft/recipe/DebugTaggedResultRecipeTest.java`

**Step 1: Write the failing test**

覆盖：

- recipe 能返回带 `recursivecraft_debug.variant` 的结果栈
- red / blue 两个结果身份不同
- serializer 读出的输出栈 tag 与声明一致

**Step 2: Run test to verify it fails**

Run:

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest"
```

Expected:

- `BUILD FAILED`

**Step 3: Write minimal implementation**

实现：

- 最小 recipe class
- 最小 serializer
- 注册 serializer
- 在 `RecursiveCraft.init()` 中注册

**Step 4: Run test to verify it passes**

Run the same command.

**Step 5: Commit**

```powershell
git add common/src/main/java/xczl/recursivecraft/recipe/DebugTaggedResultRecipe.java common/src/main/java/xczl/recursivecraft/recipe/DebugTaggedResultRecipeSerializer.java common/src/main/java/xczl/recursivecraft/registry/ModRecipeSerializers.java common/src/main/java/xczl/recursivecraft/RecursiveCraft.java common/src/test/java/xczl/recursivecraft/recipe/DebugTaggedResultRecipeTest.java
git commit -m "feat: add debug tagged recipe fixture primitive"
```

---

### Task 2: 增加 red / blue 两条 debug recipe 数据

**Files:**
- Create: `common/src/main/resources/data/recursivecraft/recipes/debug/handheld_crafter_red.json`
- Create: `common/src/main/resources/data/recursivecraft/recipes/debug/handheld_crafter_blue.json`
- Modify: `common/src/test/java/xczl/recursivecraft/core/TransactionCalculatorNbtTest.java`

**Step 1: Write the failing test**

覆盖：

- 两条 recipe 拥有不同 id
- 两条 recipe 输出同一 `Item`
- 两条 recipe 输出 tag 不同
- 输出身份可被 `DefaultMaterialIdentityNormalizer` 区分

**Step 2: Run test to verify it fails**

Run:

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest"
```

Expected:

- 与 debug fixture 相关的新测试失败

**Step 3: Write minimal implementation**

实现：

- red / blue recipe JSON
- 保证两条 recipe 输入成本高于现有普通 `handheld_crafter` recipe

**Step 4: Run test to verify it passes**

Run the same command.

**Step 5: Commit**

```powershell
git add common/src/main/resources/data/recursivecraft/recipes/debug/handheld_crafter_red.json common/src/main/resources/data/recursivecraft/recipes/debug/handheld_crafter_blue.json common/src/test/java/xczl/recursivecraft/core/TransactionCalculatorNbtTest.java
git commit -m "feat: add red blue debug fixture recipes"
```

---

### Task 3: 把 fixture 接入目标身份链的自动化验证

**Files:**
- Modify: `common/src/test/java/xczl/recursivecraft/core/CraftingTaskExecutorTargetOutputTest.java`
- Modify: `common/src/test/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandlerTest.java`

**Step 1: Write the failing test**

覆盖：

- 指定 red 目标身份时不会误选 blue
- 指定 blue 目标身份时不会误选 red
- JEI 构包仍会把展示输出 tag 传下去

**Step 2: Run test to verify it fails**

Run:

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest"
```

Expected:

- `BUILD FAILED`

**Step 3: Write minimal implementation**

实现：

- 只补测试，不再扩张生产逻辑，除非发现现有链路无法消费 fixture

**Step 4: Run test to verify it passes**

Run the same command.

**Step 5: Commit**

```powershell
git add common/src/test/java/xczl/recursivecraft/core/CraftingTaskExecutorTargetOutputTest.java common/src/test/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandlerTest.java
git commit -m "test: cover debug fixture through target identity path"
```

---

### Task 4: 写回 runtime verification fixture 当前真相

**Files:**
- Modify: `docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md`
- Modify: `docs/plans/2026-05-31-phase4a-handoff.md`
- Modify: `docs/capabilities/recursive-craft-execution.md`

**Step 1: Add verification writeback**

写回：

- 已新增 red / blue debug fixture
- `2.2 / 2.3` 可基于该 fixture 做真实 JEI 复验
- 夹具属于 shipped debug content，不是正式玩法承诺

**Step 2: Run focused verification**

Run:

```powershell
E:\Program\RecursiveCraft\gradlew.bat -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 :common:test --tests "xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest" --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest"
```

Expected:

- `BUILD SUCCESSFUL`

**Step 3: Commit**

```powershell
git add docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md docs/plans/2026-05-31-phase4a-handoff.md docs/capabilities/recursive-craft-execution.md
git commit -m "docs: record phase4a debug verification fixture"
```
