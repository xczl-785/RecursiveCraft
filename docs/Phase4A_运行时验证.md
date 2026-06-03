# Phase 4A 运行时验证

> 目标分支：`1.20.1`
> 适用阶段：`Phase 4A`
> 文档日期：2026-06-02
> 用途：作为“目标产物身份主动指定”当前正式复验文档

---

## 一、适用范围

本文件只覆盖：

1. 后端契约
2. `JEI Ctrl` 递归入口
3. 单来源玩家背包路径
4. `Phase 4A` 当前自动化与运行时复验边界

本文件不覆盖：

1. 命令增强版 `SNBT` 输入
2. planner 原生的通用 GUI 变体发现/选择器
3. 外部存储来源

---

## 二、当前状态快照

### 2.1 已完成的自动化覆盖

当前已具备以下自动化覆盖：

1. `TargetOutputSpec` 构造约束与模板栈导出
2. `C2SExecuteCraftPacket` 的旧形状兼容、新字段 round-trip、`targetItem/spec.item` 冲突拒绝
3. `CraftingTaskExecutor` 的旧成功路径保留、目标身份 `MISSING / UNSUPPORTED` 结果、入口不变量拒绝
4. `TransactionCalculator` 的目标输出身份重载、目标 identity 过滤、旧空 spec 语义保留
5. `JEI Ctrl` 递归构包的 `recipeId + TargetOutputSpec` 绑定
6. debug fixture recipe / serializer
7. debug fixture red / blue sibling identity 在执行器与 JEI 路径中的消费验证

### 2.2 当前仍待完成的部分

当前仍保留为后续抽样或扩展验证的部分：

1. 更广泛 mod 场景下的组合回归，而不只是不带歧义的 debug fixture
2. 超出 `JEI Ctrl` 入口的更多 gameplay 入口抽样
3. 扩展来源或复杂容器场景下的身份与失败语义验证

当前不应把这些剩余项写成“关键 runtime 身份链路仍未验证”；该关键链路已取得运行时证据。

---

## 三、固定验证命令

固定自动化验证命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft :common:test --tests "xczl.recursivecraft.runtime.material.TargetOutputSpecTest" --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest" --tests "xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest" --tests "xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest"
```

当前仓库已恢复标准 Gradle wrapper，Unix-like 环境可直接使用：

```bash
./gradlew --no-daemon --console=plain :common:test --tests 'xczl.recursivecraft.runtime.material.TargetOutputSpecTest' --tests 'xczl.recursivecraft.networking.C2SExecuteCraftPacketTest' --tests 'xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest' --tests 'xczl.recursivecraft.core.TransactionCalculatorNbtTest' --tests 'xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest' --tests 'xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest' --tests 'xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest'
```

运行时复验准备命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:runClient
```

Unix-like 环境可直接使用：

```bash
./gradlew --no-daemon --console=plain forge:configureClientLaunch
./gradlew --no-daemon --console=plain forge:runClient
```

回退路径：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft fabric:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft fabric:runClient
```

---

## 四、默认样例

当前默认 runtime verification 样例为：

1. `recursivecraft:debug/handheld_crafter_red`
2. `recursivecraft:debug/handheld_crafter_blue`

说明：

1. 两条 recipe 输出同一 `Item`
2. 两条 recipe 携带不同 `recursivecraft_debug.variant`
3. 它们属于 shipped debug content，不是正式玩法承诺

---

## 五、验证场景分层

### 5.1 正常 gameplay / `JEI Ctrl` 可达

1. `2.1` 旧路径不退化
2. `2.2` JEI 展示变体 A 正确产出
3. `2.3` JEI 展示变体 B 不误产出 A

当前默认用 red / blue fixture 覆盖 `2.2 / 2.3`。

### 5.2 仅 synthetic / debug 可达

1. `2.4` 强制 `recipeId` 与目标身份冲突
2. `2.5` `targetItem` 与 `TargetOutputSpec.item` 冲突

### 5.3 组合验证

1. `2.6` 中的 `MISSING` 子场景可由 gameplay 或 debug 路径观察
2. `2.6` 中的 `UNSUPPORTED` 子场景应以 synthetic / debug 路径为主

---

## 六、自动化与手工对应矩阵

| 编号 | 场景 | 到达方式 | 自动化状态 | 手工 / 集成状态 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `2.1` | 旧路径不退化 | gameplay 抽样可达 | 已覆盖 | 可选抽样 | 无 `TargetOutputSpec` 时保持旧语义 |
| `2.2` | JEI 展示变体 A 正确产出 | gameplay 可达 | 部分覆盖 | 已补证据 | 已取得运行时证据：背包实际产物保留 `recursivecraft_debug.variant`，与 JEI 当前展示变体对应 |
| `2.3` | JEI 展示变体 B 不误产出 A | gameplay 可达 | 部分覆盖 | 已补证据 | 已取得运行时证据：同一 `Item` 的 red / blue 两个目标身份在真实运行时未被压平，实际产物可区分 |
| `2.4` | `recipeId` 与目标身份冲突不得误判成功 | synthetic-debug | 已覆盖核心判定 | 待完成 | 不是普通 JEI 点击流 |
| `2.5` | `targetItem` 与 `TargetOutputSpec.item` 冲突 | synthetic-debug | 已覆盖 | 待完成 | 服务端应直接拒绝且不进入主链 |
| `2.6` | `MISSING` 与 `UNSUPPORTED` 区分 | 组合场景 | 已覆盖 | 待完成 | 不应写成“普通 JEI Ctrl 点击即可完整覆盖” |

---

## 七、记录要求

每次复验至少记录：

1. 日期
2. 提交号
3. 场景编号
4. 到达方式：`gameplay` 或 `synthetic-debug`
5. 使用的样例 recipe / 物品
6. 预期结果
7. 实际结果
8. 是否通过
9. 异常说明

### 7.1 当前已记录结果

| 日期 | 提交号 | 场景编号 | 到达方式 | 样例 | 预期结果 | 实际结果 | 是否通过 | 异常说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `2026-06-01` | `46d6a9d` | 固定自动化矩阵 | `synthetic-debug` | `TargetOutputSpecTest`、`C2SExecuteCraftPacketTest`、`CraftingTaskExecutorTargetOutputTest`、`TransactionCalculatorNbtTest`、`RecursiveCraftTransferHandlerTest`、`DebugTaggedResultRecipeTest`、`DefaultMaterialMatcherTest` | 固定矩阵可在当前工作树重跑通过 | 已使用恢复后的标准 wrapper 在当前工作树重跑，`BUILD SUCCESSFUL` | 通过 | 运行前恢复了 `gradlew` / `gradle-wrapper.jar`，否则仓库无法直接执行固定命令 |
| `2026-06-01` | `46d6a9d` | 运行时复验准备 | `synthetic-debug` | `forge:configureClientLaunch` | Forge 客户端启动准备成功 | `forge:configureClientLaunch` 返回 `BUILD SUCCESSFUL` | 通过 | 仅证明启动准备可执行，不等于已完成真实 gameplay / JEI 手工复验 |
| `2026-06-02` | `46d6a9d` | `2.2 / 2.3` | `gameplay` | `recursivecraft:debug/handheld_crafter_blue`、`recursivecraft:debug/handheld_crafter_red` | JEI 当前展示 blue / red 时，递归执行的真实产物保留对应目标身份 | 用户在 Forge runtime 中通过 `/data get entity @p Inventory` 观测到两个 `recursivecraft:handheld_crafter` 分别携带 `recursivecraft_debug.variant = "blue"` 与 `"red"` | 通过 | 当前证据用于闭合 `Phase 4A` 关键 runtime 身份链路 |

---

## 八、收口边界

只有在以下条件同时满足时，才可以把 `Phase 4A` 写成“已收口”：

1. 固定自动化验证仍然通过
2. `2.2 / 2.3` 的真实运行时证据已补齐
3. 若补做 `2.4 / 2.5 / 2.6`，写回时已明确其到达方式
4. 写回没有把 shipped debug fixture 误写成正式玩法承诺

当前状态：

- 条件 `1 / 2 / 4` 已满足
- `2.4 / 2.5 / 2.6` 仍保留在 synthetic-debug / 组合验证范围，但它们不是当前 `Phase 4A` 关键 runtime 身份链路的剩余 blocker

因此当前可将 `Phase 4A` 写为：**关键 runtime 证据已补齐，可按当前范围收口**。
