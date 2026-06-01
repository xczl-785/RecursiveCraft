# RecursiveCraft Phase 4A Debug Fixture Design

> 目标分支：`1.20.1`
> 文档日期：2026-05-31
> 主题定位：`Phase 4A` 运行时复验支撑夹具
> 文档目标：为 `JEI Ctrl` 的目标产物身份运行时复验补一个最小、可验证、低风险的样例链

---

## 一、设计结论

本方案采用：

- **最小 shipped debug fixture**
- **两条真实、非 special 的 crafting recipe**
- **同一 `Item`，两个稳定 tag 变体**
- **优先服务 `Phase 4A` checklist `2.2 / 2.3`**

本方案不采用：

- dev-only 条件注册
- built-in datapack 开关
- 依赖外部 mod 或外部内容包提供样例

原因：

1. 当前运行时中没有可靠的“同一 `Item`、多 NBT 输出”非 special 样例。
2. `Phase 4A` 的剩余阻点不是执行链实现，而是缺少端到端可复验样例。
3. 若为避免 shipped 内容而引入额外 gating 机制，侵入面会明显大于夹具本身。

---

## 二、目标

本夹具只解决一个问题：

- 为 `docs/plans/2026-05-31-target-output-nbt-phase4a-manual-verification-checklist.md` 的 `2.2 / 2.3` 提供稳定 runtime 样例

也就是说，它要支持：

1. JEI 当前展示变体 A，`Ctrl` 递归后真实产出 A
2. JEI 当前展示变体 B，`Ctrl` 递归后不得误产出 A

本夹具不负责：

1. 扩展正式玩法
2. 命令入口验证
3. GUI 变体选择
4. `2.4 / 2.5 / 2.6` 的 synthetic / debug 请求构造

---

## 三、夹具形态

### 3.1 输出物选择

推荐复用：

- `recursivecraft:handheld_crafter`

原因：

1. 现有物品已存在，无需新增模型、贴图、语言项。
2. `HandheldCrafterItem.use(...)` 不依赖自定义 NBT，因此附加 debug tag 风险较低。
3. 该物品本身是模组物品，辨识和隔离都比复用原版物品更清楚。

### 3.2 变体身份

推荐增加稳定 payload：

```text
recursivecraft_debug.phase = "4a"
recursivecraft_debug.variant = "red" | "blue"
```

约束：

1. 只使用字符串 / compound 这类当前 normalizer 明确支持的字段。
2. 不使用 `EndTag` 或其他会触发 `UNSUPPORTED` 的结构。

### 3.3 recipe 形态

推荐增加两条真实 crafting recipe：

1. `recursivecraft:debug/handheld_crafter_red`
2. `recursivecraft:debug/handheld_crafter_blue`

共同特点：

1. 都是非 special recipe
2. 输出同一个 `Item`
3. 输出 tag 不同
4. recipe id 明确不同

---

## 四、实现方式

当前风险最低的方式不是纯 `minecraft:crafting_shaped` JSON，而是：

- 新增一个最小自定义 `CraftingRecipe`
- 由它返回带 tag 的结果栈

原因：

1. 当前仓库没有现成的“普通 JSON crafting result 带 NBT”机制。
2. 本夹具必须保证：
   - JEI 看到的结果栈带 tag
   - 服务端真实 craft 结果也带同样 tag
3. 如果只让展示层带 tag、真实结果不带 tag，会把 `Phase 4A` 复验做成假阳性。

因此推荐实现为：

1. 自定义 recipe class
2. 自定义 serializer
3. 两份 recipe JSON 只负责声明输入、输出 item 与输出 tag

---

## 五、对现有系统的约束

### 5.1 对 planner 的约束

当前 planner 仍是 `Item` 级。

因此这两条 debug recipe 必须：

1. 保持可被 planner 看见
2. 但不能成为默认 cheapest path

推荐方式：

1. 让其输入成本显著高于现有普通 `handheld_crafter` recipe
2. 只把它们作为 `JEI Ctrl + recipeId + TargetOutputSpec` 验证样例

这样可以尽量避免：

- 普通无 spec 路径被 debug recipe 抢成默认规划

### 5.2 对 capability / docs 的约束

文档必须明确：

1. 这是 `Phase 4A` runtime verification fixture
2. 它属于 shipped debug content，而不是正式玩法承诺
3. 它的存在是为了闭合 `2.2 / 2.3`

---

## 六、风险与接受边界

本方案接受以下现实：

1. 夹具内容会随模组一起存在
2. 它不是最优雅的长期方案
3. 但它是当前最小、最稳、最能闭合复验证据的方案

当前不接受：

1. 为了把它做成真正 dev-only，引入一整套新的 gating 机制
2. 继续依赖不可靠的 JEI special recipe 样例做端到端结论
3. 在没有 runtime 样例的前提下宣称 `Phase 4A` 已手工闭环

---

## 七、验收要求

该夹具落地后，至少需要新增或补强以下证据：

1. 自动化：
   - recipe / serializer 能返回两个不同 tag 输出
   - `TransactionCalculator` / `CraftingTaskExecutor` 在指定目标身份时能选中对应变体
2. 手工：
   - `JEI Ctrl` 展示 red 时真实产出 red
   - `JEI Ctrl` 展示 blue 时真实产出 blue

若以上两类证据都补齐，`Phase 4A` 的 `2.2 / 2.3` 才算从“缺样例”转为“已验证”。
