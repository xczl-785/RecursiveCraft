package xczl.recursivecraft.fabric;

import net.fabricmc.api.ModInitializer;
import xczl.recursivecraft.RecursiveCraft;

public class RecursiveCraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // 一键启动 Common 端的所有逻辑
        RecursiveCraft.init();
    }
}