// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/data/CraftingTransaction.java
// (已应用 "净变化" 修复)
// ======================================================================
package xczl.recursivecraft.data;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * 辅助类 (来源: [1])
 * 存储最终的 "毛需求" (Needs) 和 "毛产出" (Provides) 列表。
 * (已修复) 现在提供 "净变化" (Net Deltas) 用于检查和执行。
 */
public class CraftingTransaction {
    // 毛需求列表 (计算过程中从虚拟库存中 "取出" 的总和)
    private final Map<Item, Integer> needs = new HashMap<>();

    // 毛产出列表 (计算过程中作为副产品 "放回" 的总和 + 最终产物)
    private final Map<Item, Integer> provides = new HashMap<>();

    public Map<Item, Integer> getNeeds() {
        return needs;
    }

    public Map<Item, Integer> getProvides() {
        return provides;
    }

    // 添加需求 (毛)
    public void addNeed(Item item, int amount) {
        if (item == Items.AIR || amount <= 0) return;
        needs.put(item, needs.getOrDefault(item, 0) + amount);
    }

    // 添加产出 (毛)
    public void addProvide(Item item, int amount) {
        if (item == Items.AIR || amount <= 0) return;
        provides.put(item, provides.getOrDefault(item, 0) + amount);
    }

    // 合并另一个事务 (用于递归) [1]
    public void merge(CraftingTransaction other) {
        other.needs.forEach(this::addNeed);
        other.provides.forEach(this::addProvide);
    }

    // <<< [修复] 新增方法：计算"净变化" >>>
    /**
     * 计算此事务的"净"物品变化 (Provides - Needs)。
     * 这是修复 "木斧Bug" 的核心。
     *
     * @return 一个 Map<Item, Integer>。
     * 负数 (e.g. -1) 表示"净需求"(最终需要从玩家处获取 1 个)。
     * 正数 (e.g. 2) 表示"净产出"(最终需要给予玩家 2 个)。
     */
    public Map<Item, Integer> getNetDeltas() {
        // 1. 使用 Provides (产出, 正数) 初始化 "净值" 表
        Map<Item, Integer> deltas = new HashMap<>(this.provides);

        // 2. 减去所有 Needs (需求, 负数)
        for (Map.Entry<Item, Integer> entry : this.needs.entrySet()) {
            Item item = entry.getKey();
            int neededAmount = entry.getValue();
            // (例如: 产出 3 - 需求 4 = 净值 -1)
            deltas.put(item, deltas.getOrDefault(item, 0) - neededAmount);
        }

        // 3. 移除所有净值为 0 的条目 (例如, 需要2个木棍, 也产出2个木棍)
        deltas.values().removeIf(count -> count == 0);

        return deltas;
    }


    /**
     * C. 执行事务 (来源: [1])
     * (已修复) 按照 "净变化" (Net Deltas) 执行原子操作
     */
    public void execute(Player player) {
        // <<< [修复] 1. 获取净变化，而不是使用 "毛" 列表 >>>
        Map<Item, Integer> netDeltas = this.getNetDeltas();

        // <<< [修复] 2. 移除所有 "净需求" (所有负值) >>>
        for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
            if (entry.getValue() < 0) { // < 0, 是净需求
                Item item = entry.getKey();
                // (例如: 净值 -1, amountToRemove = 1)
                int amountToRemove = -entry.getValue();

                // (移除逻辑与你原版一致)
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == item) {
                        int amountInStack = stack.getCount();
                        int amountToTake = Math.min(amountToRemove, amountInStack);

                        stack.shrink(amountToTake);
                        amountToRemove -= amountToTake;

                        if (amountToRemove <= 0) {
                            break;
                        }
                    }
                }
            }
        }

        // <<< [修复] 3. 给予玩家所有 "净产出" (所有正值) >>>
        for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
            if (entry.getValue() > 0) { // > 0, 是净产出
                Item item = entry.getKey();
                int totalAmountToGive = entry.getValue();

                // 获取该物品的最大堆叠数
                // 注意：一般直接用 item.getMaxStackSize() 即可，但在某些版本可能需要通过 ItemStack 获取
                int maxStackSize = new ItemStack(item).getMaxStackSize();

                // 循环切分：只要还有没给完的，就继续给
                while (totalAmountToGive > 0) {
                    // 这一堆给多少？不能超过总剩余量，也不能超过最大堆叠数
                    int splitAmount = Math.min(totalAmountToGive, maxStackSize);

                    ItemStack stackToAdd = new ItemStack(item, splitAmount);

                    // 尝试添加到背包
                    if (!player.getInventory().add(stackToAdd)) {
                        // 背包已满，多余物品掉落在地上
                        player.drop(stackToAdd, false);
                    }

                    // 扣除已给的数量
                    totalAmountToGive -= splitAmount;
                }
            }
        }
    }
}