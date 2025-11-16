package xczl.recursivecraft.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import xczl.recursivecraft.Registration;

import javax.annotation.Nullable;

public class RecursiveCrafterMenu extends AbstractContainerMenu {

    private final BlockPos blockPos;

    // 客户端构造函数 (FML 自动调用)
    public RecursiveCrafterMenu(int windowId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(windowId, playerInventory, extraData.readBlockPos());
    }

    // 服务器构造函数 (BlockEntity 调用)
    public RecursiveCrafterMenu(int windowId, @Nullable Inventory playerInventory, @Nullable BlockPos pos) {
        super(Registration.RECURSIVE_CRAFTER_MENU.get(), windowId);
        this.blockPos = (pos != null) ? pos : BlockPos.ZERO;

        if (playerInventory != null) {

            // *** 坐标修正 ***
            // 参照 CustomChestScreen.java:
            // imageHeight = 256
            // inventoryLabelY = imageHeight - 125 = 131

            // 玩家背包 (9x3) 通常在标签下方 8 像素处
            int playerInvY = 131 + 9; // = 140
            // 玩家快捷栏 (9x1) 通常在背包下方 4 像素处
            int playerHotbarY = playerInvY + (3 * 18) + 4; // = 198

            // (背包和快捷栏的 X 偏移量通常是 8，以匹配 176 宽度的纹理)
            // (根据你的新纹理 [unnamed...png], 玩家背包的 X 偏移量是 48)
            int playerInvX = 48; // <<< 调整 X 坐标以匹配新纹理

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
        // ... (Shift-Click 逻辑) ...
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D) <= 64.0D;
    }
}