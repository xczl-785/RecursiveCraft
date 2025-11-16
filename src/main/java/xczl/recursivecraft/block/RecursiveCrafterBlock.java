package xczl.recursivecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component; // <<< *** 确保导入 ***
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory; // <<< *** 确保导入 ***
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu; // <<< *** 确保导入 ***
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import xczl.recursivecraft.menu.RecursiveCrafterMenu;

import javax.annotation.Nullable;

public class RecursiveCrafterBlock extends Block {

    // (这个是你自己定义的GUI标题)
    private static final Component CONTAINER_TITLE = Component.literal("Recursive Crafter");

    public RecursiveCrafterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            // 这部分逻辑是正确的
            MenuProvider menuProvider = state.getMenuProvider(level, pos);
            if (menuProvider != null) {
                NetworkHooks.openScreen((ServerPlayer) player, menuProvider, pos);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    // --- *** 这是修正后的核心 *** ---
    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        // 返回一个 MenuProvider 的匿名实现
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                // 返回 GUI 的标题
                return CONTAINER_TITLE;
            }

            @Nullable
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