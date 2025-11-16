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
import xczl.recursivecraft.network.C2SExecuteCraftPacket;
import xczl.recursivecraft.network.PacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecursiveCrafterScreen extends AbstractContainerScreen<RecursiveCrafterMenu> {

    // *** 关键: MODID 必须与你的 mods.toml 一致 ***
    // (你的 gradle.properties 显示 mod_id=recursivecraft)
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(RecursiveCraft.MODID, "textures/gui/recursive_crafter_gui.png");

    // 物品网格的布局
    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 6;
    private static final int GRID_SLOT_SIZE = 18;
    private int gridLeft;
    private int gridTop;

    // GUI 状态
    private EditBox searchBox;
    private EditBox amountBox;
    private List<Item> allCraftableItems = new ArrayList<>();
    private List<Item> filteredCraftableItems = new ArrayList<>();
    private int currentPage = 0;
    private int maxPage = 0;
    private Button prevButton, nextButton;
    private Button executeButton;
    private Item selectedItem = Items.AIR;

    // (占位符)
    private Component productText = Component.literal("... (未选择)");
    private Component needsText = Component.literal("... (未计算)");

    public RecursiveCrafterScreen(RecursiveCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        // *** 坐标修正 ***
        // 参照 CustomChestScreen.java 和你的新纹理
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();

        // --- 坐标修正 ---
        // 1. 网格坐标
        this.gridLeft = this.leftPos + 11; // 网格左侧
        this.gridTop = this.topPos + 25; // 网格顶部 (在搜索框下方)

        // 2. 分页栏 Y 坐标
        int pageButtonY = this.gridTop + (GRID_ROWS * GRID_SLOT_SIZE) + 4; // 25 + 108 + 4 = 137

        // 6. 右侧面板
        int rightPanelX = this.leftPos + 180;
        int rightSlotX = this.leftPos + 208;

        // 8. "执行合成" 按钮 Y
        int executeButtonY = this.topPos + 110;
        int executeButtonHeight = 20;

        // *** 需求 3: 移动默认的 "物品栏" 标签 ***
        // (保持你的设置)
//        this.inventoryLabelY = executeButtonY + executeButtonHeight + 6;
        this.inventoryLabelY = pageButtonY;
        // 与 "执行合成" 按钮的 X 坐标对齐 (X = 180)
        this.inventoryLabelX = rightPanelX-75;

        // 3. 获取所有可合成物品
        if (CraftingPlanner.isReady) {
            allCraftableItems = new ArrayList<>(CraftingPlanner.getInstance().getPathMemo().keySet());
            allCraftableItems.sort((a, b) -> a.getDescription().getString().compareTo(b.getDescription().getString()));
        }

        // --- 重新计算控件坐标 ---

        // 4. 搜索框
        this.searchBox = new EditBox(this.font, this.leftPos + 9, this.topPos + 7, GRID_COLS * GRID_SLOT_SIZE, 12, Component.literal("Search"));
        this.searchBox.setResponder(this::onSearchUpdate);
        this.addRenderableWidget(this.searchBox);

        // 5. 分页按钮
        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("<"), (btn) -> {
            this.currentPage = Math.max(0, this.currentPage - 1);
            updatePageButtons();
        }).bounds(this.leftPos + 8, pageButtonY, 16, 16).build()); // 16x16

        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), (btn) -> {
            this.currentPage = Math.min(this.maxPage, this.currentPage + 1);
            updatePageButtons();
        }).bounds(this.leftPos + 26, pageButtonY, 16, 16).build()); // 16x16 (8 + 16 + 2px 间距)

        // 7. 数量输入框
        this.amountBox = new EditBox(this.font, rightSlotX, this.topPos + 30, 30, 12, Component.literal("Amt"));
        this.amountBox.setValue("1");
        // *** 需求 1: 移除输入框背景/边框 ***
        // (保持你的设置)
//        this.amountBox.setBordered(false);
        this.addRenderableWidget(this.amountBox);

        // 8. "执行合成" 按钮
        this.executeButton = this.addRenderableWidget(Button.builder(Component.literal("执行合成"), (btn) -> {
            this.onExecutePressed();
        }).bounds(rightPanelX, executeButtonY, 60, executeButtonHeight).build());

        // 9. 首次过滤和分页
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
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("请先从左侧选择一个物品。"));
            return;
        }
        int amount = 1;
        try {
            amount = Integer.parseInt(this.amountBox.getValue());
            if (amount <= 0) amount = 1;
        } catch (NumberFormatException e) { amount = 1; }
        PacketHandler.INSTANCE.sendToServer(new C2SExecuteCraftPacket(this.selectedItem, amount));
    }
    // ... (结束复制)


    // --- 渲染 ---

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        graphics.blit(BACKGROUND_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 参照 CustomChestScreen.java (renderBackground 只需要一个参数)
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        // 渲染搜索框和数量框 (保持你的设置)
        this.searchBox.render(graphics, mouseX, mouseY, partialTicks);
        this.amountBox.render(graphics, mouseX, mouseY, partialTicks);

        // *** 修复: 所有的 drawString 都被移动到了 renderLabels ***

        // 渲染 "虚拟" 物品网格
        renderCraftableItems(graphics, mouseX, mouseY);

        // 渲染选中的物品 (在右上角的槽位)
        if (this.selectedItem != Items.AIR) {
            // (保持你的设置)
            int rightSlotX = this.leftPos + 208;
            graphics.renderFakeItem(new ItemStack(this.selectedItem), rightSlotX, this.topPos + 8);
        }

        // 渲染玩家背包槽的 Tooltip
        this.renderTooltip(graphics, mouseX, mouseY);
    }


    // *** 修复: 添加 renderLabels 方法来绘制所有文本 ***
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 1. 调用 super.renderLabels()，它会使用你在 init() 中设置的 inventoryLabelX/Y 绘制 "物品栏"
        super.renderLabels(graphics, mouseX, mouseY);

        // --- 使用 相对坐标 (没有 this.leftPos/this.topPos) 绘制你自己的文本 ---

        // 2. 绘制 "产物:" 和 "数量:"
        // (180 是你设置的 rightPanelX 的相对值)
        int rightPanelX_relative = 180;
        graphics.drawString(this.font, Component.literal("产物:"), rightPanelX_relative, 10, 0x404040);
        graphics.drawString(this.font, Component.literal("数量:"), rightPanelX_relative, 32, 0x404040);

        // 3. 绘制分页文本
        String pageText = String.format("%d / %d", this.currentPage + 1, this.maxPage + 1);

        // (从你的代码中复制相对坐标的计算)
        int gridTop_relative = 25; // (来自 this.topPos + 25)
        int pageButtonY_relative = gridTop_relative + (GRID_ROWS * GRID_SLOT_SIZE) + 4; // 25 + 108 + 4 = 137
        int pageTextY_relative = pageButtonY_relative + 16 + 4; // 按钮 Y + 按钮高度 + 4px 间距

        // (8 是你设置的 this.leftPos + 8 的相对值)
        graphics.drawString(this.font, pageText, 8, pageTextY_relative, 0x404040);
    }


    // 渲染物品网格 (逻辑不变)
    private void renderCraftableItems(GuiGraphics graphics, int mouseX, int mouseY) {
        // ... (复制自你提供的 RecursiveCrafterScreen.java, 无需修改)
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
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
        // ... (结束复制)
    }

    // 交互 (逻辑不变)
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ... (复制自你提供的 RecursiveCrafterScreen.java, 无需修改)
        if (checkGridClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // 检查网格点击 (逻辑不变)
    private boolean checkGridClick(double mouseX, double mouseY) {
        // ... (复制自你提供的 RecursiveCrafterScreen.java, 无需修改)
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
                return true;
            }
        }
        return false;
    }
}