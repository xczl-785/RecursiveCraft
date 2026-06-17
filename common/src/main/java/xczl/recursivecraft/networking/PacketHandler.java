package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import xczl.recursivecraft.RecursiveCraft;

public class PacketHandler {
    private static final ResourceLocation EXECUTE_CRAFT_PACKET_ID = RecursiveCraft.id("execute_craft");

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), EXECUTE_CRAFT_PACKET_ID, (buf, context) ->
                C2SExecuteCraftPacket.handle(C2SExecuteCraftPacket.decode(buf), () -> context));

        RecursiveCraft.LOGGER.info("RecursiveCraft: Networking registered.");
    }

    @Environment(EnvType.CLIENT)
    public static void sendToServer(C2SExecuteCraftPacket packet) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("Unable to send packet to the server while not in game!");
        }
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess());
        packet.encode(buf);
        NetworkManager.sendToServer(EXECUTE_CRAFT_PACKET_ID, buf);
    }
}
