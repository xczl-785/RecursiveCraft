package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkChannel;
import xczl.recursivecraft.RecursiveCraft;

public class PacketHandler {
    // 创建通道
    public static final NetworkChannel CHANNEL = NetworkChannel.create(RecursiveCraft.id("main"));

    public static void register() {
        // 注册数据包
        // 参数顺序：类, 编码方法, 解码方法, 处理方法
        CHANNEL.register(
                C2SExecuteCraftPacket.class,
                C2SExecuteCraftPacket::encode,
                C2SExecuteCraftPacket::decode,
                C2SExecuteCraftPacket::handle
        );

        RecursiveCraft.LOGGER.info("RecursiveCraft: Networking registered.");
    }
}
