package xczl.recursivecraft;

import dev.architectury.registry.menu.MenuRegistry;
import xczl.recursivecraft.client.RecursiveCrafterScreen;
import xczl.recursivecraft.registry.ModMenus;

public class RecursiveCraftClient {
    public static void init() {
        MenuRegistry.registerScreenFactory(
                ModMenus.RECURSIVE_CRAFTER_MENU.get(),
                RecursiveCrafterScreen::new
        );
        RecursiveCraft.LOGGER.info("RecursiveCraft Client: Screen factory registered.");
    }
}
