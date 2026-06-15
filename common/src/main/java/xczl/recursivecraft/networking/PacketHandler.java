package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import xczl.recursivecraft.RecursiveCraft;

import java.util.function.Supplier;

public class PacketHandler {
    public static final Identifier CHANNEL_ID = RecursiveCraft.id("main");

    public static void register() {
        // Register C2S packet receiver using the deprecated but functional API.
        // TODO: Migrate to CustomPacketPayload-based API when convenient.
        NetworkManager.registerReceiver(
                NetworkManager.clientToServer(),
                CHANNEL_ID,
                (buf, context) -> {
                    C2SExecuteCraftPacket pkt = C2SExecuteCraftPacket.decode(buf);
                    C2SExecuteCraftPacket.handle(pkt, () -> context);
                }
        );

        RecursiveCraft.LOGGER.info("RecursiveCraft: Networking registered.");
    }

    public static void sendToServer(C2SExecuteCraftPacket packet) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(),
                null // Will be resolved by the platform
        );
        packet.encode(buf);
        NetworkManager.sendToServer(CHANNEL_ID, buf);
    }
}
