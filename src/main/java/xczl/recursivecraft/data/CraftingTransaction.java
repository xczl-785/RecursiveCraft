package xczl.recursivecraft.data;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.HashMap;
import java.util.Map;

/**
 * 辅助类 (来源: [1])
 * 仅在 "阶段二：事务执行" 中使用，用于存储最终的 "需求" 和 "产出" 列表。
 */
public class CraftingTransaction {
    // 需求列表 (需要消耗的基础材料) [1]
    private final Map<Item, Integer> needs = new HashMap<>();

    // 产出列表 (最终产物 + 副产物) [1]
    private final Map<Item, Integer> provides = new HashMap<>();

    public Map<Item, Integer> getNeeds() {
        return needs;
    }

    public Map<Item, Integer> getProvides() {
        return provides;
    }

    // 添加需求
    public void addNeed(Item item, int amount) {
        if (item == Items.AIR || amount <= 0) return;
        needs.put(item, needs.getOrDefault(item, 0) + amount);
    }

    // 添加产出
    public void addProvide(Item item, int amount) {
        if (item == Items.AIR || amount <= 0) return;
        provides.put(item, provides.getOrDefault(item, 0) + amount);
    }

    // 合并另一个事务 (用于递归) [1]
    public void merge(CraftingTransaction other) {
        other.needs.forEach(this::addNeed);
        other.provides.forEach(this::addProvide);
    }

    /**
     * C. 执行事务 (来源: [1])
     * 按照 "先移除, 后给予" 的原子操作执行
     */
    public void execute(Player player) {
        // 1. 从背包移除所有 'Needs' 列表中的物品 (来源: [1])
        for (Map.Entry<Item, Integer> entry : needs.entrySet()) {
            Item item = entry.getKey();
            int amountToRemove = entry.getValue();

            // 遍历玩家背包，手动移除指定数量的物品
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

        // 2. 给予玩家所有 'Provides' 列表中的物品 (来源: [1])
        for (Map.Entry<Item, Integer> entry : provides.entrySet()) {
            ItemStack stackToAdd = new ItemStack(entry.getKey(), entry.getValue());

            // 尝试放入背包
            if (!player.getInventory().add(stackToAdd)) {
                // 3. 背包已满，多余物品掉落在地上 (来源: [1])
                player.drop(stackToAdd, false);
            }
        }
    }
}