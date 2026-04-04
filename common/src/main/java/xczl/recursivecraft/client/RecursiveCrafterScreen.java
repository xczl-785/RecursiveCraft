package xczl.recursivecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.menu.RecursiveCrafterMenu;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.networking.PacketHandler;

import java.util.List;

public class RecursiveCrafterScreen extends AbstractContainerScreen<RecursiveCrafterMenu> {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(RecursiveCraft.MOD_ID, "textures/gui/recursive_crafter_gui.png");

    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 6;
    private static final int GRID_SLOT_SIZE = 18;
    private int gridLeft;
    private int gridTop;

    private EditBox searchBox;
    private EditBox amountBox;
    private Button prevButton, nextButton;
    private Button executeButton;
    private Item selectedItem = Items.AIR;

    private final CraftableItemList itemList = new CraftableItemList();

    public RecursiveCrafterScreen(RecursiveCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();

        ClientFavorites.load();

        this.gridLeft = this.leftPos + 11;
        this.gridTop = this.topPos + 25;
        int pageButtonY = this.gridTop + (GRID_ROWS * GRID_SLOT_SIZE) + 4;
        int rightPanelX = this.leftPos + 180;
        int rightSlotX = this.leftPos + 208;
        int executeButtonY = this.topPos + 110;

        this.inventoryLabelY = pageButtonY;
        this.inventoryLabelX = rightPanelX - 75;

        // 初始化数据
        itemList.tryLoadFromPlanner();

        this.searchBox = new EditBox(this.font, this.leftPos + 9, this.topPos + 7, GRID_COLS * GRID_SLOT_SIZE, 12, Component.literal("Search"));
        this.searchBox.setResponder(query -> {
            itemList.search(query);
            updatePageButtons();
        });
        this.addRenderableWidget(this.searchBox);

        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("<"), (btn) -> {
            itemList.prevPage();
            updatePageButtons();
        }).bounds(this.leftPos + 8, pageButtonY, 16, 16).build());

        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), (btn) -> {
            itemList.nextPage();
            updatePageButtons();
        }).bounds(this.leftPos + 26, pageButtonY, 16, 16).build());

        this.amountBox = new EditBox(this.font, rightSlotX, this.topPos + 30, 30, 12, Component.literal("Amt"));
        this.amountBox.setValue("1");
        this.addRenderableWidget(this.amountBox);

        this.executeButton = this.addRenderableWidget(Button.builder(Component.translatable("recursivecraft.gui.execute"), (btn) -> {
            this.onExecutePressed();
        }).bounds(rightPanelX, executeButtonY, 60, 20).build());

        // 触发第一次搜索
        itemList.search(this.searchBox.getValue());
        updatePageButtons();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers) || this.amountBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if ((this.searchBox.isFocused() || this.amountBox.isFocused())
                && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updatePageButtons() {
        this.prevButton.active = itemList.hasPrevPage();
        this.nextButton.active = itemList.hasNextPage();
    }

    private void onExecutePressed() {
        if (this.selectedItem == Items.AIR) return;
        int amount = 1;
        try {
            amount = Integer.parseInt(this.amountBox.getValue());
            if (amount <= 0) amount = 1;
        } catch (NumberFormatException e) {
            amount = 1;
        }
        PacketHandler.CHANNEL.sendToServer(new C2SExecuteCraftPacket(this.selectedItem, amount));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        graphics.blit(BACKGROUND_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        // 自动刷新：如果打开界面时配方还没算好，检测到就自动加载
        if (itemList.tryLoadFromPlanner()) {
            itemList.forceRefresh();
            itemList.search(this.searchBox.getValue());
            updatePageButtons();
        }

        this.searchBox.render(graphics, mouseX, mouseY, partialTicks);
        this.amountBox.render(graphics, mouseX, mouseY, partialTicks);

        renderCraftableItems(graphics, mouseX, mouseY);

        if (this.selectedItem != Items.AIR) {
            int rightSlotX = this.leftPos + 208;
            graphics.renderFakeItem(new ItemStack(this.selectedItem), rightSlotX, this.topPos + 8);
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int rightPanelX_relative = 180;
        graphics.drawString(this.font, Component.translatable("recursivecraft.gui.output"), rightPanelX_relative, 10, 0x404040, false);
        graphics.drawString(this.font, Component.translatable("recursivecraft.gui.amount"), rightPanelX_relative, 32, 0x404040, false);

        if (!CraftingPlanner.getInstance().isReady()) {
            graphics.drawString(this.font, Component.translatable("recursivecraft.gui.analyzing"), 8, 50, 0xFF0000, false);
        } else {
            String pageText = String.format("%d / %d", itemList.getCurrentPage() + 1, itemList.getMaxPage() + 1);
            int gridTop_relative = 25;
            int pageButtonY_relative = gridTop_relative + (GRID_ROWS * GRID_SLOT_SIZE) + 4;
            int pageTextY_relative = pageButtonY_relative + 16 + 4;
            graphics.drawString(this.font, pageText, 8, pageTextY_relative, 0x404040, false);
        }
    }

    private void renderCraftableItems(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Item> pageItems = itemList.getCurrentPageItems();

        for (int i = 0; i < pageItems.size(); i++) {
            Item item = pageItems.get(i);
            ItemStack stack = new ItemStack(item);

            int x = this.gridLeft + (i % GRID_COLS) * GRID_SLOT_SIZE;
            int y = this.gridTop + (i / GRID_COLS) * GRID_SLOT_SIZE;

            graphics.renderFakeItem(stack, x, y);

            if (ClientFavorites.isFavorite(item)) {
                graphics.fill(x, y, x + 4, y + 4, 0xFFFFD700);
            }

            if (this.selectedItem == item) {
                graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            }

            if (mouseX >= x && mouseX < (x + 16) && mouseY >= y && mouseY < (y + 16)) {
                List<Component> tooltip = Screen.getTooltipFromItem(this.minecraft, stack);

                if (ClientFavorites.isFavorite(item)) {
                    tooltip.add(Component.translatable("recursivecraft.gui.favorited").withStyle(ChatFormatting.YELLOW));
                } else {
                    tooltip.add(Component.translatable("recursivecraft.gui.right_click_favorite").withStyle(ChatFormatting.DARK_GRAY));
                }

                graphics.renderTooltip(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (checkGridClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean checkGridClick(double mouseX, double mouseY, int button) {
        List<Item> pageItems = itemList.getCurrentPageItems();

        for (int i = 0; i < pageItems.size(); i++) {
            int x = this.gridLeft + (i % GRID_COLS) * GRID_SLOT_SIZE;
            int y = this.gridTop + (i / GRID_COLS) * GRID_SLOT_SIZE;

            if (mouseX >= x && mouseX < (x + 16) && mouseY >= y && mouseY < (y + 16)) {
                Item clickedItem = pageItems.get(i);

                if (button == 1) { // 右键收藏
                    ClientFavorites.toggleFavorite(clickedItem);
                    return true;
                }

                if (button == 0) { // 左键选择
                    this.selectedItem = clickedItem;
                    return true;
                }
            }
        }
        return false;
    }
}
