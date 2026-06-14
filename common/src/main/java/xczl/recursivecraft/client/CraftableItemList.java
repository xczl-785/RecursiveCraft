package xczl.recursivecraft.client;

import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.compat.jei.RecursiveCraftJeiRuntime;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;
import xczl.recursivecraft.utils.PinyinUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可合成物品列表的数据模型。
 * 管理物品的加载、搜索过滤、收藏排序和分页逻辑，与 GUI 渲染解耦。
 */
public class CraftableItemList {

    private static final int PAGE_SIZE = 8 * 6; // GRID_COLS * GRID_ROWS

    /** 每个物品预计算的拼音数据，避免搜索时重复计算 */
    private record PinyinEntry(String initials, String fullPinyin, String fullPinyinNoSpace) {}

    private List<CraftableTarget> allTargets = new ArrayList<>();
    private Map<CraftableTarget, PinyinEntry> pinyinCache = new HashMap<>();
    private List<CraftableTarget> filteredTargets = new ArrayList<>();
    private String lastSearchQuery = null;
    private int currentPage = 0;
    private int maxPage = 0;
    private int lastJeiGeneration = Integer.MIN_VALUE;

    /** 尝试从 CraftingPlanner 加载数据，返回是否成功加载 */
    public boolean tryLoadFromPlanner() {
        if (!CraftingPlanner.getInstance().isReady()) {
            return false;
        }
        int jeiGeneration = currentJeiGeneration();
        if (!allTargets.isEmpty() && jeiGeneration == lastJeiGeneration) {
            return false;
        }

        Set<Item> plannerItems = new HashSet<>(CraftingPlanner.getInstance().getResult().getPathMemo().keySet());
        Map<String, CraftableTarget> mergedTargets = new LinkedHashMap<>();

        for (Item item : plannerItems) {
            ItemStack displayStack = CraftingPlanner.getInstance().getResult().getPathMemo().get(item) != null
                    ? CraftingPlanner.getInstance().getResult().getPathMemo().get(item).getResultItem(null).copy()
                    : new ItemStack(item);
            if (displayStack.isEmpty()) {
                displayStack = new ItemStack(item);
            }
            CraftableTarget target = new CraftableTarget(
                    displayStack,
                    null,
                    displayStack.getComponentsPatch().isEmpty() ? null : TargetOutputSpec.fromStack(displayStack),
                    null
            );
            mergedTargets.put(target.searchKey(), target);
        }

        if (Platform.isModLoaded("jei") && RecursiveCraftJeiRuntime.isAvailable()) {
            for (CraftableTarget target : RecursiveCraftJeiRuntime.collectCraftableTargets(plannerItems)) {
                mergedTargets.putIfAbsent(target.searchKey(), target);
            }
        }

        allTargets = new ArrayList<>(mergedTargets.values());
        buildPinyinCache();
        sortTargets(allTargets);
        lastJeiGeneration = jeiGeneration;
        lastSearchQuery = null; // 强制下次搜索时刷新
        return true;
    }

    private void buildPinyinCache() {
        pinyinCache = new HashMap<>(allTargets.size());
        for (CraftableTarget target : allTargets) {
            String displayName = target.displayStack().getHoverName().getString();
            String full = PinyinUtils.toFullPinyin(displayName);
            pinyinCache.put(target, new PinyinEntry(
                    PinyinUtils.toInitials(displayName),
                    full,
                    full.replace(" ", "")
            ));
        }
    }

    public boolean isLoaded() {
        return !allTargets.isEmpty();
    }

    /** 执行搜索过滤，返回是否真正更新了列表（防抖） */
    public boolean search(String query) {
        String lowerQuery = query.toLowerCase().trim();
        if (lowerQuery.equals(lastSearchQuery)) {
            return false;
        }
        lastSearchQuery = lowerQuery;

        filteredTargets = allTargets.stream()
                .filter(target -> {
                    if (lowerQuery.isEmpty()) return true;
                    String displayName = target.displayStack().getHoverName().getString().toLowerCase();
                    if (displayName.contains(lowerQuery)) return true;
                    String registryId = BuiltInRegistries.ITEM.getKey(target.item()).toString();
                    if (registryId.contains(lowerQuery)) return true;
                    PinyinEntry pinyin = pinyinCache.get(target);
                    if (pinyin != null) {
                        if (pinyin.initials().contains(lowerQuery)) return true;
                        if (pinyin.fullPinyin().contains(lowerQuery)) return true;
                        if (pinyin.fullPinyinNoSpace().contains(lowerQuery)) return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        sortTargets(filteredTargets);
        currentPage = 0;
        maxPage = Math.max(0, (filteredTargets.size() - 1) / PAGE_SIZE);
        return true;
    }

    public void forceRefresh() {
        lastSearchQuery = null;
    }

    public List<CraftableTarget> getCurrentPageItems() {
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filteredTargets.size());
        if (start >= filteredTargets.size()) return Collections.emptyList();
        return filteredTargets.subList(start, end);
    }

    public int getCurrentPage() { return currentPage; }
    public int getMaxPage() { return maxPage; }

    public boolean hasPrevPage() { return currentPage > 0; }
    public boolean hasNextPage() { return currentPage < maxPage; }

    public void prevPage() {
        currentPage = Math.max(0, currentPage - 1);
    }

    public void nextPage() {
        currentPage = Math.min(maxPage, currentPage + 1);
    }

    private void sortTargets(List<CraftableTarget> list) {
        list.sort((target1, target2) -> {
            boolean fav1 = ClientFavorites.isFavorite(target1.item());
            boolean fav2 = ClientFavorites.isFavorite(target2.item());
            if (fav1 && !fav2) return -1;
            if (!fav1 && fav2) return 1;
            String id1 = BuiltInRegistries.ITEM.getKey(target1.item()).toString();
            String id2 = BuiltInRegistries.ITEM.getKey(target2.item()).toString();
            int idCompare = id1.compareTo(id2);
            if (idCompare != 0) {
                return idCompare;
            }
            return target1.searchKey().compareTo(target2.searchKey());
        });
    }

    private int currentJeiGeneration() {
        if (!Platform.isModLoaded("jei")) {
            return Integer.MIN_VALUE;
        }
        return RecursiveCraftJeiRuntime.generation();
    }
}
