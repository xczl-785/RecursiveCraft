package xczl.recursivecraft;

import com.google.common.base.Suppliers;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xczl.recursivecraft.command.RecursiveCraftCommand;
import xczl.recursivecraft.config.ModConfig;
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.networking.PacketHandler;
// 引入 Registration 类
import xczl.recursivecraft.registry.*;

import java.util.function.Supplier;

public class RecursiveCraft {
    public static final String MOD_ID = "recursivecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Supplier<RegistrarManager> REGISTRIES = Suppliers.memoize(() -> RegistrarManager.get(MOD_ID));

    public static void init() {
        // 1. 加载配置
        ModConfig.loadConfig();

        // 2. 初始化所有注册表
        ModBlocks.register();
        ModItems.register();
        ModMenus.register();
        ModTabs.register();

        // 3. 注册网络
        PacketHandler.register();

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            RecursiveCraftCommand.register(dispatcher, registry);
        });

        // 4. 监听服务器启动事件
        LifecycleEvent.SERVER_STARTING.register(server -> {
            LOGGER.info("Server is starting, triggering CraftingPlanner...");
            Thread plannerThread = new Thread(() -> {
                CraftingPlanner.getInstance().buildOptimalPathTree(server.getRecipeManager());
            }, "RecursiveCraft-Planner");
            plannerThread.setUncaughtExceptionHandler((t, e) ->
                    LOGGER.error("CraftingPlanner failed unexpectedly", e));
            plannerThread.start();
        });

        // 5. 安全加载客户端入口
        EnvExecutor.runInEnv(Env.CLIENT, () -> RecursiveCraftClient::init);

        LOGGER.info("RecursiveCraft initialized!");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}