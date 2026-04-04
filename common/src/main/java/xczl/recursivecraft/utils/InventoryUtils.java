package xczl.recursivecraft.utils;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class InventoryUtils {

    private InventoryUtils() {}

    /**
     * 将容器内容快照为 {@code Map<Item, 数量>}，用于合成计算的虚拟库存。
     */
    public static Map<Item, Integer> snapshot(Container inventory) {
        Map<Item, Integer> result = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                result.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return result;
    }
}
