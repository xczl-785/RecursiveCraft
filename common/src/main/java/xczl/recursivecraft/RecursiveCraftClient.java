package xczl.recursivecraft;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.menu.MenuRegistry;
import xczl.recursivecraft.client.RecursiveCrafterScreen;
// 确保 Registration 已经在 common 包中
import xczl.recursivecraft.registry.ModMenus;

public class RecursiveCraftClient {
    public static void init() {
        // [迁移] 替代 Forge 的 FMLClientSetupEvent -> MenuScreens.register
        // Architectury 使用 MenuRegistry.registerScreenFactory
        ClientLifecycleEvent.CLIENT_SETUP.register(instance -> {
            MenuRegistry.registerScreenFactory(
                    ModMenus.RECURSIVE_CRAFTER_MENU.get(),
                    RecursiveCrafterScreen::new
            );
            RecursiveCraft.LOGGER.info("RecursiveCraft Client: Screen factory registered.");
        });
    }
}