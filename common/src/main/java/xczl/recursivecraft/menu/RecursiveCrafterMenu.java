package xczl.recursivecraft.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf; // 关键导入
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.registry.ModBlocks;
import xczl.recursivecraft.registry.ModMenus;

// 移除 javax.annotation.Nullable，改用 JetBrains 或 Minecraft 的 Nullable，或者不加也行，这里为了兼容性先去掉或使用 Minecraft 原生的
// import javax.annotation.Nullable;

public class RecursiveCrafterMenu extends AbstractContainerMenu {

    private final BlockPos blockPos;
    private final ContainerLevelAccess access;

    // 客户端构造函数 (由 MenuRegistry.ofExtended 自动调用)
    public RecursiveCrafterMenu(int windowId, Inventory playerInventory, FriendlyByteBuf extraData) {
        // 从 buffer 中读取 BlockPos (这对应了 Block 类里 writeBlockPos 的逻辑)
        this(windowId, playerInventory, extraData.readBlockPos());
    }

    // 服务器/通用构造函数
    public RecursiveCrafterMenu(int windowId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.RECURSIVE_CRAFTER_MENU.get(), windowId);
        this.blockPos = (pos != null) ? pos : BlockPos.ZERO;
        this.access = (pos != null) ? ContainerLevelAccess.create(playerInventory.player.level(), pos) : ContainerLevelAccess.NULL;

        if (playerInventory != null) {
            // *** 布局修正 (沿用你之前的逻辑) ***
            int playerInvY = 140;
            int playerHotbarY = 198;
            int playerInvX = 48;

            // 玩家背包 (9x3)
            for (int y = 0; y < 3; ++y) {
                for (int x = 0; x < 9; ++x) {
                    this.addSlot(new Slot(playerInventory, x + y * 9 + 9, playerInvX + x * 18, playerInvY + y * 18));
                }
            }
            // 玩家快捷栏 (9x1)
            for (int x = 0; x < 9; ++x) {
                this.addSlot(new Slot(playerInventory, x, playerInvX + x * 18, playerHotbarY));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 这里先返回空，如果需要 Shift 点击功能，可以在这里补充逻辑
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 使用 ContainerLevelAccess 进行安全检查
        return stillValid(this.access, player, ModBlocks.RECURSIVE_CRAFTER_BLOCK.get());
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }
}