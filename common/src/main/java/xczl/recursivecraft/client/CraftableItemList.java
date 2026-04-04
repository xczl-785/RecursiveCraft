package xczl.recursivecraft.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.utils.PinyinUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 可合成物品列表的数据模型。
 * 管理物品的加载、搜索过滤、收藏排序和分页逻辑，与 GUI 渲染解耦。
 */
public class CraftableItemList {

    private static final int PAGE_SIZE = 8 * 6; // GRID_COLS * GRID_ROWS

    /** 每个物品预计算的拼音数据，避免搜索时重复计算 */
    private record PinyinEntry(String initials, String fullPinyin, String fullPinyinNoSpace) {}

    private List<Item> allItems = new ArrayList<>();
    private Map<Item, PinyinEntry> pinyinCache = new HashMap<>();
    private List<Item> filteredItems = new ArrayList<>();
    private String lastSearchQuery = null;
    private int currentPage = 0;
    private int maxPage = 0;

    /** 尝试从 CraftingPlanner 加载数据，返回是否成功加载 */
    public boolean tryLoadFromPlanner() {
        if (!allItems.isEmpty() || !CraftingPlanner.getInstance().isReady()) {
            return false;
        }
        allItems = new ArrayList<>(CraftingPlanner.getInstance().getResult().getPathMemo().keySet());
        buildPinyinCache();
        sortItems(allItems);
        lastSearchQuery = null; // 强制下次搜索时刷新
        return true;
    }

    private void buildPinyinCache() {
        pinyinCache = new HashMap<>(allItems.size());
        for (Item item : allItems) {
            String displayName = item.getDescription().getString();
            String full = PinyinUtils.toFullPinyin(displayName);
            pinyinCache.put(item, new PinyinEntry(
                    PinyinUtils.toInitials(displayName),
                    full,
                    full.replace(" ", "")
            ));
        }
    }

    public boolean isLoaded() {
        return !allItems.isEmpty();
    }

    /** 执行搜索过滤，返回是否真正更新了列表（防抖） */
    public boolean search(String query) {
        String lowerQuery = query.toLowerCase().trim();
        if (lowerQuery.equals(lastSearchQuery)) {
            return false;
        }
        lastSearchQuery = lowerQuery;

        filteredItems = allItems.stream()
                .filter(item -> {
                    if (lowerQuery.isEmpty()) return true;
                    String displayName = item.getDescription().getString().toLowerCase();
                    if (displayName.contains(lowerQuery)) return true;
                    String registryId = BuiltInRegistries.ITEM.getKey(item).toString();
                    if (registryId.contains(lowerQuery)) return true;
                    PinyinEntry pinyin = pinyinCache.get(item);
                    if (pinyin != null) {
                        if (pinyin.initials().contains(lowerQuery)) return true;
                        if (pinyin.fullPinyin().contains(lowerQuery)) return true;
                        if (pinyin.fullPinyinNoSpace().contains(lowerQuery)) return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        sortItems(filteredItems);
        currentPage = 0;
        maxPage = Math.max(0, (filteredItems.size() - 1) / PAGE_SIZE);
        return true;
    }

    public void forceRefresh() {
        lastSearchQuery = null;
    }

    public List<Item> getCurrentPageItems() {
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filteredItems.size());
        if (start >= filteredItems.size()) return Collections.emptyList();
        return filteredItems.subList(start, end);
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

    private void sortItems(List<Item> list) {
        list.sort((item1, item2) -> {
            boolean fav1 = ClientFavorites.isFavorite(item1);
            boolean fav2 = ClientFavorites.isFavorite(item2);
            if (fav1 && !fav2) return -1;
            if (!fav1 && fav2) return 1;
            String id1 = BuiltInRegistries.ITEM.getKey(item1).toString();
            String id2 = BuiltInRegistries.ITEM.getKey(item2).toString();
            return id1.compareTo(id2);
        });
    }
}
