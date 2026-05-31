# RecursiveCraft NBT 前置机制梳理与影响分析

> 目标分支：`1.20.1`
> 产出日期：2026-05-31
> 分析方式：主线人工梳理 + 两个独立子代理隔离分析后交叉比对

---

## 一、结论摘要

当前 RecursiveCraft 的核心机制可以概括为：

1. 服务端启动时预计算“物品级”的最优合成路径与理论成本
2. 运行时根据玩家请求与玩家背包快照，递归求出一笔“净变化事务”
3. 最终直接对玩家背包执行净扣减与净发放，而不是逐级真实执行每一级配方

两份独立分析都确认：当前系统最关键的假设是“库存身份 = `Item` + 数量”，而不是“具体 `ItemStack` 实例”。因此，NBT 方案真正冲击的不是单一分支，而是：

- 库存快照
- 虚拟库存推进
- 事务记录
- 正式扣减
- 入口表达能力

另一方面，两份分析也一致认为：**若 NBT 方案只升级运行时库存模型，而暂时不把启动预计算升级为 NBT 节点图，那么服务端启动阶段的复杂度通常不会出现同等级膨胀。**

---

## 二、当前完整机制

### 2.1 全局时序

#### 阶段 A：模组初始化与规划准备

1. `RecursiveCraft.init()` 加载配置、注册方块/物品/菜单/网络/命令。
2. 在 `LifecycleEvent.SERVER_STARTING` 中创建后台线程。
3. 后台线程调用 `CraftingPlanner.buildOptimalPathTree(recipeManager)`。
4. `CraftingPlanner` 扫描全部普通 crafting 配方，构建：
   - `pathMemo`
   - `costMemo`
   - `recipeLookup`
5. 规划结果通过不可变 `PlanningResult` 快照发布。

关键证据：

- `common/src/main/java/xczl/recursivecraft/RecursiveCraft.java:27`
- `common/src/main/java/xczl/recursivecraft/RecursiveCraft.java:45`
- `common/src/main/java/xczl/recursivecraft/core/CraftingPlanner.java:91`

#### 阶段 B：玩家入口与请求发送

当前存在三类主要入口：

1. 方块入口
   - `RecursiveCrafterBlock.use()` 打开 `RecursiveCrafterMenu`
2. 手持入口
   - `HandheldCrafterItem.use()` 打开同一菜单
3. 命令入口
   - `/craft_recursive item amount`

此外还有 JEI 入口：

- `RecursiveCraftTransferHandler.transferRecipe()` 在按住 Ctrl 时发送递归合成请求，并附带 `recipeId`

关键证据：

- `common/src/main/java/xczl/recursivecraft/block/RecursiveCrafterBlock.java:32`
- `common/src/main/java/xczl/recursivecraft/item/HandheldCrafterItem.java:25`
- `common/src/main/java/xczl/recursivecraft/command/RecursiveCraftCommand.java:19`
- `common/src/main/java/xczl/recursivecraft/compat/jei/RecursiveCraftTransferHandler.java:73`

#### 阶段 C：客户端列表展示与请求发包

客户端界面 `RecursiveCrafterScreen` 会：

1. 从 `CraftableItemList` 加载 `pathMemo.keySet()` 作为可递归合成物品列表
2. 支持搜索、分页、收藏与选中目标物品
3. 点击执行按钮后发送 `C2SExecuteCraftPacket(selectedItem, amount)`

关键证据：

- `common/src/main/java/xczl/recursivecraft/client/RecursiveCrafterScreen.java:64`
- `common/src/main/java/xczl/recursivecraft/client/RecursiveCrafterScreen.java:114`

#### 阶段 D：服务端统一执行

无论是命令还是网络包，最终都会进入 `CraftingTaskExecutor.tryExecute()`：

1. 请求合法性校验
2. `CraftingPlanner` ready 校验
3. 顶层配方解析
4. 调用 `TransactionCalculator` 进行递归事务计算
5. 检查目标净产出是否足够
6. 检查基础材料是否足够
7. 调用 `CraftingTransaction.execute()` 落地事务

关键证据：

- `common/src/main/java/xczl/recursivecraft/core/CraftingTaskExecutor.java:37`
- `common/src/main/java/xczl/recursivecraft/networking/C2SExecuteCraftPacket.java:52`

#### 阶段 E：递归事务计算

`TransactionCalculator.calculate()` 的实际工作不是逐级真实合成，而是：

1. 将玩家背包快照为 `virtualInventory`
2. 递归展开目标配方
3. 优先消耗虚拟库存已有成品
4. 再为不足部分选择候选配方并递归展开
5. 聚合生成 `CraftingTransaction`

关键证据：

- `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:64`
- `common/src/main/java/xczl/recursivecraft/core/TransactionCalculator.java:79`

#### 阶段 F：净变化执行

`CraftingTransaction.execute()`：

1. 先计算 `netDeltas`
2. 按净需求从玩家背包中扣除输入
3. 按净产出发放结果
4. 背包放不下则掉落

关键证据：

- `common/src/main/java/xczl/recursivecraft/data/CraftingTransaction.java:62`
- `common/src/main/java/xczl/recursivecraft/data/CraftingTransaction.java:85`

---

## 三、关键操作与关键数据结构

### 3.1 规划层

#### 关键类

- `CraftingPlanner`
- `CraftingPlanner.PlanningResult`

#### 关键数据结构

- `Map<Item, CraftingRecipe> pathMemo`
- `Map<Item, Double> costMemo`
- `Map<Item, List<CraftingRecipe>> recipeLookup`

#### 关键算法

- 全 recipe 索引
- 基础物品初始化成本
- Bellman-Ford 风格收敛
- rescue 阶段
- 再收敛并发布快照

### 3.2 执行层

#### 关键类

- `CraftingTaskExecutor`
- `TransactionCalculator`
- `CraftingTransaction`

#### 关键数据结构

- `CalcContext.virtualInventory: Map<Item, Integer>`
- `CalcContext.recursionStack: Set<Item>`
- `uncraftableCache: Set<Item>`
- `needs: Map<Item, Integer>`
- `provides: Map<Item, Integer>`
- `netDeltas: Map<Item, Integer>`

### 3.3 交互层

#### 关键类

- `RecursiveCrafterScreen`
- `CraftableItemList`
- `RecursiveCrafterMenu`
- `RecursiveCrafterBlock`
- `HandheldCrafterItem`
- `RecursiveCraftTransferHandler`

---

## 四、当前算法梳理

### 4.1 预计算算法

当前预计算的节点是 `Item`，而不是 `ItemStack`。

核心步骤：

1. 遍历全部普通 crafting 配方
2. 建立产物到候选配方的映射
3. 将全注册表 `Item` 加入 `allItems`
4. 无配方产出的物品成本设为 `1.0`
5. 多轮松弛每个 recipe 的理论成本
6. 对 tag / 多选输入总是取“最便宜候选”

这意味着预计算得到的是：

- 某个产物“理论上最便宜”的配方
- 但不是“当前背包上下文下必定可行”的配方

### 4.2 运行时递归算法

运行时更接近“搜索 + 虚拟库存模拟”：

1. 检查深度与循环
2. 优先吃掉虚拟库存中的现有成品
3. 若仍不足，收集候选配方
4. 先尝试浅层可满足且理论成本更优的 recipe
5. 对每个 ingredient 再递归求解
6. 成功则提交虚拟库存快照，失败则尝试下一个候选

### 4.3 最终执行算法

最终执行阶段不会还原完整的多级 craft 过程，而是只看最终净变化：

- 需要消耗什么
- 需要产出什么

这也是当前系统效率高、实现简单的主要原因之一。

---

## 五、两份独立分析的交叉结论

### 5.1 一致点

两份独立分析一致确认：

1. 当前系统的主身份模型是 `Item`，不是 `ItemStack`
2. NBT 问题的根源是“库存被压平成 `Item/count`”
3. 运行时链路比预计算层更先受到 NBT 方案冲击
4. 若预计算仍保持 `Item` 粒度，启动期复杂度不会因 NBT 自动爆炸
5. 若预计算也升级为 NBT 节点图，则图规模与复杂度存在明显膨胀风险

### 5.2 互补点

第一份分析更强调：

- 完整时序
- 递归事务并非真实逐级 craft
- planner 与运行时的职责边界

第二份分析更强调：

- 哪些具体结构已经把物品压平
- 若升级 NBT 友好模型，哪些数据结构必须先改
- 入口协议是否也要升级

综合后可以更清晰地下结论：**NBT 前置工作的关键，不是先讨论某个匹配细节，而是先确认哪些层必须从 `Item` 身份迁移出来。**

---

## 六、NBT 方案会影响什么

### 6.1 会明显变化的部分

#### 1. 库存快照

当前：

- `InventoryUtils.snapshot()` 输出 `Map<Item, Integer>`

若支持 NBT：

- 需要升级为某种 `StackKey -> count` 或等价抽象

#### 2. 虚拟库存推进

当前：

- `virtualInventory` 只按 `Item` 计数

若支持 NBT：

- 必须能区分不同 NBT 的同一物品实例

#### 3. 事务模型

当前：

- `needs/provides/netDeltas` 均为 `Map<Item, Integer>`

若支持 NBT：

- 必须能表达“消耗哪类实例”

#### 4. 执行层扣减

当前：

- `stack.getItem() == item` 即可扣减

若支持 NBT：

- 必须采用统一匹配规则，而不是只看 `Item`

#### 5. 入口表达能力

当前：

- GUI、命令、网络包目标物品都是 `Item`

若未来需要“指定目标产物的 NBT 版本”，入口协议也需要升级

### 6.2 可以暂时不变的部分

#### 1. 服务端启动时机

仍可在 `SERVER_STARTING` 中做预热。

#### 2. 规划线程与快照发布模型

线程模型与不可变结果发布机制本身并不依赖 `Item` 粒度。

#### 3. Bellman-Ford 风格收敛框架

算法框架本身仍可保留，问题在于“节点定义”是否升级。

---

## 七、关于启动预计算是否会因 NBT 膨胀恶化

### 7.1 若仅运行时支持 NBT，预计算仍按 Item

一般不会同等级恶化。

原因：

1. 当前预计算扫描的是 crafting recipe 与注册表 item，不是玩家实际库存状态
2. `allItems` 当前来自配方输出和 `BuiltInRegistries.ITEM`
3. 成本表和候选表当前都按 `Item` 聚合

这意味着启动阶段的节点规模仍近似由：

- recipe 数量
- item 注册表大小

决定，而不是由实际 NBT 变体数量决定。

### 7.2 若预计算也升级为 NBT 节点

风险会明显升高，甚至可能失去“可穷举预构建”的前提。

原因：

1. NBT 空间通常不是有限静态枚举
2. 一些变体取决于玩家行为、模组状态或运行期数据
3. 同一 `Item` 的等价类会被切裂为多个节点
4. `pathMemo/costMemo/recipeLookup` 都可能按变体数膨胀

因此，当前阶段不推荐直接把预计算层升级到完整 NBT 图。

---

## 八、推荐的风险分层

### L1：局部止血

- 只修运行时最明显的误吃误给
- 不改预计算
- 风险最低，但精度有限

### L2：运行时身份升级

- 保持预计算按 `Item`
- 运行时库存、事务、匹配、执行升级为 NBT 友好
- 这是当前最推荐的主路线

### L3：入口协议升级

- 允许请求特定 NBT 目标产物
- 需要改 GUI、命令、网络序列化

### L4：预计算图升级

- 让 planner 也按 NBT 节点工作
- 风险最高
- 当前不建议直接进入

---

## 九、对后续 NBT 设计的直接启示

1. 不要把 NBT 方案当成局部补丁
2. 先把当前完整机制与分层边界确认清楚
3. 第一轮优先改运行时库存模型，而不是先改 planner
4. 若未来要支持外部存储，应复用同一库存抽象层，不另起一套模型

---

## 十、最终建议

对于当前项目，NBT 的前置工作已经足够明确：

- 现有系统是“Item 级预计算 + Item 级运行时事务”
- 真正阻塞 NBT 的是运行时库存与事务模型
- 预计算层目前不是第一优先改造对象

因此，后续正式 NBT 设计建议以如下前提展开：

1. 先定义共享库存抽象层
2. 先升级运行时库存与事务身份模型
3. 暂时保留 `CraftingPlanner` 的 `Item` 级预计算
4. 待运行时路径稳定后，再决定 planner 是否需要有限度地感知 NBT
