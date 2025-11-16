package xczl.recursivecraft.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.Registration;

// 标记这个类只在客户端加载
@Mod.EventBusSubscriber(modid = RecursiveCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 将我们的菜单(Menu)和屏幕(Screen)绑定
        event.enqueueWork(() -> {
            MenuScreens.register(Registration.RECURSIVE_CRAFTER_MENU.get(), RecursiveCrafterScreen::new);
            RecursiveCraft.LOGGER.info("Registered RecursiveCrafterScreen");
        });
    }
}