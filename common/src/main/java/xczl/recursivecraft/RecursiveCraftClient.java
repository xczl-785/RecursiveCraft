package xczl.recursivecraft;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.menu.MenuRegistry;
import xczl.recursivecraft.client.RecursiveCrafterScreen;
// 确保 Registration 已经在 common 包中
import xczl.recursivecraft.registry.ModMenus;

public class RecursiveCraftClient {
    public static void init() {
        // Register client screens through the shared Architectury client entrypoint.
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
