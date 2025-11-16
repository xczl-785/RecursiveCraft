package xczl.recursivecraft.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import xczl.recursivecraft.RecursiveCraft;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    // 1.20.1 的网络注册
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RecursiveCraft.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;

        // C2S: 客户端请求服务器执行合成
        // (注意这里没有变动, 但 C2SExecuteCraftPacket 内部会大改)
        INSTANCE.registerMessage(id++,
                C2SExecuteCraftPacket.class,
                C2SExecuteCraftPacket::encode,
                C2SExecuteCraftPacket::decode,
                C2SExecuteCraftPacket::handle);

        // (你未来需要的其他数据包)
        // INSTANCE.registerMessage(id++, C2SRequestCalculationPacket.class, ...);
        // INSTANCE.registerMessage(id++, S2CUpdateCalculationPacket.class, ...);
    }
}