package xczl.recursivecraft.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import xczl.recursivecraft.RecursiveCraft;

@Mod(RecursiveCraft.MOD_ID)
public class RecursiveCraftForge {
    public RecursiveCraftForge() {
        // [关键步骤]
        // Architectury 需要接管 Forge 的事件总线，以便让 DeferredRegister 生效
        EventBuses.registerModEventBus(RecursiveCraft.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // 一键启动 Common 端的所有逻辑
        RecursiveCraft.init();
    }
}