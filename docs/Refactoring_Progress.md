# RecursiveCraft 重构进度板

> 基于 `docs/Code_Review_Report.md` 审阅报告执行重构
> 原则：不做功能性修改，仅重构。每阶段完成后编译验证、提交并推送。

---

## 阶段一：安全与稳定性（P0）— ✅ 已完成并推送

commit: `08e46d2` (branch: `1.21.1`)

| 编号 | 任务 | 状态 | 关键改动 |
|------|------|------|----------|
| 2.1 | CraftingPlanner 线程安全 | ✅ | 引入不可变 `PlanningResult` 内部类（持有 `pathMemo`/`costMemo`/`recipeLookup` 的不可变快照），通过 `volatile` 引用一次性发布；`buildOptimalPathTree()` 的所有中间状态改为方法局部变量；启动线程添加名称 `"RecursiveCraft-Planner"` 和 `UncaughtExceptionHandler` |
| 2.2 | 网络包服务端校验 | ✅ | `CraftingTaskExecutor.isValidRequest()` 增加 `amount` 上限校验；`resolveRecipe()` 增加 `isSpecial()` 配方过滤 |
| 2.3 | INFINITE_COST 不可变性 | ✅ | `CostMap` 的 `INFINITE_COST` 单例的 `baseMaterials` 改用 `Collections.unmodifiableMap()`，防止意外写入 |

### API 变更（阶段一引入，后续阶段需遵守）

| 旧 API | 新 API |
|--------|--------|
| `CraftingPlanner.isReady` (static volatile boolean) | `CraftingPlanner.getInstance().isReady()` |
| `CraftingPlanner.getInstance().getPathMemo()` | `CraftingPlanner.getInstance().getResult().getPathMemo()` |
| `CraftingPlanner.getInstance().getCostMemo()` | `CraftingPlanner.getInstance().getResult().getCostMemo()` |
| `CraftingPlanner.getInstance().getRecipesFor(item)` | `CraftingPlanner.getInstance().getResult().getRecipesFor(item)` |

---

## 阶段二：架构清理（P1）— 🔧 代码已改完，未编译验证、未提交

| 编号 | 任务 | 状态 | 关键改动 |
|------|------|------|----------|
| 3.1 | 移除 TransactionCalculator 策略模式 | ✅ | 删除 `RecipeCandidateStrategy`/`IngredientOptionStrategy`/`SatisfactionPolicy` 三个接口及 `DefaultXxx`/`NetDeltaXxx` 三个实现类；所有调用改为直接调用同名私有方法 |
| 3.2 | 简化 CostMap → `Map<Item,Double>` | ✅ | `PlanningResult.costMemo` 类型从 `Map<Item, CostMap>` 改为 `Map<Item, Double>`；`TransactionCalculator.getCost()` 直接读 `Double`；**删除** `CostMap.java` 和 `CostMapTest.java` |
| 3.3 | 清理 ModConfig 占位符 | ✅ | 删除 `logDirtBlock`/`magicNumber`/`magicNumberIntroduction`/`items` 等占位符配置项及相关的 `parseItems()`/`BuiltInRegistries` 依赖；新增有实际意义的 `maxCraftAmount`（默认 2304）；`CraftingTaskExecutor` 改为引用 `ModConfig.maxCraftAmount` 而非硬编码常量 |
| 3.5 | 删除 MixinTitleScreen 调试残留 | ✅ | 删除 `MixinTitleScreen.java`；`recursivecraft-common.mixins.json` 的 `client` 数组清空 |
| 3.4 | 拆分 RecursiveCrafterScreen 数据层 | ✅ | 新建 `CraftableItemList.java` 负责物品加载/搜索过滤/收藏排序/分页逻辑；`RecursiveCrafterScreen` 瘦身为纯 GUI 层，通过 `CraftableItemList` 实例获取数据 |

### 文件变更清单（未提交）

**已修改：**
- `core/TransactionCalculator.java` — 删除策略模式，`CostMap` 引用 → `Double`
- `core/CraftingPlanner.java` — `PlanningResult.costMemo` 类型改为 `Map<Item,Double>`，删除 `CostMap` import
- `core/CraftingTaskExecutor.java` — 引用 `ModConfig.maxCraftAmount` 替代硬编码常量
- `config/ModConfig.java` — 重写，仅保留 `maxCraftAmount` 配置项
- `client/RecursiveCrafterScreen.java` — 重写，委托 `CraftableItemList` 管理数据
- `resources/recursivecraft-common.mixins.json` — client 数组清空

**新增：**
- `client/CraftableItemList.java` — 物品列表数据模型

**已删除（已 git rm）：**
- `data/CostMap.java`
- `test/.../data/CostMapTest.java`
- `mixin/MixinTitleScreen.java`

### ⚠️ 待完成动作
1. 运行 `gradlew build` 编译验证
2. 若有编译错误，修复后重新验证
3. `git add` 相关文件 → `git commit` → `git push`

---

## 阶段三：完善与优化（P2）— ✅ 已完成并推送

| 编号 | 任务 | 状态 | 关键改动 |
|------|------|------|----------|
| 4.1 | 硬编码中文迁移到语言文件 | ✅ | `CraftingTaskExecutor`/`RecursiveCraftTransferHandler`/`RecursiveCrafterScreen` 中所有中文字面量改为 `Component.translatable(key)`；`zh_cn.json` 新增 12 个翻译键 |
| 4.2 | 拼音搜索缓存 | ✅ | `CraftableItemList` 加载时调用 `buildPinyinCache()` 预计算 `Map<Item, PinyinEntry>`（含 initials/fullPinyin/fullPinyinNoSpace）；`search()` 直接查缓存，不再每次按键重算 |
| 4.3 | 消除 `snapshotPlayerInventory()` 重复 | ✅ | 新建 `utils/InventoryUtils.snapshot(Container)`；`TransactionCalculator` 和 `CraftingTaskExecutor` 改为调用共享方法 |
| 4.4 | `CraftingTransaction.execute()` 微优化 | ✅ | `new ItemStack(item).getMaxStackSize()` → `item.getMaxStackSize()` |
| 4.5 | `consumeFromVirtualInventory` 返回值语义 | ✅ | 改为返回实际消耗量 `consumed`；调用方变量重命名为 `consumed` |
| 4.6 | `Ingredient.toString()` 作为聚合 Key 不可靠 | ✅ | 改为 `getItems()` 排序后 Item registry ID 用 `|` 拼接的稳定 Key |
| 4.7 | 添加 `en_us.json` | ✅ | 新建 `assets/recursivecraft/lang/en_us.json`，包含与 4.1 配套的英文翻译 |

---

## 提交规范

每个阶段提交使用如下格式：
```
重构阶段N(优先级): 简短描述

- 变更点1
- 变更点2

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```
