package xczl.recursivecraft;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import xczl.recursivecraft.client.ClientModEvents;
import xczl.recursivecraft.event.ModEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import xczl.recursivecraft.network.PacketHandler; // <<< 导入

@Mod(RecursiveCraft.MODID)
public class RecursiveCraft {
    public static final String MODID = "recursivecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RecursiveCraft() {
        // 注册 ModEvents 类来监听 FORGE 事件总线
        MinecraftForge.EVENT_BUS.register(new ModEvents());

        // 获取 Mod 事件总线
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册我们的方块, 物品, 和菜单
        Registration.register(modEventBus);

        // *** 关键修改 ***
        // 1. 注册网络数据包
        PacketHandler.register();

        // 2. 注册客户端设置事件 (用于绑定 Screen)
        // 我们使用一个专门的类来处理客户端事件，这更整洁
        modEventBus.register(ClientModEvents.class);
    }
}