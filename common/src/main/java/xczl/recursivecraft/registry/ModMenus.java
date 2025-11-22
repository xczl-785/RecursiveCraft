package xczl.recursivecraft.registry;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.menu.RecursiveCrafterMenu;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(RecursiveCraft.MOD_ID, Registries.MENU);

    // 修复：使用 MenuRegistry.of (普通菜单)，不要用 ofExtended
    public static final RegistrySupplier<MenuType<RecursiveCrafterMenu>> RECURSIVE_CRAFTER_MENU = MENU_TYPES.register("recursive_crafter_menu",
            () -> MenuRegistry.ofExtended(RecursiveCrafterMenu::new)
    );

    public static void register() {
        MENU_TYPES.register();
    }
}