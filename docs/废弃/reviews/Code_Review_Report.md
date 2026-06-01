# RecursiveCraft 代码审阅报告

> 审阅日期：2026-04-04
> 审阅范围：common 模块全部 Java 源码（30 个文件）、构建配置、测试代码
> 目标版本：0.70 (Minecraft 1.20.1, Architectury 9.1.12)

---

## 一、总体评价

RecursiveCraft 是一个功能完整、架构基本合理的 Minecraft 合成辅助 Mod。核心算法（Bellman-Ford 式成本收敛 + 快照回溯模拟）思路清晰，解决了递归合成中循环依赖、多候选配方选择等难题。代码注释充分，可读性较好。

但随着功能迭代（JEI 集成、指定配方、手持模式等），代码中积累了一些**架构腐化**和**潜在风险**，需要在下一轮重构中解决。以下按优先级从高到低排列。

---

## 二、严重问题（P0 — 必须修复）

### 2.1 线程安全：`CraftingPlanner` 裸线程 + 无同步保护

**文件：** `RecursiveCraft.java:47-49`, `CraftingPlanner.java`

```java
new Thread(() -> {
    CraftingPlanner.getInstance().buildOptimalPathTree(server.getRecipeManager());
}).start();
```

**问题：**
1. `buildOptimalPathTree()` 在后台线程中写入 `pathMemo`、`costMemo`、`recipeLookup`（全部是普通 `HashMap`），而主线程/网络线程随时可能读取这些 Map（通过 `TransactionCalculator`、`CraftingTaskExecutor`）。这构成了**数据竞争**，可能导致 `ConcurrentModificationException` 或读到不一致状态。
2. `isReady` 虽然是 `volatile`，但它只保护了"是否完成"这一个布尔值，不保护 Map 内容的可见性。在 Java 内存模型下，另一线程看到 `isReady == true` 时，不保证能看到 Map 的最终状态（除非 Map 的写入 happens-before `isReady` 的写入，且读取端先读 `isReady` 再读 Map — 当前代码没有严格保证这一点）。
3. 裸 `new Thread()` 没有异常处理，若 `buildOptimalPathTree()` 抛出未捕获异常，线程静默死亡，`isReady` 永远为 `false`，玩家只看到"正在初始化"。

**建议：**
- 使用 `Collections.unmodifiableMap()` + 原子替换引用（或使用 `ConcurrentHashMap`）。
- 写入完成后通过 `volatile` 写入触发 happens-before 关系（把 Map 引用本身设为 volatile 并在最后一步赋值）。
- 使用带名称的线程（`new Thread(..., "RecursiveCraft-Planner")`）并添加 `UncaughtExceptionHandler`。
- 或者更简洁地：在后台线程构建好所有结果后，一次性发布一个不可变的 `PlanningResult` 对象，通过 `volatile` 引用替换。

### 2.2 安全漏洞：网络包缺乏服务端校验

**文件：** `C2SExecuteCraftPacket.java`, `CraftingTaskExecutor.java`

**问题：**
1. `amount` 字段没有上限校验。恶意客户端可以发送 `amount = Integer.MAX_VALUE`，触发极深的递归计算（`MAX_DEPTH = 30` 可以一定程度缓解，但大量物品的事务计算本身就很耗时），可能造成**服务端 DoS**。
2. `forcedRecipeId` 虽然做了产物匹配校验，但没有检查该配方是否为 `isSpecial()`（特殊配方通常不应被强制使用）。
3. `CraftingTaskExecutor.tryExecute()` 没有权限检查。通过网络包，任何普通玩家都可以使用递归合成功能，但命令 `/craft_recursive` 却要求 OP 权限——两者权限模型不一致。

**建议：**
- 对 `amount` 加上合理上限（如 `Math.min(amount, 64 * 36)` 即一背包上限）。
- 在 `resolveRecipe()` 中增加 `isSpecial()` 检查。
- 统一权限模型：要么都不检查权限（合成器本身即为"权限"），要么都检查。

### 2.3 `CostMap` 的 `INFINITE_COST` 单例被意外修改风险

**文件：** `CostMap.java:17, 43-46`

```java
public static final CostMap INFINITE_COST = new CostMap(true);

public void add(CostMap other) {
    if (this == INFINITE_COST || other == INFINITE_COST) return;
    other.baseMaterials.forEach(this::addMaterial);
}
```

`INFINITE_COST` 是一个全局单例，但 `baseMaterials` 是一个可变的 `HashMap`。虽然 `add()` 做了 `this == INFINITE_COST` 的短路检查，但如果未来有人直接调用 `INFINITE_COST.addMaterial(...)` 或 `INFINITE_COST.multiply(2)` —— `multiply()` 不检查 `this == INFINITE_COST`，只在第一行做了检查——就会污染这个单例。

**建议：** 让 `INFINITE_COST` 的 `baseMaterials` 使用 `Collections.unmodifiableMap()` 或在所有修改方法中统一检查。

---

## 三、重要问题（P1 — 强烈建议修复）

### 3.1 `TransactionCalculator` 的策略模式过度设计

**文件：** `TransactionCalculator.java:31-43, 405-429`

定义了三个策略接口 `RecipeCandidateStrategy`、`IngredientOptionStrategy`、`SatisfactionPolicy`，但：
1. 每个接口只有一个实现类（`DefaultXxx`），且这些实现类仅仅是转发调用外部类的同名私有方法。
2. 策略对象在构造函数中硬编码创建，没有注入点。
3. 这些策略类没有提供任何可测试性或可扩展性的实际价值。

**建议：** 删除策略接口和默认实现类，直接调用私有方法。如果未来确实需要策略模式（如不同的合成算法），届时再引入。同时清理重复方法（如 `collectCandidateRecipes()` 和 `DefaultRecipeCandidateStrategy.collectCandidates()` 完全等价）。

### 3.2 `CraftingPlanner` 的 `CostMap` 未被实际使用

**文件：** `CraftingPlanner.java:45-46, 170-183`

`costMemo` 存储了每个物品的 `CostMap`，但 `finalizePlanningResult()` 中只是将其设为 `new CostMap(item, finalCost)` —— 一个仅包含物品自身和 minCostTable 数值的 CostMap。这里的 `CostMap` 并没有真正追踪基础材料的组成（"1 个木棍 = 0.5 个木板 = 0.125 个原木"的分解链），只是包装了一个 `double` 值。

`TransactionCalculator` 中对 `CostMap` 的使用也仅限于 `getCost()` 方法取 `getTotalItemCost()`，本质上就是一个 `Map<Item, Double>`。

**建议：** 要么让 `CostMap` 真正追踪材料分解链（这需要在 `calculateRecipeCost` 阶段构建），要么简化为 `Map<Item, Double> costMemo`，避免 `CostMap` 类给人造成错误的高级印象。

### 3.3 `ModConfig` 配置项与功能完全无关

**文件：** `ModConfig.java`

配置项包括 `logDirtBlock`、`magicNumber`、`magicNumberIntroduction`、`items` —— 这些看起来是模板或占位符代码，与 RecursiveCraft 的实际功能没有任何关系。没有任何代码读取这些配置值来影响合成行为。

**建议：** 
- 删除占位符配置项。
- 加入有意义的配置项，如：最大合成数量限制、黑名单物品、是否允许非 OP 使用命令、日志详细程度等。
- 考虑迁移到更现代的配置方案（如 Cloth Config API 或 TOML）。

### 3.4 `RecursiveCrafterScreen` 中的 UI 逻辑过于紧凑

**文件：** `RecursiveCrafterScreen.java` (301 行)

Screen 类同时承担了：
1. 物品数据管理（加载、过滤、排序）
2. 搜索逻辑
3. 收藏逻辑
4. 分页逻辑
5. 网格渲染
6. 点击事件处理

**建议：** 将物品列表管理（过滤、排序、分页）抽取为独立的 `ItemListModel` 或类似的数据层类，Screen 只负责渲染和事件转发。这也有利于未来添加单元测试。

### 3.5 `MixinTitleScreen` 为调试残留

**文件：** `mixin/MixinTitleScreen.java` (探索得知为空/debug mixin)

Mixin 注入到 `TitleScreen.init()` 中但只有调试日志输出。这不应该出现在发布版本中。

**建议：** 删除该 Mixin 及其在 `recursivecraft-common.mixins.json` 中的注册。

---

## 四、一般问题（P2 — 建议改进）

### 4.1 硬编码的中文字符串

**涉及文件：** `CraftingTaskExecutor.java`, `RecursiveCraftTransferHandler.java`, `RecursiveCrafterScreen.java`

大量用户可见的字符串（如 `"§c合成系统正在初始化，请稍候..."`、`"§c缺少材料"`、`"执行合成"` 等）直接硬编码为中文。这阻碍了国际化。

**建议：** 统一使用 `Component.translatable()` 配合语言文件 `zh_cn.json` / `en_us.json`。

### 4.2 `PinyinUtils` 每次搜索都重新计算拼音

**文件：** `PinyinUtils.java`, `RecursiveCrafterScreen.java:142-152`

`onSearchUpdate()` 每次搜索时对所有物品调用 `PinyinUtils.matches()`，而 `matches()` 内部对每个物品名称调用 `toInitials()` 和 `toFullPinyin()`（各自遍历每个字符并查询 Pinyin4j）。物品数量通常在 800~1200+，每次按键都会触发全量重算。

**建议：** 预计算每个物品的拼音首字母和全拼，缓存在 `Map<Item, SearchableEntry>` 中，搜索时直接匹配缓存值。

### 4.3 `snapshotPlayerInventory()` 重复实现

**文件：** `TransactionCalculator.java:287-296`, `CraftingTaskExecutor.java:177-186`

两处几乎相同的库存快照逻辑。

**建议：** 抽取为共享的工具方法，如 `InventoryUtils.snapshot(Inventory)`。

### 4.4 `CraftingTransaction.execute()` 中的物品堆叠处理

**文件：** `CraftingTransaction.java:122-123`

```java
int maxStackSize = new ItemStack(item).getMaxStackSize();
```

每次循环都创建一个临时 `ItemStack` 仅为获取 `maxStackSize`。虽然影响微小，但可以用 `item.getMaxStackSize()` 替代（1.20.1 中 Item 类有此方法）。

### 4.5 `consumeFromVirtualInventory` 返回值语义模糊

**文件：** `TransactionCalculator.java:307-318`

该方法返回 `amountInInventory`（库存中该物品的总数），而非实际消耗的数量。调用方用 `amount - amountInInventory` 计算剩余需求，当 `amountInInventory > amount` 时结果为负数（被 `<= 0` 检查拦住了），但语义不清晰。

**建议：** 返回 `Math.min(amount, amountInInventory)` 即实际消耗量，调用方改为 `amount - consumed`。

### 4.6 `Ingredient.toString()` 作为聚合 Key 不可靠

**文件：** `TransactionCalculator.java:186`

```java
String key = ingredient.toString();
```

`Ingredient.toString()` 的输出依赖 Minecraft 内部实现，不保证两个语义相同的 Ingredient 产生相同的字符串，也不保证跨版本稳定。

**建议：** 自行构建一个基于 Ingredient 内容（`getItems()` 排序后的 Item ID 拼接）的稳定 Key。

### 4.7 缺少英文本地化文件 `en_us.json`

当前只有 `zh_cn.json`，没有 `en_us.json`（Minecraft 默认语言）。英语用户看到的将是翻译 Key 而非可读名称。

**建议：** 添加 `en_us.json` 作为默认语言文件。

---

## 五、架构建议（重构方向）

### 5.1 核心三件套的依赖关系梳理

当前核心类之间的依赖关系：

```
CraftingPlanner (单例)
   ├── pathMemo: Map<Item, CraftingRecipe>
   ├── costMemo: Map<Item, CostMap>
   └── recipeLookup: Map<Item, List<CraftingRecipe>>

TransactionCalculator
   ├── 直接引用 CraftingPlanner.getInstance() 获取 pathMemo, costMemo
   └── 直接引用 CraftingPlanner.getInstance().getRecipesFor()

CraftingTaskExecutor (全静态方法)
   ├── 直接引用 CraftingPlanner.isReady
   ├── 直接引用 CraftingPlanner.getInstance().getPathMemo()
   └── 创建 TransactionCalculator
```

**问题：** `CraftingPlanner` 同时充当数据仓库和算法执行器，其他类都直接耦合到它的单例。

**重构建议：**
1. 将 `CraftingPlanner` 拆分为：
   - `RecipeAnalyzer`：负责算法执行（`buildOptimalPathTree`），仅在初始化时运行。
   - `RecipeDatabase`（或 `CraftingPlan`）：持有 `pathMemo`、`costMemo`、`recipeLookup` 的不可变快照，作为值对象传递。
2. `TransactionCalculator` 和 `CraftingTaskExecutor` 通过构造参数接收 `RecipeDatabase`，而非直接访问单例。
3. 这样不仅解决线程安全问题，还使得单元测试可以注入 mock 的 `RecipeDatabase`。

### 5.2 事务模型优化

当前 `CraftingTransaction` 只记录"毛需求"和"毛产出"，通过 `getNetDeltas()` 计算净值。这个模型对于调试和复杂失败诊断不够友好。

**建议：** 考虑引入树形事务结构：
```
CraftingPlan
  └── steps: List<CraftingStep>
        ├── recipe: CraftingRecipe
        ├── runs: int
        ├── inputs: Map<Item, Integer>
        ├── outputs: Map<Item, Integer>
        └── subSteps: List<CraftingStep>  // 子材料的合成步骤
```

这样可以：
- 向玩家展示完整的合成步骤树
- 精确定位失败发生在哪一步
- 为 GUI 提供合成步骤预览

### 5.3 测试覆盖率

当前测试仅覆盖了 `TransactionCalculator`、`CostMap`、`CraftingTransaction` 三个类。以下是建议补充测试的优先级：

| 优先级 | 目标类 | 测试重点 |
|--------|--------|----------|
| P0 | `CraftingPlanner` | 循环配方的成本收敛、Rescue 阶段、迭代终止条件 |
| P0 | `CraftingTaskExecutor` | 权限校验、边界值（amount=0, MAX_VALUE）、forcedRecipe 校验 |
| P1 | `C2SExecuteCraftPacket` | 编解码一致性、恶意输入 |
| P2 | `PinyinUtils` | 多音字、非中文字符、空输入 |

---

## 六、问题汇总表

| 编号 | 优先级 | 类别 | 描述 | 涉及文件 |
|------|--------|------|------|----------|
| 2.1 | P0 | 线程安全 | CraftingPlanner 裸线程 + 无同步保护 | RecursiveCraft.java, CraftingPlanner.java |
| 2.2 | P0 | 安全 | 网络包缺乏服务端校验（amount 无上限、权限不一致） | C2SExecuteCraftPacket.java, CraftingTaskExecutor.java |
| 2.3 | P0 | 正确性 | INFINITE_COST 单例可被意外修改 | CostMap.java |
| 3.1 | P1 | 过度设计 | TransactionCalculator 策略模式无实际价值 | TransactionCalculator.java |
| 3.2 | P1 | 设计缺陷 | CostMap 未真正追踪材料链，退化为 double 包装 | CraftingPlanner.java, CostMap.java |
| 3.3 | P1 | 残留代码 | ModConfig 全为占位符配置，与功能无关 | ModConfig.java |
| 3.4 | P1 | 职责混乱 | RecursiveCrafterScreen 承担过多职责 | RecursiveCrafterScreen.java |
| 3.5 | P1 | 残留代码 | MixinTitleScreen 为调试残留 | MixinTitleScreen.java |
| 4.1 | P2 | 国际化 | 大量硬编码中文字符串 | 多个文件 |
| 4.2 | P2 | 性能 | 拼音搜索每次全量重算 | PinyinUtils.java, RecursiveCrafterScreen.java |
| 4.3 | P2 | 重复代码 | snapshotPlayerInventory() 重复实现 | TransactionCalculator.java, CraftingTaskExecutor.java |
| 4.4 | P2 | 微优化 | 不必要的临时 ItemStack 创建 | CraftingTransaction.java |
| 4.5 | P2 | 可读性 | consumeFromVirtualInventory 返回值语义模糊 | TransactionCalculator.java |
| 4.6 | P2 | 健壮性 | Ingredient.toString() 作为聚合 Key 不可靠 | TransactionCalculator.java |
| 4.7 | P2 | 国际化 | 缺少 en_us.json 默认语言文件 | resources/assets/ |

---

## 七、推荐重构路线图

### 阶段一：修复安全与稳定性（P0）
1. 修复 CraftingPlanner 线程安全（引入不可变结果对象）
2. 添加网络包服务端校验（amount 上限、权限检查）
3. 保护 INFINITE_COST 单例不可变性

### 阶段二：架构清理（P1）
4. 拆分 CraftingPlanner → RecipeAnalyzer + RecipeDatabase
5. 移除 TransactionCalculator 中的过度设计
6. 清理 ModConfig（替换为实际配置项）
7. 清理 MixinTitleScreen
8. 拆分 RecursiveCrafterScreen 的数据层

### 阶段三：完善与优化（P2）
9. 国际化：硬编码字符串迁移到语言文件
10. 拼音搜索缓存优化
11. 消除重复代码
12. 补充测试覆盖率

---

*本报告可作为后续创建重构任务/Issue 的依据。建议按阶段逐步推进，每个阶段完成后验证功能回归。*
