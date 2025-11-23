package xczl.recursivecraft.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.registry.ModBlocks;
import xczl.recursivecraft.registry.ModMenus;

//import javax.annotation.Nullable;
import org.jetbrains.annotations.Nullable;

public class RecursiveCrafterMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final boolean isHandheld; // 新增标记

    // 客户端构造函数 (读取网络包)
    public RecursiveCrafterMenu(int windowId, Inventory playerInventory, FriendlyByteBuf extraData) {
        // 先调用一个私有构造辅助方法，或者在构造函数里处理逻辑
        // 由于 super 必须第一行，我们只能在内部处理
        super(ModMenus.RECURSIVE_CRAFTER_MENU.get(), windowId);

        this.isHandheld = extraData.readBoolean(); // 读取标记
        if (isHandheld) {
            this.access = ContainerLevelAccess.NULL;
        } else {
            BlockPos pos = extraData.readBlockPos();
            this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        }

        initLayout(playerInventory);
    }

    // 服务器/通用构造函数
    public RecursiveCrafterMenu(int windowId, Inventory playerInventory, @Nullable BlockPos pos) {
        super(ModMenus.RECURSIVE_CRAFTER_MENU.get(), windowId);

        // 如果 pos 为 null，则认为是手持模式
        this.isHandheld = (pos == null);

        if (pos != null) {
            this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        } else {
            this.access = ContainerLevelAccess.NULL;
        }

        initLayout(playerInventory);
    }

    // 提取布局逻辑，避免重复代码
    private void initLayout(Inventory playerInventory) {
        if (playerInventory != null) {
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
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 如果是手持模式，直接返回 true (或者可以加一个校验：玩家手中是否持有该物品)
        if (this.isHandheld) {
            return true;
        }
        // 如果是方块模式，检查距离
        return stillValid(this.access, player, ModBlocks.RECURSIVE_CRAFTER_BLOCK.get());
    }
}