package xczl.recursivecraft.config;

import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import xczl.recursivecraft.RecursiveCraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class ModConfig {
    // 配置文件路径
    private static final File CONFIG_FILE = Platform.getConfigFolder().resolve(RecursiveCraft.MOD_ID + ".properties").toFile();
    private static final Properties properties = new Properties();

    // --- 配置项 ---
    public static boolean logDirtBlock = true;
    public static int magicNumber = 42;
    public static String magicNumberIntroduction = "The magic number is... ";
    // 存储解析后的物品集合
    public static Set<Item> items = new HashSet<>();

    public static void loadConfig() {
        try {
            if (!CONFIG_FILE.exists()) {
                saveConfig(); // 创建默认配置
            } else {
                properties.load(new FileInputStream(CONFIG_FILE));

                // 1. 读取布尔值
                logDirtBlock = Boolean.parseBoolean(properties.getProperty("logDirtBlock", "true"));

                // 2. 读取整数
                try {
                    magicNumber = Integer.parseInt(properties.getProperty("magicNumber", "42"));
                } catch (NumberFormatException e) {
                    magicNumber = 42;
                }

                // 3. 读取字符串
                magicNumberIntroduction = properties.getProperty("magicNumberIntroduction", "The magic number is... ");

                // 4. 读取物品列表 (以逗号分隔的字符串)
                String itemsStr = properties.getProperty("items", "minecraft:iron_ingot");
                parseItems(itemsStr);
            }
        } catch (Exception e) {
            RecursiveCraft.LOGGER.error("Failed to load config!", e);
        }
    }

    public static void saveConfig() {
        try {
            properties.setProperty("logDirtBlock", String.valueOf(logDirtBlock));
            properties.setProperty("magicNumber", String.valueOf(magicNumber));
            properties.setProperty("magicNumberIntroduction", magicNumberIntroduction);

            // [修复] 真实的保存逻辑：遍历 items 集合，生成逗号分隔的字符串
            if (items != null && !items.isEmpty()) {
                StringBuilder itemsStr = new StringBuilder();
                for (Item item : items) {
                    // 获取注册名 (e.g., "minecraft:apple")
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    if (key != null) {
                        itemsStr.append(key.toString()).append(",");
                    }
                }
                // 删除最后一个多余的逗号
                if (itemsStr.length() > 0) {
                    itemsStr.setLength(itemsStr.length() - 1);
                }
                properties.setProperty("items", itemsStr.toString());
            } else {
                // 如果集合为空，保存默认值或空字符串
                properties.setProperty("items", "minecraft:iron_ingot");
            }

            properties.store(new FileOutputStream(CONFIG_FILE), "RecursiveCraft Configuration");
        } catch (Exception e) {
            RecursiveCraft.LOGGER.error("Failed to save config!", e);
        }
    }

    // 解析物品字符串 "minecraft:apple,minecraft:stick" -> Set<Item>
    private static void parseItems(String itemsStr) {
        items.clear();
        String[] splits = itemsStr.split(",");
        for (String s : splits) {
            String id = s.trim();
            if (id.isEmpty()) continue;
            ResourceLocation loc = new ResourceLocation(id);
            if (BuiltInRegistries.ITEM.containsKey(loc)) {
                items.add(BuiltInRegistries.ITEM.get(loc));
            } else {
                RecursiveCraft.LOGGER.warn("Config: Item not found: " + id);
            }
        }
    }
}