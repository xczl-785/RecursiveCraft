# Phase 4A 运行时验证

> 目标分支：`1.20.1`
> 适用阶段：`Phase 4A`
> 文档日期：2026-06-01
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
2. 自定义 GUI 变体选择器
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

当前仍未完成：

1. `JEI Ctrl` 从当前展示输出发起后，真实产物是否与展示身份一致的 gameplay 复验
2. 同一 `Item` 的不同 NBT 输出变体在真实运行时是否稳定区分
3. 请求级失败信息在真实界面或聊天提示中是否与自动化预期一致

在以上证据补齐前，不应宣称 `Phase 4A` 已整体完成。

---

## 三、固定验证命令

固定自动化验证命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft :common:test --tests "xczl.recursivecraft.runtime.material.TargetOutputSpecTest" --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest" --tests "xczl.recursivecraft.recipe.DebugTaggedResultRecipeTest" --tests "xczl.recursivecraft.runtime.match.DefaultMaterialMatcherTest"
```

运行时复验准备命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft forge:runClient
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
| `2.2` | JEI 展示变体 A 正确产出 | gameplay 可达 | 部分覆盖 | 待完成 | 自动化已覆盖构包与 identity 路径，真实产出仍待运行时验证 |
| `2.3` | JEI 展示变体 B 不误产出 A | gameplay 可达 | 部分覆盖 | 待完成 | 自动化已覆盖 red/blue sibling identity，不等于 gameplay 已闭环 |
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

---

## 八、收口边界

只有在以下条件同时满足时，才可以把 `Phase 4A` 写成“已收口”：

1. 固定自动化验证仍然通过
2. `2.2 / 2.3` 的真实运行时证据已补齐
3. 若补做 `2.4 / 2.5 / 2.6`，写回时已明确其到达方式
4. 写回没有把 shipped debug fixture 误写成正式玩法承诺
