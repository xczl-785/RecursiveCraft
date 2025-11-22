package xczl.recursivecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.ItemRenderer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecursiveCrafterScreen extends AbstractContainerScreen<RecursiveCrafterMenu> {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(RecursiveCraft.MOD_ID, "textures/gui/recursive_crafter_gui.png");

    // 布局常量
    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 6;
    private static final int GRID_SLOT_SIZE = 18;
    private int gridLeft;
    private int gridTop;

    // 状态变量
    private EditBox searchBox;
    private EditBox amountBox;
    private List<Item> allCraftableItems = new ArrayList<>();
    private List<Item> filteredCraftableItems = new ArrayList<>();
    private int currentPage = 0;
    private int maxPage = 0;
    private Button prevButton, nextButton;
    private Button executeButton; // 这里的 executeButton 在你的原代码里可能未定义成字段，我补充上
    private Item selectedItem = Items.AIR;

    public RecursiveCrafterScreen(RecursiveCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();

        this.gridLeft = this.leftPos + 11;
        this.gridTop = this.topPos + 25;
        int pageButtonY = this.gridTop + (GRID_ROWS * GRID_SLOT_SIZE) + 4;
        int rightPanelX = this.leftPos + 180;
        int rightSlotX = this.leftPos + 208;
        int executeButtonY = this.topPos + 110;
        int executeButtonHeight = 20;

        this.inventoryLabelY = pageButtonY;
        this.inventoryLabelX = rightPanelX - 75; // 调整物品栏标签位置

        // 获取数据
        if (CraftingPlanner.isReady) {
            allCraftableItems = new ArrayList<>(CraftingPlanner.getInstance().getPathMemo().keySet());
            // 简单的按名称排序
            allCraftableItems.sort((a, b) -> a.getDescription().getString().compareTo(b.getDescription().getString()));
        }

        // 搜索框
        this.searchBox = new EditBox(this.font, this.leftPos + 9, this.topPos + 7, GRID_COLS * GRID_SLOT_SIZE, 12, Component.literal("Search"));
        this.searchBox.setResponder(this::onSearchUpdate);
        this.addRenderableWidget(this.searchBox);

        // 分页按钮
        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("<"), (btn) -> {
            this.currentPage = Math.max(0, this.currentPage - 1);
            updatePageButtons();
        }).bounds(this.leftPos + 8, pageButtonY, 16, 16).build());

        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), (btn) -> {
            this.currentPage = Math.min(this.maxPage, this.currentPage + 1);
            updatePageButtons();
        }).bounds(this.leftPos + 26, pageButtonY, 16, 16).build());

        // 数量框
        this.amountBox = new EditBox(this.font, rightSlotX, this.topPos + 30, 30, 12, Component.literal("Amt"));
        this.amountBox.setValue("1");
        this.addRenderableWidget(this.amountBox);

        // 执行按钮
        this.executeButton = this.addRenderableWidget(Button.builder(Component.literal("执行合成"), (btn) -> {
            this.onExecutePressed();
        }).bounds(rightPanelX, executeButtonY, 60, executeButtonHeight).build());

        // 初始化列表
        onSearchUpdate(this.searchBox.getValue());
    }

    private void onSearchUpdate(String query) {
        String lowerQuery = query.toLowerCase().trim();
        this.filteredCraftableItems = this.allCraftableItems.stream()
                .filter(item -> item.getDescription().getString().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());

        this.currentPage = 0;
        this.maxPage = Math.max(0, (this.filteredCraftableItems.size() - 1) / (GRID_COLS * GRID_ROWS));
        updatePageButtons();
    }

    private void updatePageButtons() {
        this.prevButton.active = this.currentPage > 0;
        this.nextButton.active = this.currentPage < this.maxPage;
    }

    private void onExecutePressed() {
        if (this.selectedItem == Items.AIR) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("请先从左侧选择一个物品。"));
            }
            return;
        }
        int amount = 1;
        try {
            amount = Integer.parseInt(this.amountBox.getValue());
            if (amount <= 0) amount = 1;
        } catch (NumberFormatException e) { amount = 1; }

        // [迁移] 发送网络包
        // Forge: PacketHandler.INSTANCE.sendToServer(...)
        // Architectury: PacketHandler.CHANNEL.sendToServer(...)
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

        this.searchBox.render(graphics, mouseX, mouseY, partialTicks);
        this.amountBox.render(graphics, mouseX, mouseY, partialTicks);

        renderCraftableItems(graphics, mouseX, mouseY);

        // 渲染选中物品
        if (this.selectedItem != Items.AIR) {
            int rightSlotX = this.leftPos + 208;
            graphics.renderFakeItem(new ItemStack(this.selectedItem), rightSlotX, this.topPos + 8);
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        // 绘制文本 (使用相对坐标)
        int rightPanelX_relative = 180;
        graphics.drawString(this.font, Component.literal("产物:"), rightPanelX_relative, 10, 0x404040, false);
        graphics.drawString(this.font, Component.literal("数量:"), rightPanelX_relative, 32, 0x404040, false);

        String pageText = String.format("%d / %d", this.currentPage + 1, this.maxPage + 1);
        int gridTop_relative = 25;
        int pageButtonY_relative = gridTop_relative + (GRID_ROWS * GRID_SLOT_SIZE) + 4;
        int pageTextY_relative = pageButtonY_relative + 16 + 4;

        graphics.drawString(this.font, pageText, 8, pageTextY_relative, 0x404040, false);
    }

    private void renderCraftableItems(GuiGraphics graphics, int mouseX, int mouseY) {
        int startIndex = this.currentPage * (GRID_COLS * GRID_ROWS);

        for (int i = 0; i < (GRID_COLS * GRID_ROWS); i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= this.filteredCraftableItems.size()) {
                break;
            }

            Item item = this.filteredCraftableItems.get(itemIndex);
            ItemStack stack = new ItemStack(item);

            int x = this.gridLeft + (i % GRID_COLS) * GRID_SLOT_SIZE;
            int y = this.gridTop + (i / GRID_COLS) * GRID_SLOT_SIZE;

            graphics.renderFakeItem(stack, x, y);

            if (this.selectedItem == item) {
                graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            }

            if (mouseX >= x && mouseX < (x + 16) && mouseY >= y && mouseY < (y + 16)) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (checkGridClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean checkGridClick(double mouseX, double mouseY) {
        int startIndex = this.currentPage * (GRID_COLS * GRID_ROWS);
        for (int i = 0; i < (GRID_COLS * GRID_ROWS); i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= this.filteredCraftableItems.size()) {
                break;
            }

            int x = this.gridLeft + (i % GRID_COLS) * GRID_SLOT_SIZE;
            int y = this.gridTop + (i / GRID_COLS) * GRID_SLOT_SIZE;

            if (mouseX >= x && mouseX < (x + 16) && mouseY >= y && mouseY < (y + 16)) {
                this.selectedItem = this.filteredCraftableItems.get(itemIndex);
                // 播放点击音效 (可选)
                // Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }
}