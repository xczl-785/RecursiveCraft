package xczl.recursivecraft.item;

import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.menu.RecursiveCrafterMenu;

public class HandheldCrafterItem extends Item {
    public HandheldCrafterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MenuRegistry.openExtendedMenu(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.recursivecraft.handheld_crafter");
                }

                @Nullable
                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
                    // 传入 null 作为 BlockPos，表示这是手持打开的
                    return new RecursiveCrafterMenu(windowId, inventory, (BlockPos) null);
                }
            }, buf -> {
                // 写入一个 boolean 标记：true 代表是手持打开的
                buf.writeBoolean(true);
            });
        }
        return InteractionResult.SUCCESS;
    }
}