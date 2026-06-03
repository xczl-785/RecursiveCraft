package xczl.recursivecraft.networking;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import xczl.recursivecraft.core.CraftingTaskExecutor;
import xczl.recursivecraft.runtime.material.TargetOutputSpec;
import xczl.recursivecraft.testsupport.MinecraftTestBootstrap;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class C2SExecuteCraftPacketTest {
    @BeforeAll
    static void init() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void decode_shouldPreserveLegacyShapeWhenTargetOutputSpecIsAbsent() {
        ResourceLocation forcedRecipeId = new ResourceLocation("test", "legacy");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeId(net.minecraft.core.registries.BuiltInRegistries.ITEM, Items.TORCH);
        buf.writeInt(4);
        buf.writeBoolean(true);
        buf.writeResourceLocation(forcedRecipeId);

        C2SExecuteCraftPacket decoded = C2SExecuteCraftPacket.decode(buf);

        assertEquals(Items.TORCH, decoded.targetItem());
        assertEquals(4, decoded.amount());
        assertEquals(forcedRecipeId, decoded.forcedRecipeId());
        assertNull(decoded.targetOutputSpec());
    }

    @Test
    void decode_shouldPreserveTargetOutputSpecTagPayload() {
        ResourceLocation forcedRecipeId = new ResourceLocation("test", "tagged");
        CompoundTag tag = new CompoundTag();
        tag.putString("Potion", "minecraft:strong_healing");
        C2SExecuteCraftPacket packet = new C2SExecuteCraftPacket(
                Items.POTION,
                2,
                forcedRecipeId,
                new TargetOutputSpec(Items.POTION, tag),
                List.of(new net.minecraft.world.item.ItemStack(Items.SUGAR), new net.minecraft.world.item.ItemStack(Items.GLASS_BOTTLE))
        );

        C2SExecuteCraftPacket decoded = decode(packet);

        assertEquals(Items.POTION, decoded.targetItem());
        assertEquals(2, decoded.amount());
        assertEquals(forcedRecipeId, decoded.forcedRecipeId());
        assertNotNull(decoded.targetOutputSpec());
        assertEquals(Items.POTION, decoded.targetOutputSpec().item());
        assertEquals(tag, decoded.targetOutputSpec().tag());
        assertNotNull(decoded.displayedIngredients());
        assertEquals(2, decoded.displayedIngredients().size());
        assertEquals(Items.SUGAR, decoded.displayedIngredients().get(0).getItem());
        assertEquals(Items.GLASS_BOTTLE, decoded.displayedIngredients().get(1).getItem());
    }

    @Test
    void encodeDecode_shouldRoundTripObservableFields() {
        CompoundTag tag = new CompoundTag();
        tag.putString("variant", "spruce");
        C2SExecuteCraftPacket packet = new C2SExecuteCraftPacket(
                Items.CHEST,
                1,
                null,
                new TargetOutputSpec(Items.CHEST, tag)
        );

        C2SExecuteCraftPacket decoded = decode(packet);

        assertEquals(packet.targetItem(), decoded.targetItem());
        assertEquals(packet.amount(), decoded.amount());
        assertEquals(packet.forcedRecipeId(), decoded.forcedRecipeId());
        assertNotNull(decoded.targetOutputSpec());
        assertEquals(packet.targetOutputSpec().item(), decoded.targetOutputSpec().item());
        assertEquals(packet.targetOutputSpec().tag(), decoded.targetOutputSpec().tag());
    }

    @Test
    void handle_shouldRejectConflictingTargetItemAndTargetOutputSpecItem() {
        ServerPlayer player = mock(ServerPlayer.class);
        NetworkManager.PacketContext context = mock(NetworkManager.PacketContext.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(context).queue(org.mockito.ArgumentMatchers.any(Runnable.class));
        when(context.getPlayer()).thenReturn(player);

        C2SExecuteCraftPacket packet = new C2SExecuteCraftPacket(
                Items.TORCH,
                1,
                null,
                new TargetOutputSpec(Items.STICK, null)
        );

        try (MockedStatic<CraftingTaskExecutor> executor = Mockito.mockStatic(CraftingTaskExecutor.class)) {
            C2SExecuteCraftPacket.handle(packet, supplierOf(context));

            ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
            verify(player).sendSystemMessage(messageCaptor.capture());
            assertTrue(messageCaptor.getValue().toString().contains("recursivecraft.msg.invalid_request"));
            assertFalse(messageCaptor.getValue().toString().contains("craft_success"));
            executor.verifyNoInteractions();
        }
    }

    private static Supplier<NetworkManager.PacketContext> supplierOf(NetworkManager.PacketContext context) {
        return () -> context;
    }

    private static C2SExecuteCraftPacket decode(C2SExecuteCraftPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        return C2SExecuteCraftPacket.decode(buf);
    }
}
