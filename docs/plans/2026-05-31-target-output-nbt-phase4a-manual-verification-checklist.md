# 目标产物 NBT 主动指定 Phase 4A 手工验证清单

> 目标分支：`1.20.1`
> 适用阶段：`Phase 4A`
> 文档日期：2026-05-31
> 用途：为“目标产物 NBT 主动指定”第一批实施提供固定的 JEI / 网络 / 执行器端到端复验面

---

## 一、适用范围

本清单只覆盖：

1. 后端契约
2. `JEI Ctrl` 递归入口
3. 单来源玩家背包路径

本清单不覆盖：

1. 命令增强版 `SNBT` 输入
2. 自定义 GUI 变体选择器
3. 外部存储来源

---

## 二、当前状态快照

### 2.1 已完成的自动化验证

当前已存在自动化覆盖：

1. `TargetOutputSpec` 的构造约束与模板栈导出
2. `C2SExecuteCraftPacket` 的旧形状兼容、新字段 round-trip、`targetItem/spec.item` 冲突拒绝
3. `CraftingTaskExecutor` 的旧成功路径保留、目标身份 `MISSING / UNSUPPORTED` 结果、入口不变量拒绝
4. `TransactionCalculator` 的目标输出身份重载、目标 identity 过滤、旧空 spec 语义保留
5. `JEI Ctrl` 递归构包的 `recipeId + TargetOutputSpec` 绑定
6. debug fixture tagged recipe / serializer
7. debug fixture red / blue sibling identity 在执行器与 JEI 构包测试中的消费路径

固定验证命令：

`java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :common:test --tests "xczl.recursivecraft.runtime.material.TargetOutputSpecTest" --tests "xczl.recursivecraft.networking.C2SExecuteCraftPacketTest" --tests "xczl.recursivecraft.core.CraftingTaskExecutorTargetOutputTest" --tests "xczl.recursivecraft.core.TransactionCalculatorNbtTest" --tests "xczl.recursivecraft.compat.jei.RecursiveCraftTransferHandlerTest"`

### 2.2 运行时复验准备事实

推荐平台选择：

1. 优先使用 `Forge`
2. `Fabric` 仅作为回退路径，用于 `Forge` 环境受阻或需要补做装载器对照时使用

当前环境已验证可成功执行的 launch 准备命令：

```powershell
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 forge:configureClientLaunch
java -classpath E:\Program\RecursiveCraft\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p E:\Program\RecursiveCraft\.worktrees\backport-1.20.1 fabric:configureClientLaunch
```

补充说明：

1. 上述两条 `configureClientLaunch` 命令都已成功
2. 在当前环境中，应使用仓库根目录 wrapper jar + `GradleWrapperMain` 形式，不应把工作树内的 `.\gradlew` / `.\gradlew.bat` 作为首选入口
3. 以上结论只代表“运行时复验准备命令可执行”，不代表 `JEI Ctrl` gameplay 手工复验已经完成
4. 当前仓库已补入 `recursivecraft:debug/handheld_crafter_red` 与 `recursivecraft:debug/handheld_crafter_blue` 作为 runtime verification fixture

### 2.3 仍待完成的手工 / 集成验证

以下结论仍未在真实运行时完成复验：

1. `JEI Ctrl` 从当前展示输出变体发起请求后，真实产物是否与展示身份一致
2. 同一 `Item` 的不同 NBT 输出变体在真实 gameplay 中是否会稳定区分
3. 请求级失败信息在真实界面 / 聊天提示中是否与自动化预期一致

在这些场景补证前，不应宣称 `Phase 4A` 已整体完成。

---

## 三、验证场景分层

### 3.1 正常 gameplay / `JEI Ctrl` 可达场景

#### 2.1 旧路径不退化

1. 不传 `TargetOutputSpec`
2. 走现有物品级递归请求
3. 行为应与当前版本一致

#### 2.2 JEI 展示变体 A 正确产出

1. JEI 当前展示输出变体 A
2. 使用 `Ctrl` 递归发送请求
3. 最终产物必须是 A
4. 前提是当前运行环境中存在“同一 `Item`、多个不同 NBT 输出变体”的 JEI 样例
5. 当前推荐直接使用：
   - `recursivecraft:debug/handheld_crafter_red`
   - `recursivecraft:debug/handheld_crafter_blue`

#### 2.3 JEI 展示变体 B 不误产出 A

1. 同一 `Item` 存在多个不同 NBT 输出变体
2. JEI 当前展示 B
3. 执行后不得产出 A
4. 若缺少上述 JEI 样例，则该项应记录为“样例未具备”，而不是“代码未通过”
5. 当前仓库已提供上述 debug fixture，可直接作为默认样例

### 3.2 需要 synthetic / debug 路径的场景

#### 2.4 强制 `recipeId` 与目标身份冲突

1. 请求同时带 `recipeId` 与 `TargetOutputSpec`
2. `recipeId` 指向的输出身份与目标身份不一致
3. 请求不得误判成功
4. 该场景不属于正常 `JEI Ctrl` 点击流，需通过改包、数据注入或 debug harness 构造

#### 2.5 `targetItem` 与 `TargetOutputSpec.item` 冲突

1. 人为构造一个冲突请求
2. 服务端应直接拒绝
3. 不进入递归计算主链
4. 该场景不属于正常 `JEI Ctrl` 点击流，需通过改包或 debug harness 构造

#### 2.6 缺料与不支持区分

1. 目标身份可理解但当前无可行路径
2. 目标身份当前版本无法稳定归一化
3. 两类结果必须可区分
4. `MISSING` 子场景可能在真实 gameplay 中观察到；`UNSUPPORTED` 子场景更应视为 synthetic / debug-path 复验，不宜写成“普通 JEI Ctrl 点击即可覆盖”

---

## 四、自动化 / 手工对应矩阵

| 编号 | 场景 | 到达方式 | 自动化状态 | 手工 / 集成状态 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 2.1 | 旧路径不退化 | 正常 gameplay 抽样可达 | 已覆盖 | 可选抽样 | `CraftingTaskExecutorTargetOutputTest.tryExecute_shouldKeepLegacySuccessCheckWhenTargetOutputSpecIsNull` 与 `TransactionCalculatorNbtTest.calculate_overloadWithNullDesiredOutputKeyShouldPreserveLegacySemantics` 已覆盖旧语义保留 |
| 2.2 | JEI 展示变体 A 正确产出 | 正常 gameplay 可达；当前默认样例为 debug fixture | 部分覆盖 | 待完成 | 自动化已覆盖 JEI 构包 + red/blue sibling identity 路径；真实产出仍需在运行时验证 |
| 2.3 | JEI 展示变体 B 不误产出 A | 正常 gameplay 可达；当前默认样例为 debug fixture | 部分覆盖 | 待完成 | 自动化已覆盖同一 `Item` 不同 tag 会形成不同 `TargetOutputSpec`，且 red/blue 不会互相误选；真实 gameplay 仍待验证 |
| 2.4 | `recipeId` 与目标身份冲突不得误判成功 | 仅 synthetic / debug | 已覆盖核心判定 | 待完成 | 自动化已覆盖目标 identity mismatch 不会被算作成功；该项不能被描述为普通 JEI 点击流 |
| 2.5 | `targetItem` 与 `TargetOutputSpec.item` 冲突 | 仅 synthetic / debug | 已覆盖 | 待完成 | 包入口与执行器入口都已自动化覆盖“直接拒绝且不进入主链”；若补人工复验，需改包或 debug harness |
| 2.6 | 缺料与不支持区分 | 组合场景：`MISSING` 可 gameplay / debug，`UNSUPPORTED` 以 synthetic / debug 为主 | 已覆盖 | 待完成 | 执行器 / 计算器自动化已覆盖 `MISSING / UNSUPPORTED` 分流；不要把整项写成“普通 JEI Ctrl 点击即可完整覆盖” |

---

## 五、记录要求

每次复验至少记录：

1. 日期
2. 提交号
3. 场景编号
4. 到达方式（`gameplay` / `synthetic-debug`）
5. 使用的样例 recipe / 物品
6. 预期结果
7. 实际结果
8. 是否通过
9. 异常说明
