package xczl.recursivecraft;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.client.gui.MenuScreenRegistry;
import xczl.recursivecraft.client.RecursiveCrafterScreen;
import xczl.recursivecraft.registry.ModMenus;

public class RecursiveCraftClient {
    public static void init() {
        ClientLifecycleEvent.CLIENT_SETUP.register(instance -> {
            MenuScreenRegistry.registerScreenFactory(
                    ModMenus.RECURSIVE_CRAFTER_MENU.get(),
                    RecursiveCrafterScreen::new
            );
            RecursiveCraft.LOGGER.info("RecursiveCraft Client: Screen factory registered.");
        });
    }
}
