# RecursiveCraft Cross-Machine Transfer Handoff

> 目标分支：`1.20.1`
> 文档日期：2026-06-01
> 用途：在另一台机器上继续当前 `Phase 4A` 收尾与后续开发

---

## 一、优先阅读顺序

1. `docs/README.md`
2. `docs/architecture/NBT_总体方案决议.md`
3. `docs/architecture/NBT_实施路线图.md`
4. `docs/capabilities/recursive-craft-execution.md`
5. `docs/plans/2026-05-31-phase4a-handoff.md`
6. `docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md`
7. `docs/plans/2026-05-31-phase4a-debug-fixture-design.md`
8. `docs/plans/2026-05-31-phase4a-debug-fixture-implementation-plan.md`

---

## 二、当前代码状态

当前 `Phase 4A` 已完成：

1. `TargetOutputSpec` 后端契约
2. `C2SExecuteCraftPacket` 扩展
3. `CraftingTaskExecutor` 目标身份成功判定
4. `TransactionCalculator` 公开 `desiredOutputKey` 重载
5. `JEI Ctrl` 构包绑定 `recipeId + TargetOutputSpec`
6. `Phase 4A` 文档写回
7. red / blue debug fixture 基元、数据样例与自动化覆盖

当前仍未完成：

1. 基于真实运行时执行 `2.1 - 2.6` 的手工 / 集成复验
2. 记录真实 gameplay 与 synthetic-debug 的实际复验结果
3. 在复验完成前，不得宣称 `Phase 4A` 已完全收口

---

## 三、固定自动化验证命令

建议先执行：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft :common:test --tests "xczl.recursivecraft.runtime.material.TargetOutputSpecTest" --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest" --tests "xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest" --tests "xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest"
```

---

## 四、运行时复验启动命令

推荐平台：

1. 优先 `Forge`
2. `Fabric` 作为回退

推荐命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:runClient
```

若 `Forge` 受阻，再切：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft fabric:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft fabric:runClient
```

---

## 五、默认 runtime verification 样例

当前 `2.2 / 2.3` 默认使用：

1. `recursivecraft:debug/handheld_crafter_red`
2. `recursivecraft:debug/handheld_crafter_blue`

含义：

1. 同一 `Item`
2. 不同 `recursivecraft_debug.variant`
3. 用于验证 `JEI Ctrl` 展示身份是否会沿链路传到最终结果

---

## 六、迁移后第一件事

在另一台机器上继续开发时，第一件事不是继续改实现，而是：

1. 跑固定自动化验证命令
2. 跑 `Forge` 启动准备 / client
3. 用 red / blue fixture 完成 `2.2 / 2.3`
4. 再决定是否需要继续推进 `2.4 / 2.5 / 2.6` 的 synthetic-debug 复验
