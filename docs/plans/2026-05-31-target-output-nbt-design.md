# RecursiveCraft 目标产物 NBT 主动指定设计方案

> 目标分支：`1.20.1`
> 文档日期：2026-05-31
> 主题定位：`Phase 4` 第一优先扩展项
> 文档目标：把“目标产物 NBT 主动指定”从评估项升级为可落地的正式子方案

---

## 一、设计结论

本方案的推荐路线是：

- **保留 `CraftingPlanner` 的 `Item` 级粗规划**
- **在运行时链路引入目标产物身份约束**
- **先扩后端契约与 JEI 递归入口**
- **再按阶段扩到命令与自定义 GUI**

不推荐把这项能力一开始就做成：

- planner 级 NBT 图规划
- 自定义 GUI 直接列出全部 NBT 变体
- 一次性覆盖所有入口、所有复杂输出规则

原因很明确：

1. 当前代码已经具备 `desiredKey` 的运行时预留位，最小可行路径是把它从“内部能力”升级为“正式入口契约”。
2. JEI 当前天然掌握“用户看到的具体输出栈”，是最适合先打通的入口。
3. 自定义 GUI 目前仍是 `Item` 级列表，如果强行一步做成 NBT 变体选择器，会把“变体发现 / 展示 / 搜索 / 收藏”一整串复杂度同时抬高。

---

## 二、背景与现状

当前系统已经完成：

1. 输入材料侧的运行时 NBT 精确匹配
2. 单来源玩家背包路径的精确扣减与正式执行
3. 请求级 `MISSING / UNSUPPORTED` 聚合

但目标产物侧仍停留在：

- 用户只指定 `Item`
- 未指定时，默认按选中的 recipe 真实输出

当前入口现状：

1. 命令：`Item + amount`
2. GUI：`Item + amount`
3. JEI Ctrl 递归：`Item + amount + recipeId`
4. 网络包：`Item + amount + optional recipeId`
5. 执行器：`Item + amount + optional recipeId`

当前内部现状：

- `TransactionCalculator.calculateRecursive(..., MaterialKey desiredKey)` 已能约束目标输出身份
- `simulateRecipe()` 与 `recipeMatchesDesiredIdentity()` 已能按目标身份筛掉错误输出
- 但公开入口还没有把目标身份传进来

---

## 三、设计目标

### 3.1 本方案要解决的问题

当同一个 `Item` 对应多个不同 NBT 输出变体时，用户应能主动表达：

- “我要这个具体输出变体”

而不是只能说：

- “我要这个 `Item`，至于最终是什么 NBT，由系统默认 recipe 决定”

### 3.2 本方案不解决的问题

本方案不在当前子方案内解决：

1. planner 升级为 NBT 节点图
2. 外部存储支持
3. 第三方存储兼容
4. 自定义 GUI 一次性列出所有可能输出变体
5. 复杂字段忽略规则系统

---

## 四、核心设计决策

### 4.1 目标产物表达采用“目标输出约束”，不是“自由构造任意栈”

推荐定义：

- 用户表达的是一个 **`TargetOutputSpec`**
- 它表示“我要求最终输出满足这个身份约束”
- 它不是“我任意伪造一个输出栈，系统必须想办法做出来”

推荐结构：

```java
public final class TargetOutputSpec {
    private final Item item;
    private final @Nullable CompoundTag tag;
    private final MatchMode mode;
}
```

第一阶段推荐 `MatchMode` 只支持：

- `EXACT_NORMALIZED_IDENTITY`

含义：

- 服务端接到 `TargetOutputSpec` 后，先走 `MaterialIdentityNormalizer`
- 归一化成功则得到 `desiredOutputKey`
- 后续所有 recipe 选择都围绕这个 `desiredOutputKey`

为什么不直接传 `MaterialKey`：

1. `MaterialKey` 是运行时内部对象，不适合直接作为网络契约
2. 当前客户端和命令层更自然持有的是 `Item + NBT`
3. 用 `ItemStack` / `Item + Tag` 表达，服务端再归一化，更符合现有体系

### 4.2 继续坚持 runtime-only 目标约束，不升级 planner

推荐规则：

1. planner 仍按 `Item` 级给候选 recipe 列表
2. runtime 在 `TransactionCalculator` 中用 `desiredOutputKey` 过滤和排序
3. 只有输出身份满足目标约束的 recipe 才算合法候选

这意味着：

- planner 继续回答“理论上做这个 `Item` 有哪些候选”
- runtime 回答“这些候选里，哪些能真正产出我指定的目标变体”

这是当前最稳妥的路线。

### 4.3 失败语义仍尽量保持两类对外结果

推荐对外仍只承诺：

1. `MISSING`
2. `UNSUPPORTED`

解释：

- 如果目标输出约束无法被任何 recipe 满足，但语义上系统能稳定判断，这是 `MISSING`
- 如果目标输出约束本身无法被当前 normalizer / matcher 稳定表达，这是 `UNSUPPORTED`

不推荐新增第三类用户可见错误码，除非后续确实发现“目标输出不匹配”必须独立表达。

### 4.4 入口分阶段扩展，而不是同步全部改完

推荐阶段顺序：

1. `Phase 4A`：后端契约 + JEI 递归入口
2. `Phase 4B`：命令入口
3. `Phase 4C`：自定义 GUI

原因：

- JEI 当前已经天然拿得到“当前展示 recipe 的具体输出栈”
- 命令次之，可通过附加 SNBT 参数补进来
- GUI 最后做，因为当前 GUI 的列表、收藏、搜索都是 `Item` 级

---

## 五、推荐架构方案

### 5.1 公开入口契约

推荐把当前请求模型：

```java
(Item targetItem, int amount, @Nullable ResourceLocation forcedRecipeId)
```

演进为：

```java
(Item targetItem,
 int amount,
 @Nullable ResourceLocation forcedRecipeId,
 @Nullable TargetOutputSpec targetOutputSpec)
```

兼容规则：

- `targetOutputSpec == null` 时，完全沿用当前行为
- `targetOutputSpec != null` 时，系统必须保证最终产物满足目标身份

入参不变量：

- 当 `targetOutputSpec != null` 时，`targetOutputSpec.item` 必须与 `targetItem` 完全相等
- 若两者不相等，请求直接视为 `invalid_request`

不采用“以其中一个为准并自动修正另一个”的策略，原因：

1. 这会把基础入参冲突静默吞掉
2. 容易造成命令、JEI、网络三条入口行为不一致
3. 这类错误属于请求构造错误，而不是运行时缺料或不支持

### 5.2 服务端处理链

推荐服务端执行顺序：

1. 接收 `targetItem + amount + recipeId + targetOutputSpec`
2. 若 `targetOutputSpec != null`：
   - 构造临时 `ItemStack`
   - 走 `MaterialIdentityNormalizer`
   - 成功则得到 `desiredOutputKey`
   - 失败则请求直接为 `UNSUPPORTED`
3. `CraftingTaskExecutor` 把 `desiredOutputKey` 传给 `TransactionCalculator`
4. `TransactionCalculator`：
   - 顶层 recipe 候选先按 `Item` 收集
   - 再按 `desiredOutputKey` 过滤输出身份
   - 递归搜索时保持现有输入侧 NBT 语义
5. 事务生成后，执行器在最终成功校验时也必须确认：
   - `resolvedOutputs` 中满足目标输出身份
   - 不能再只按 `Item` 数量判成功

### 5.3 成功判定升级

这是本方案里最关键的一条：

当前 `CraftingTaskExecutor` 的成功判定仍是：

- `splitNetChanges()`
- `hasEnoughTargetProvide(targetItem, amount, netProvides)`

这只看 `Item` 数量，不看目标身份。

支持目标产物 NBT 后，必须升级为：

- 若无 `targetOutputSpec`：沿用当前 `Item` 级判定
- 若有 `targetOutputSpec`：按 `resolvedOutputs` 的规范化身份统计目标变体数量

否则会出现：

- 做出了“错误 NBT 的同类物品”
- 但系统仍误判为成功

### 5.4 JEI 路径

JEI 是推荐的第一落地点。

当前 JEI Ctrl 递归只发送：

- `output.getItem()`
- `recipe.getId()`

推荐升级为：

- `output.getItem()`
- `recipe.getId()`
- `output` 对应的 `TargetOutputSpec`

这样 JEI 的语义可以保持：

- 用户看到什么输出
- 递归合成就按什么输出身份去做

### 5.5 命令路径

推荐命令扩展方式：

```text
/craft_recursive <item> <amount> [target_nbt_snbt]
```

说明：

- 没有第三参时，保持旧行为
- 有第三参时，由服务端解析成 `CompoundTag`

不推荐第一版做复杂 DSL。

### 5.6 GUI 路径

GUI 不建议在第一落地点就强做完整变体选择器。

推荐路线：

1. 先保持 GUI 当前能力不变
2. 等 JEI 和命令路径证明后端契约稳定后
3. 再单独规划 GUI 的“输出变体发现 / 展示 / 搜索 / 收藏”能力

也就是说：

- GUI 属于本方案的完整路线一部分
- 但不是第一批实施窗口的必选项

---

## 六、失败语义与不支持边界

### 6.1 `UNSUPPORTED`

以下情况推荐归为 `UNSUPPORTED`：

1. `TargetOutputSpec` 无法被当前 normalizer 稳定归一化
2. recipe 输出身份无法被稳定归一化
3. 目标输出约束依赖当前版本未支持的复杂语义

### 6.2 `MISSING`

以下情况推荐归为 `MISSING`：

1. 系统能理解目标输出约束
2. 但在当前 recipe 集合中没有可行路径生成该目标变体
3. 或者有合法路径，但当前输入材料不足

### 6.3 `invalid_request`

以下情况推荐在进入计算前直接拒绝：

1. `targetItem == AIR`
2. `amount <= 0`
3. `targetOutputSpec.item != targetItem`

这类错误不属于递归合成搜索结果，因此不应折叠进 `MISSING / UNSUPPORTED`。

### 6.4 明确不支持项

第一版应明确不支持：

1. 部分字段忽略规则
2. 任意复杂 Tag 模式匹配
3. 跨第三方存储联动
4. GUI 端任意输出变体浏览器

---

## 七、测试与验收矩阵

本方案至少必须覆盖：

1. 未传 `targetOutputSpec` 时旧行为不变
2. 传入正确目标输出 NBT 时成功
3. 传入错误目标输出 NBT 时不误判成功
4. 同一 `Item` 多个输出变体时，能选中正确 recipe
5. 强制 `recipeId` 与目标输出身份冲突时结果正确
6. JEI 递归路径按展示输出身份工作
7. `UNSUPPORTED` 与 `MISSING` 区分正确
8. `targetItem` 与 `targetOutputSpec.item` 冲突时直接视为 `invalid_request`

### 7.1 手工 / 端到端验证矩阵

除自动化验证外，`Phase 4A` 至少应补以下手工或集成验证：

1. JEI 展示输出 A，递归执行后真实产物确为 A
2. JEI 展示输出 B，且与 A 为同 `Item` 不同 NBT，执行后不得误产出 A
3. 强制 `recipeId` 指向错误输出身份时，请求不得误判成功
4. 未传 `TargetOutputSpec` 时，JEI 以外旧路径行为不变
5. 网络包中 `targetItem` 与 `targetOutputSpec.item` 冲突时，服务端直接拒绝

---

## 八、推荐实施顺序

### 8.1 `Phase 4A` 后端契约 + JEI

做什么：

1. 引入 `TargetOutputSpec`
2. 升级 `C2SExecuteCraftPacket`
3. 升级 `CraftingTaskExecutor`
4. 升级 `TransactionCalculator` 公开入口
5. 升级成功判定逻辑
6. 改 JEI 递归入口
7. 补自动化测试

不做什么：

1. 不做 GUI 变体浏览器
2. 不做命令 DSL 复杂扩展
3. 不做 planner NBT 感知

前置门槛：

1. `Phase 3` 的方案代码对照已完成
2. `Phase 3` 固定自动化验证已重新执行
3. `Phase 2/3` 手工验证清单与复验记录已补齐到足以结束收口阶段

### 8.2 `Phase 4B` 命令

做什么：

1. 为命令增加可选 SNBT 输入
2. 增加命令层解析与错误提示

### 8.3 `Phase 4C` GUI

做什么：

1. 设计输出变体展示模型
2. 扩展 `CraftableItemList` / `RecursiveCrafterScreen`
3. 处理收藏与搜索语义

---

## 九、推荐结论

本方案的推荐结论是：

1. 采用 **runtime-only 的目标输出身份约束**
2. 公开入口新增 **`TargetOutputSpec`**
3. 第一批实施优先落 **后端契约 + JEI**
4. 命令与 GUI 分阶段补入
5. `RuntimeMatchClause` 不在本子方案第一批实现内扩张

这条路线最符合当前仓库现状，也最能避免把下一阶段重新做成一轮大爆炸式改造。
