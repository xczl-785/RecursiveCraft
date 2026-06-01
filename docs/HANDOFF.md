# RecursiveCraft 当前转接说明

> 当前分支：`1.20.1`
> 文档日期：2026-06-01
> 用途：为下一窗口、协作者或代理恢复当前主线状态、直接任务和边界

---

## 一、优先阅读顺序

1. `docs/README.md`
2. `docs/architecture/NBT_总体方案决议.md`
3. `docs/architecture/NBT_实施路线图.md`
4. `docs/architecture/共享库存抽象层.md`
5. `docs/architecture/存储支持与适配路线图.md`
6. `docs/capabilities/recursive-craft-execution.md`
7. `docs/Phase4A_运行时验证.md`
8. `docs/reviews/2026-05-31-NBT_Phase3_方案与代码对照报告.md`

---

## 二、当前主线真相

当前应按以下事实理解仓库状态：

1. 当前实现主线已落在真实 `1.20.1` 分支。
2. `Phase 2` 第一阶段运行时 NBT 主链已经落地，范围限定为单来源玩家背包路径。
3. `Phase 4A` 第一批“目标产物身份主动指定”已经完成后端契约、执行链路、JEI 构包和自动化覆盖。
4. `Phase 4A` 仍未完成真实运行时 `JEI Ctrl` / gameplay 手工复验，因此不能宣称该阶段已整体收口。
5. 共享库存抽象层已经是正式当前架构，不再只存在于历史计划文档中。
6. 存储扩展的官方路线已经明确为：
   - 先方块模式相邻原版容器
   - 后第三方存储接口适配

---

## 三、如果继续当前主线，直接任务是什么

如果下一窗口继续当前 `Phase 4A` 主线，直接任务不是重新开做实现，而是：

1. 先执行固定自动化验证命令。
2. 优先使用 `Forge` 进行运行时复验准备；若受阻，再回退 `Fabric`。
3. 用 `recursivecraft:debug/handheld_crafter_red` 与 `recursivecraft:debug/handheld_crafter_blue` 完成 `JEI Ctrl` 样例复验。
4. 写回结果时明确区分：
   - `gameplay`
   - `synthetic-debug`

---

## 四、固定验证命令

聚焦 `Phase 4A` 的固定自动化验证命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft :common:test --tests "xczl.recursivecraft.runtime.material.TargetOutputSpecTest" --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest" --tests "xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest" --tests "xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest"
```

运行时复验准备命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:runClient
```

若 `Forge` 路径受阻，再回退：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft fabric:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft fabric:runClient
```

---

## 五、默认运行时验证样例

当前默认样例是：

1. `recursivecraft:debug/handheld_crafter_red`
2. `recursivecraft:debug/handheld_crafter_blue`

这两条样例共享同一 `Item`，但携带不同 `recursivecraft_debug.variant`，用于验证：

- JEI 当前展示什么输出身份
- 递归执行就按什么身份产出

---

## 六、明确边界

下一窗口继续时仍需遵守：

1. 不把 `Phase 4A` 复验窗口退回成“从头重新实现”窗口。
2. 不在 `Phase 4A` 收口时顺手混入 `Phase 4B` 命令扩展或 `Phase 4C` GUI 变体选择。
3. 不把“准备命令可执行”写成“真实运行时复验已完成”。
4. 不把存储适配工作混入 `Phase 4A` 当前收口。
5. 在真实运行时复验补齐前，不得宣称“目标产物 NBT 主动指定”已经整体完成。

---

## 七、下一轮扩展优先级

`Phase 4A` 复验补齐后，后续扩展默认优先级为：

1. 方块模式相邻原版容器支持
2. 第三方存储接口适配策略
3. planner 是否需要局部 NBT 感知

其中存储路线的正式说明以 `docs/architecture/存储支持与适配路线图.md` 为准。
