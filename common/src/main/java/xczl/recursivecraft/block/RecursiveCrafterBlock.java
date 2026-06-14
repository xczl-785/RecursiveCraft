package xczl.recursivecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.FriendlyByteBuf;
import dev.architectury.registry.menu.MenuRegistry;
import xczl.recursivecraft.menu.RecursiveCrafterMenu;

//import javax.annotation.Nullable;

public class RecursiveCrafterBlock extends Block {

    // (这个是你自己定义的GUI标题)
    private static final Component CONTAINER_TITLE = Component.translatable("block.recursivecraft.recursive_crafter");

    public RecursiveCrafterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            MenuProvider menuProvider = state.getMenuProvider(level, pos);
            if (menuProvider != null) {
                MenuRegistry.openExtendedMenu((ServerPlayer) player, menuProvider, (FriendlyByteBuf buf) -> {
                    // [修改] 写入 false，表示这是方块打开的
                    buf.writeBoolean(false);
                    // 只有方块打开时才写入 BlockPos
                    buf.writeBlockPos(pos);
                });
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.SUCCESS;
    }

    // --- *** 这是修正后的核心 *** ---
//    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        // 返回一个 MenuProvider 的匿名实现
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                // 返回 GUI 的标题
                return CONTAINER_TITLE;
            }

//            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
                // 在这里创建并返回 *真正的* Menu 实例
                // 我们需要传递 BlockPos，以便 Menu 可以进行 stillValid 检查
                return new RecursiveCrafterMenu(windowId, playerInventory, pos);
            }
        };
    }
    // --- *** 修正结束 *** ---
}
