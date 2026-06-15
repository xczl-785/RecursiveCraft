package xczl.recursivecraft.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import xczl.recursivecraft.RecursiveCraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientFavorites {
    private static final File FILE = Platform.getConfigFolder().resolve("recursivecraft_favorites.json").toFile();
    private static final Gson GSON = new Gson();
    // 存储 Item 的 Registry Name 字符串 (e.g. "minecraft:stone")
    private static Set<String> favorites = new HashSet<>();

    public static void load() {
        try {
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    Type setType = new TypeToken<HashSet<String>>(){}.getType();
                    favorites = GSON.fromJson(reader, setType);
                    if (favorites == null) favorites = new HashSet<>();
                }
            }
        } catch (Exception e) {
            RecursiveCraft.LOGGER.error("Failed to load favorites", e);
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(favorites, writer);
        } catch (Exception e) {
            RecursiveCraft.LOGGER.error("Failed to save favorites", e);
        }
    }

    public static boolean isFavorite(Item item) {
        return favorites.contains(getItemId(item));
    }

    public static void toggleFavorite(Item item) {
        String id = getItemId(item);
        if (favorites.contains(id)) {
            favorites.remove(id);
        } else {
            favorites.add(id);
        }
        save();
    }

    private static String getItemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}