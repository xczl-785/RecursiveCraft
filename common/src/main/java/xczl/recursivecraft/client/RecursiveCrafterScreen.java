package xczl.recursivecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
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
import xczl.recursivecraft.utils.PinyinUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecursiveCrafterScreen extends AbstractContainerScreen<RecursiveCrafterMenu> {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(RecursiveCraft.MOD_ID, "textures/gui/recursive_crafter_gui.png");

    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 6;
    private static final int GRID_SLOT_SIZE = 18;
    private int gridLeft;
    private int gridTop;

    private EditBox searchBox;
    private EditBox amountBox;
    private List<Item> allCraftableItems = new ArrayList<>();
    private List<Item> filteredCraftableItems = new ArrayList<>();
    private int currentPage = 0;
    private int maxPage = 0;
    private Button prevButton, nextButton;
    private Button executeButton;
    private Item selectedItem = Items.AIR;


    public RecursiveCrafterScreen(RecursiveCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();

        // 加载收藏配置
        ClientFavorites.load();

        this.gridLeft = this.leftPos + 11;
        this.gridTop = this.topPos + 25;
        int pageButtonY = this.gridTop + (GRID_ROWS * GRID_SLOT_SIZE) + 4;
        int rightPanelX = this.leftPos + 180;
        int rightSlotX = this.leftPos + 208;
        int executeButtonY = this.topPos + 110;

        this.inventoryLabelY = pageButtonY;
        this.inventoryLabelX = rightPanelX - 75;

        // 获取数据并进行初始排序
        if (CraftingPlanner.isReady) {
            allCraftableItems = new ArrayList<>(CraftingPlanner.getInstance().getPathMemo().keySet());
            sortItems(allCraftableItems);
        }

        this.searchBox = new EditBox(this.font, this.leftPos + 9, this.topPos + 7, GRID_COLS * GRID_SLOT_SIZE, 12, Component.literal("Search"));
        this.searchBox.setResponder(this::onSearchUpdate);
        this.addRenderableWidget(this.searchBox);

        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("<"), (btn) -> {
            this.currentPage = Math.max(0, this.currentPage - 1);
            updatePageButtons();
        }).bounds(this.leftPos + 8, pageButtonY, 16, 16).build());

        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), (btn) -> {
            this.currentPage = Math.min(this.maxPage, this.currentPage + 1);
            updatePageButtons();
        }).bounds(this.leftPos + 26, pageButtonY, 16, 16).build());

        this.amountBox = new EditBox(this.font, rightSlotX, this.topPos + 30, 30, 12, Component.literal("Amt"));
        this.amountBox.setValue("1");
        this.addRenderableWidget(this.amountBox);

        this.executeButton = this.addRenderableWidget(Button.builder(Component.literal("执行合成"), (btn) -> {
            this.onExecutePressed();
        }).bounds(rightPanelX, executeButtonY, 60, 20).build());

        onSearchUpdate(this.searchBox.getValue());
    }

    /**
     * [新增] 拦截按键事件，防止输入 "E" 时关闭界面
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 1. 让输入框优先处理标准按键
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers) || this.amountBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // 2. 如果输入框处于聚焦状态，且按下了 "背包键" (默认E)，则拦截事件，防止界面关闭
        if ((this.searchBox.isFocused() || this.amountBox.isFocused())
                && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sortItems(List<Item> list) {
        list.sort((item1, item2) -> {
            boolean fav1 = ClientFavorites.isFavorite(item1);
            boolean fav2 = ClientFavorites.isFavorite(item2);

            if (fav1 && !fav2) return -1;
            if (!fav1 && fav2) return 1;

            String id1 = BuiltInRegistries.ITEM.getKey(item1).toString();
            String id2 = BuiltInRegistries.ITEM.getKey(item2).toString();
            return id1.compareTo(id2);
        });
    }

    private void onSearchUpdate(String query) {
        String lowerQuery = query.toLowerCase().trim();

        List<Item> filtered = this.allCraftableItems.stream()
                .filter(item -> {
                    String displayName = item.getDescription().getString();
                    String registryId = BuiltInRegistries.ITEM.getKey(item).toString();

                    if (PinyinUtils.matches(displayName, query)) return true;
                    if (registryId.contains(lowerQuery)) return true;

                    return false;
                })
                .collect(Collectors.toList());

        sortItems(filtered);

        this.filteredCraftableItems = filtered;
        this.currentPage = 0;
        this.maxPage = Math.max(0, (this.filteredCraftableItems.size() - 1) / (GRID_COLS * GRID_ROWS));
        updatePageButtons();
    }

    private void updatePageButtons() {
        this.prevButton.active = this.currentPage > 0;
        this.nextButton.active = this.currentPage < this.maxPage;
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
        graphics.drawString(this.font, Component.literal("产物:"), rightPanelX_relative, 10, 0x404040, false);
        graphics.drawString(this.font, Component.literal("数量:"), rightPanelX_relative, 32, 0x404040, false);

        if (!CraftingPlanner.isReady) {
            // [新增] 加载提示
            graphics.drawString(this.font, Component.literal("正在分析配方..."), 8, 50, 0xFF0000, false);
            graphics.drawString(this.font, Component.literal("请稍后重开界面"), 8, 62, 0xFF0000, false);
        } else {
            String pageText = String.format("%d / %d", this.currentPage + 1, this.maxPage + 1);
            int gridTop_relative = 25;
            int pageButtonY_relative = gridTop_relative + (GRID_ROWS * GRID_SLOT_SIZE) + 4;
            int pageTextY_relative = pageButtonY_relative + 16 + 4;
            graphics.drawString(this.font, pageText, 8, pageTextY_relative, 0x404040, false);
        }
    }

    private void renderCraftableItems(GuiGraphics graphics, int mouseX, int mouseY) {
        int startIndex = this.currentPage * (GRID_COLS * GRID_ROWS);

        for (int i = 0; i < (GRID_COLS * GRID_ROWS); i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= this.filteredCraftableItems.size()) break;

            Item item = this.filteredCraftableItems.get(itemIndex);
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
                    tooltip.add(Component.literal("★ 已收藏").withStyle(ChatFormatting.YELLOW));
                } else {
                    // [修改] 提示文案改为 "右键"
                    tooltip.add(Component.literal("右键点击收藏").withStyle(ChatFormatting.DARK_GRAY));
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
        int startIndex = this.currentPage * (GRID_COLS * GRID_ROWS);
        for (int i = 0; i < (GRID_COLS * GRID_ROWS); i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= this.filteredCraftableItems.size()) break;

            int x = this.gridLeft + (i % GRID_COLS) * GRID_SLOT_SIZE;
            int y = this.gridTop + (i / GRID_COLS) * GRID_SLOT_SIZE;

            if (mouseX >= x && mouseX < (x + 16) && mouseY >= y && mouseY < (y + 16)) {
                Item clickedItem = this.filteredCraftableItems.get(itemIndex);

                // [优化] 右键点击收藏
                if (button == 1) {
                    // 1. 切换收藏状态
                    ClientFavorites.toggleFavorite(clickedItem);

                    // 2. 播放音效 (可选，增加反馈感)
                    // Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

                    // [重要改动]
                    // 这里删除了 onSearchUpdate(...) 调用。
                    // 这样物品会留在原地，星星立刻亮起，且不会跳转回第一页。
                    // 只有当你下次搜索或重新打开 GUI 时，它才会排到最前面。

                    return true;
                }

                // 左键点击选择
                if (button == 0) {
                    this.selectedItem = clickedItem;
                    return true;
                }
            }
        }
        return false;
    }
}