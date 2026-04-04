package xczl.recursivecraft.testsupport;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class MinecraftTestBootstrap {
    private static volatile boolean initialized = false;

    private MinecraftTestBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        initialized = true;
    }
}

