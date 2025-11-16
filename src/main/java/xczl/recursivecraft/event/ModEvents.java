package xczl.recursivecraft.event;

import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.command.RecursiveCraftCommand;
import xczl.recursivecraft.core.CraftingPlanner;
import net.minecraft.commands.CommandBuildContext;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RecursiveCraft.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        RecursiveCraft.LOGGER.info("Server is starting, triggering CraftingPlanner...");
        new Thread(() -> {
            CraftingPlanner.getInstance().buildOptimalPathTree(event.getServer().getRecipeManager());
        }).start();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // 1. 获取 BuildContext
        CommandBuildContext context = event.getBuildContext();

        // 2. *** 关键修复 ***
        // 检查 context 是否为 null。
        // 在游戏启动的某些阶段（例如客户端加载主菜单时），此事件可能会
        // 在没有服务器注册表上下文的情况下触发。
        // 我们必须跳过此"坏"事件，等待服务器加载时（context 非 null）的"好"事件。
        if (context == null) {
            RecursiveCraft.LOGGER.warn("Skipping command registration: CommandBuildContext is null (client-side event?). Will register when server loads.");
            return; // 跳过
        }

        // 3. 只有当 context 非 null 时，才执行注册
        RecursiveCraft.LOGGER.info("Registering commands with valid context...");
        RecursiveCraftCommand.register(event.getDispatcher(), context);
    }
}