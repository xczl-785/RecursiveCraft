package xczl.recursivecraft.data;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * 辅助类 (来源: [1])
 * 仅在 "阶段一：DP规划" 中使用，用于 *比较* 不同配方的成本。
 */
public class CostMap {
    // 存储基础材料及其 *浮点数* 成本 (例如：1根木棍 = 0.5个木板 = 0.125个原木)
    public final Map<Item, Double> baseMaterials;
    private double totalCost = 0;

    public static final CostMap INFINITE_COST = new CostMap(true);

    // 基础构造器
    public CostMap() {
        this.baseMaterials = new HashMap<>();
    }

    // 无限成本构造器 (用于循环检测)
    private CostMap(boolean isInfinite) {
        this.baseMaterials = new HashMap<>();
        this.totalCost = Double.MAX_VALUE;
    }

    // 基础材料的构造器 (DP的 "Base Case" [1])
    public CostMap(Item baseItem, double amount) {
        this.baseMaterials = new HashMap<>();
        this.addMaterial(baseItem, amount);
    }

    public void addMaterial(Item item, double amount) {
        double newAmount = baseMaterials.getOrDefault(item, 0.0) + amount;
        baseMaterials.put(item, newAmount);
        recalculateTotal();
    }

    // 合并另一个成本图 [1]
    public void add(CostMap other) {
        if (this == INFINITE_COST || other == INFINITE_COST) return;
        other.baseMaterials.forEach(this::addMaterial);
    }

    // 成本翻倍 [1]
    public CostMap multiply(double factor) {
        if (this == INFINITE_COST) return this;
        CostMap result = new CostMap();
        this.baseMaterials.forEach((item, amount) -> {
            result.addMaterial(item, amount * factor);
        });
        return result;
    }

    // 成本均分 (例如：1原木 -> 4木板，木板成本 = 原木成本 / 4) [1]
    public CostMap divide(double divisor) {
        if (this == INFINITE_COST) return this;
        if (divisor == 0) return INFINITE_COST; // 避免除零
        return multiply(1.0 / divisor);
    }

    // 重新计算总成本 (用于比较)
    private void recalculateTotal() {
        this.totalCost = 0;
        for (double amount : baseMaterials.values()) {
            this.totalCost += amount;
        }
    }

    // 获取总成本，用于DP比较 [1]
    public double getTotalItemCost() {
        return this.totalCost;
    }

    public boolean isInfinite() {
        return this.totalCost == Double.MAX_VALUE;
    }
}