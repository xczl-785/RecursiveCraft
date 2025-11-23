package xczl.recursivecraft.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.item.HandheldCrafterItem;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(RecursiveCraft.MOD_ID, Registries.ITEM);

    // 引用 ModBlocks.RECURSIVE_CRAFTER_BLOCK
    public static final RegistrySupplier<Item> RECURSIVE_CRAFTER_ITEM = ITEMS.register("recursive_crafter",
            () -> new BlockItem(ModBlocks.RECURSIVE_CRAFTER_BLOCK.get(), new Item.Properties())
    );

    // [新增] 手持递归合成器
    public static final RegistrySupplier<Item> HANDHELD_CRAFTER_ITEM = ITEMS.register("handheld_crafter",
            () -> new HandheldCrafterItem(new Item.Properties().stacksTo(1))
    );

    public static void register() {
        ITEMS.register();
    }
}