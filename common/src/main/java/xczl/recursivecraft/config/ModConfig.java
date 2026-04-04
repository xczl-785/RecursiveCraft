package xczl.recursivecraft.config;

import dev.architectury.platform.Platform;
import xczl.recursivecraft.RecursiveCraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class ModConfig {
    private static final File CONFIG_FILE = Platform.getConfigFolder().resolve(RecursiveCraft.MOD_ID + ".properties").toFile();
    private static final Properties properties = new Properties();

    /** 单次合成数量上限 */
    public static int maxCraftAmount = 2304;

    public static void loadConfig() {
        try {
            if (!CONFIG_FILE.exists()) {
                saveConfig();
            } else {
                properties.load(new FileInputStream(CONFIG_FILE));
                try {
                    maxCraftAmount = Integer.parseInt(properties.getProperty("maxCraftAmount", "2304"));
                    if (maxCraftAmount <= 0) maxCraftAmount = 2304;
                } catch (NumberFormatException e) {
                    maxCraftAmount = 2304;
                }
            }
        } catch (Exception e) {
            RecursiveCraft.LOGGER.error("Failed to load config!", e);
        }
    }

    public static void saveConfig() {
        try {
            properties.setProperty("maxCraftAmount", String.valueOf(maxCraftAmount));
            properties.store(new FileOutputStream(CONFIG_FILE), "RecursiveCraft Configuration");
        } catch (Exception e) {
            RecursiveCraft.LOGGER.error("Failed to save config!", e);
        }
    }
}
