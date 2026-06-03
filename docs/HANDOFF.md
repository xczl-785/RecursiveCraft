# RecursiveCraft 当前转接说明

> 当前分支：`1.20.1`
> 文档日期：2026-06-02
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
3. `Phase 4A` 第一批“目标产物身份主动指定”已经完成后端契约、执行链路、JEI 构包、GUI 的 JEI runtime 变体补充选择和自动化覆盖。
4. `Phase 4A` 的固定自动化矩阵与 `forge:configureClientLaunch` 已在当前工作树重新验证通过。
5. `Phase 4A` 的关键 runtime 身份链路证据已补齐：Forge runtime 中已观测到 `recursivecraft:debug/handheld_crafter_blue|red` 的真实产物分别保留 `recursivecraft_debug.variant = "blue"|"red"`。
6. 共享库存抽象层已经是正式当前架构，不再只存在于历史计划文档中。
7. 存储扩展的官方路线已经明确为：
   - 先方块模式相邻原版容器
   - 后第三方存储接口适配

---

## 三、如果继续当前主线，直接任务是什么

如果下一窗口继续当前主线，默认不再是 `Phase 4A` 复验窗口，而是进入 `Phase 4A` 之后的 NBT 扩展窗口：

1. 先刷新当前头提交的 `Phase 3` 方案-代码对照，明确残余 `Item` 级桥接与执行语义缺口。
2. 然后进入方块模式相邻原版容器支持的正式设计与实施。
3. 再规划第三方存储接口适配。
4. planner 是否需要局部 NBT 感知放在后续评估项。

---

## 四、固定验证命令

聚焦 `Phase 4A` 的固定自动化验证命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft :common:test --tests "xczl.recursivecraft.runtime.material.TargetOutputSpecTest" --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest" --tests "xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest" --tests "xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest"
```

当前 Unix-like 环境可直接使用：

```bash
./gradlew --no-daemon --console=plain :common:test --tests 'xczl.recursivecraft.runtime.material.TargetOutputSpecTest' --tests 'xczl.recursivecraft.networking.C2SExecuteCraftPacketTest' --tests 'xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest' --tests 'xczl.recursivecraft.core.TransactionCalculatorNbtTest' --tests 'xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest' --tests 'xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest' --tests 'xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest'
```

运行时复验准备命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:runClient
```

当前 Unix-like 环境可直接使用：

```bash
./gradlew --no-daemon --console=plain forge:configureClientLaunch
./gradlew --no-daemon --console=plain forge:runClient
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

1. 不把后续 NBT 窗口退回成“从头重新实现 `Phase 4A`”窗口。
2. 不把 shipped debug fixture 误写成正式玩法承诺。
3. 不把方块模式 / 第三方存储支持直接堆回旧 `Map<Item, Integer>` 模型。
4. 不在未完成 `Phase 3` 对照刷新前，跳过当前头提交的残余语义核对。

---

## 七、下一轮扩展优先级

`Phase 4A` 收口后，后续扩展默认优先级为：

1. `Phase 3` 当前头提交方案-代码对照刷新
2. 方块模式相邻原版容器支持
3. 第三方存储接口适配策略
4. planner 是否需要局部 NBT 感知

其中存储路线的正式说明以 `docs/architecture/存储支持与适配路线图.md` 为准。
