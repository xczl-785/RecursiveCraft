package xczl.recursivecraft.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CraftingTaskExecutorNbtTest {
    @BeforeAll static void init(){ MinecraftTestBootstrap.init(); }
    @Test
    void smoke() {
        assertNotNull(CraftingTaskExecutor.class);
    }
}
