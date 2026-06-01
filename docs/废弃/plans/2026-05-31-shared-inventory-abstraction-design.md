# RecursiveCraft 共享库存抽象层设计方案

> 目标分支：`1.20.1`
> 文档日期：2026-05-31
> 设计目标：为第一阶段运行时 NBT 方案提供统一的库存身份、匹配、虚拟库存与真实执行基础
> 当前状态：第一版正式设计提案

---

## 一、设计目标

本设计不改变已经确认的总体路线：

- `CraftingPlanner` 保留 `Item` 级粗规划职责
- NBT 精度主要落在运行时执行链
- 外部存储当前仅保留扩展点，不构成已承诺实施范围

因此，本设计的目标不是重写整个递归合成系统，而是建立一层新的共享库存抽象，用来替代当前散落在多个类中的：

- `Map<Item, Integer>` 快照
- `countItem(item)` 校验
- `stack.getItem() == item` 扣减
- 各处各自为政的材料匹配逻辑

---

## 二、设计边界

### 2.1 本设计负责

1. 定义运行时材料身份模型
2. 定义身份归一化入口
3. 定义统一材料匹配接口
4. 定义虚拟库存结构
5. 定义真实执行计划桥梁
6. 定义运行时单来源正式执行的一致性要求

### 2.2 本设计暂不负责

1. 不修改 `CraftingPlanner` 的 `Item` 级预计算图
2. 不支持玩家主动指定目标产物 NBT
3. 不接入外部存储网络
4. 不解决多来源跨容器补偿事务

---

## 三、核心设计原则

1. `MaterialKey` 是唯一正式材料身份
2. `MaterialIdentityNormalizer` 是唯一正式身份归一化入口
3. `MaterialMatcher` 是唯一正式材料语义中心
4. `VirtualInventorySnapshot` 是唯一正式虚拟库存表达
5. `ResolvedExecutionPlan` 是从虚拟事务到真实执行的唯一桥梁
6. 预检查、虚拟扣减、正式扣减必须共享同一 matcher 语义
7. 第一阶段只承诺单来源玩家背包路径的一致性

---

## 四、对象级正式提案

### 4.1 `MaterialKey`

#### 4.1.1 目标

`MaterialKey` 用于表示运行时材料身份，是以下行为的统一主键：

- 虚拟库存统计
- 材料匹配
- 虚拟扣减
- 正式扣减
- 缺料分析

#### 4.1.2 推荐定义

```java
public final class MaterialKey {
    private final Item item;
    private final NormalizedMaterialPayload payload;
}
```

#### 4.1.3 约束

1. 必须可稳定 `equals/hashCode`
2. 必须可生成稳定日志表示
3. 不直接持有原始 `ItemStack` 引用
4. 不包含纯瞬时对象身份

#### 4.1.4 第一阶段默认保留项

- `Item`
- 耐久
- 附魔
- 药水内容
- 书内容
- 自定义功能性组件

#### 4.1.5 为什么不直接用 `ItemStack`

不直接使用 `ItemStack` 作为主键，原因：

1. 过重
2. 相等性语义不稳定
3. 会把不该进入身份的瞬时字段带入比较
4. 难以建立统一规范化规则

---

### 4.2 `NormalizedMaterialPayload`

#### 4.2.1 目标

保存**规范化后的功能性载荷**，而不是原始全部组件/NBT 数据。

#### 4.2.2 推荐定义

```java
public final class NormalizedMaterialPayload {
    private final String canonicalVersion;
    private final List<CanonicalField> fields;
}
```

#### 4.2.3 规范化要求

1. 字段顺序稳定
2. 字段名空间固定
3. 数值、文本、布尔、列表、映射都有 canonical form
4. 集合必须明确是“有序列表”还是“无序集合”
5. 缺失字段与默认值是否等价必须显式定义
6. 需要版本字段

#### 4.2.4 第一阶段处理原则

- 功能性字段：保留
- 纯显示性字段：确认无关后可忽略
- 无法确认作用：保留

---

### 4.3 `MaterialIdentityNormalizer`

#### 4.3.1 目标

负责把原始 `ItemStack` 转换为：

- `MaterialKey`
- 或显式归一化失败结果

#### 4.3.2 推荐接口

```java
public interface MaterialIdentityNormalizer {
    NormalizationResult normalize(ItemStack stack);
}
```

```java
public enum NormalizationKind {
    NORMALIZED,
    UNSUPPORTED_MATERIAL_SEMANTICS
}
```

#### 4.3.3 边界

- 归一化失败属于 normalizer 责任
- `MaterialMatcher` 不负责临时兜底归一化失败

---

### 4.4 `IngredientRequirement`

#### 4.4.1 目标

表示 recipe 输入在运行时的可匹配需求。

#### 4.4.2 推荐定义

```java
public final class IngredientRequirement {
    private final List<MaterialKey> exactCandidates;
    private final List<RuntimeMatchClause> runtimeClauses;
}
```

#### 4.4.3 作用

1. 让 recipe 层继续提供候选项
2. 让运行时能表达精确候选与附加匹配约束
3. 为候选排序、命中、回退提供统一输入

#### 4.4.4 `RuntimeMatchClause`

为避免 `runtimeClauses` 只停留在命名层，第一阶段建议明确为：

```java
public final class RuntimeMatchClause {
    private final String clauseId;
    private final ClauseKind kind;
    private final Map<String, String> parameters;
}
```

第一阶段约束：

1. `RuntimeMatchClause` 只承载已被文档化的附加匹配约束
2. 它不是任意脚本执行入口
3. 它的求值上下文限定为：
   - 当前候选 `MaterialKey`
   - 当前 `IngredientRequirement`
4. 若某类约束无法稳定表达为 clause，则直接进入 `UNSUPPORTED_MATERIAL_SEMANTICS`

---

### 4.5 `MaterialMatcher`

#### 4.5.1 目标

作为整个运行时链路的材料语义中心。

#### 4.5.2 推荐接口

```java
public interface MaterialMatcher {
    IngredientRequirement requirementOf(Ingredient ingredient);
    CandidateMatchResult match(MaterialKey candidate, IngredientRequirement requirement);
    RequestLevelResult aggregate(List<CandidateMatchResult> candidateResults);
}
```

#### 4.5.3 候选级结果

```java
public enum CandidateMatchKind {
    MATCHED,
    REJECTED_BY_IDENTITY,
    UNSUPPORTED_MATERIAL_SEMANTICS
}
```

说明：

- `REJECTED_BY_IDENTITY` 表示同类 `Item` 存在，但身份不匹配
- `UNSUPPORTED_MATERIAL_SEMANTICS` 表示当前版本无法稳定判断

#### 4.5.4 请求级结果

```java
public enum RequestLevelKind {
    SATISFIED,
    MISSING,
    UNSUPPORTED
}
```

说明：

- 对外用户可见结果只承诺 `MISSING` 与 `UNSUPPORTED`
- 候选级细分结果用于内部回退、排序与诊断

#### 4.5.5 聚合规则

1. 若存在可继续尝试候选，则继续尝试
2. 若全部候选失败且至少一个为 `UNSUPPORTED_MATERIAL_SEMANTICS`，请求级结果为 `UNSUPPORTED`
3. 否则请求级结果为 `MISSING`

---

### 4.6 `InventorySource`

#### 4.6.1 目标

表示一个真实库存来源。

第一阶段来源：

- 玩家背包

未来来源：

- 原版容器
- 第三方存储

#### 4.6.2 推荐接口

```java
public interface InventorySource {
    Iterable<ItemStack> snapshotStacks();
    boolean consumeAt(int slotIndex, MaterialKey expectedKey, int amount);
    InsertionResult insert(ItemStack stack);
}
```

#### 4.6.3 第一阶段边界

第一阶段不要求 `InventorySource` 自己承担高级选择、预留或补偿事务策略，但必须提供按既定执行计划精确扣减的底层能力。

---

### 4.7 `VirtualInventorySnapshot`

#### 4.7.1 目标

作为运行时搜索阶段的正式虚拟库存表达。

#### 4.7.2 推荐定义

```java
public final class VirtualInventorySnapshot {
    private final Map<MaterialKey, Integer> totals;
    private final Map<Item, List<MaterialKey>> itemIndex;
}
```

#### 4.7.3 作用

1. 保存虚拟库存总量
2. 提供 `Item -> MaterialKey` 辅助索引
3. 作为 recipe 级回退的最小快照单位

---

### 4.8 `InventoryView`

#### 4.8.1 目标

表示一个或多个来源聚合后的统一库存视图。

第一阶段虽然只有玩家背包来源，但接口不能写死成单来源。

#### 4.8.2 推荐接口

```java
public interface InventoryView {
    VirtualInventorySnapshot snapshot(MaterialIdentityNormalizer normalizer);
    ResolvedExecutionPlan planExecution(CraftingTransaction transaction,
                                        MaterialIdentityNormalizer normalizer,
                                        MaterialMatcher matcher);
    ExecutionCommitResult commitExecution(ResolvedExecutionPlan plan,
                                          CraftingTransaction transaction);
    List<InventorySource> sources();
}
```

#### 4.8.3 职责边界

负责：

1. 生成统一虚拟快照
2. 基于当前真实来源生成执行计划
3. 提交真实执行计划
4. 暴露来源集合

不负责：

1. 定义 matcher 语义
2. 管理搜索过程中的业务回退
3. 替代 `TransactionCalculator`

---

### 4.9 `ResolvedExecutionPlan`

#### 4.9.1 目标

把虚拟层已选定的材料身份，绑定到真实库存中的可执行消耗位置。

#### 4.9.2 推荐定义

```java
public final class ResolvedExecutionPlan {
    private final List<ResolvedConsumption> consumptions;
}
```

```java
public final class ResolvedConsumption {
    private final InventorySource source;
    private final int slotIndex;
    private final MaterialKey key;
    private final int amount;
}
```

#### 4.9.3 作用

1. 绑定虚拟事务与真实库存位置
2. 允许正式提交前 revalidate
3. 防止正式执行时重新扫描并选到另一组栈
4. 不再持有独立输出列表，避免与事务对象形成双重事实来源

---

### 4.10 `CraftingTransaction`

#### 4.10.1 目标

表达逻辑层面的事务结果，而不是直接承担真实执行细节。

#### 4.10.2 推荐定义

```java
public final class CraftingTransaction {
    private final Map<MaterialKey, Integer> needs;
    private final List<ItemStack> resolvedOutputs;
}
```

#### 4.10.3 说明

当前明确不再采用：

- `provides: Map<MaterialKey, Integer>`

作为第一阶段最终产物表达。

原因：

- 输入侧需要材料身份聚合
- 输出侧第一阶段跟随 recipe 真实输出更合理
- 否则事务对象更像结算摘要，不像可执行计划

#### 4.10.4 执行目标

后续执行方向应从：

```java
execute(Player player)
```

演进为：

```java
execute(ResolvedExecutionPlan plan,
        CraftingTransaction transaction,
        InventoryView inventoryView)
```

---

## 五、运行时流程

### 5.1 预检查与虚拟搜索

1. `InventoryView.snapshot()` 生成 `VirtualInventorySnapshot`
2. `MaterialIdentityNormalizer` 生成材料身份
3. `MaterialMatcher.requirementOf()` 生成输入需求
4. `TransactionCalculator` 在虚拟库存上做候选尝试
5. ingredient 可试探，recipe 是最小提交/回退单元

### 5.2 正式执行

1. 基于 `CraftingTransaction` 与当前真实库存生成 `ResolvedExecutionPlan`
2. 提交前 revalidate 所有 `ResolvedConsumption`
3. 若 revalidate 失败，则整笔执行失败且不发生部分扣减
4. `InventoryView.commitExecution()` 负责按 `ResolvedConsumption` 调用来源侧 `consumeAt()` 完成正式扣减
5. 若输入扣减全部成功，则按 `CraftingTransaction.resolvedOutputs` 进入输出投放
6. 输出插入失败不触发输入回滚，进入既定降级路径

---

## 六、真实执行原子性

第一阶段明确采用：

- **输入扣减原子性 + 产物投放降级容错**

具体语义：

1. 输入侧必须“先验证后提交”
2. 若正式提交前库存状态变化，则整笔失败且不发生部分扣减
3. 产物插入失败不回滚输入扣减，而使用既定 fallback
4. 第一阶段只承诺单来源玩家背包路径一致性
5. 不承诺多来源跨容器补偿事务

第一阶段 fallback 明确定义为：

- 背包插入失败时，按当前项目既有语义掉落到世界

---

## 七、失败语义

### 7.1 内部候选级语义

- `MATCHED`
- `REJECTED_BY_IDENTITY`
- `UNSUPPORTED_MATERIAL_SEMANTICS`

### 7.2 对外请求级语义

- `MISSING`
- `UNSUPPORTED`

### 7.3 可观察载体要求

第一阶段要求：

1. 请求级结果必须体现在统一返回对象或统一错误语义中
2. 命令、网络、GUI 共用同一请求级结果
3. 测试断言以统一请求级结果为准

第一阶段不要求：

- 为内部候选级结果单独做 UI 展示

---

## 八、与当前代码的映射关系

### 8.1 第一批改动区域

- `common/src/main/java/xczl/recursivecraft/utils/InventoryUtils.java`
- `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java`
- `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java`
- `common/src/main/java/xczl/recursivecraft/data/CraftingTransaction.java`

### 8.2 第一阶段尽量不改的区域

- `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java`
- GUI 主体交互层
- 命令与网络协议的目标物品表达

---

## 九、测试与验收要求

### 9.1 必须覆盖的测试类型

1. 同一 `Item` 不同 NBT 的匹配区分
2. 输入侧正确命中、错误输入侧不命中
3. 虚拟库存扣减与正式执行结果一致
4. recipe 失败回退后虚拟库存无污染
5. “缺料”与“不支持”语义区分
6. `ResolvedExecutionPlan` revalidate 失败时无部分扣减

### 9.2 第一阶段回归重点

- 旧的纯 `Item` 场景不能退化
- JEI 强制 recipe 路径不能被 matcher 破坏
- planner 给出的默认候选在 NBT 场景下可被运行时回退

---

## 十、当前不展开的课题

1. 目标产物 NBT 主动指定
2. 外部存储来源聚合策略
3. 第三方存储兼容层
4. planner 是否需要局部感知 NBT

这些课题保留到第一阶段运行时 NBT 路径稳定后再评估。

---

## 十一、设计定稿门槛

本设计提案要从“正式提案”升级到“定稿”，至少需满足：

1. 对象职责没有明显重叠或空洞
2. 候选级与请求级语义闭合
3. 正式执行原子性要求明确
4. `ResolvedExecutionPlan` 作为虚拟到真实执行桥梁被接受
5. 输出侧不再保留未定分支表达

一旦上述条件满足，`Phase 1` 就具备收口条件。 
