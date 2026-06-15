package xczl.recursivecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import xczl.recursivecraft.RecursiveCraft;
import xczl.recursivecraft.core.CraftingPlanner;
import xczl.recursivecraft.menu.RecursiveCrafterMenu;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.networking.PacketHandler;

import java.util.List;

public class RecursiveCrafterScreen extends AbstractContainerScreen<RecursiveCrafterMenu> {

    private static final Identifier BACKGROUND_TEXTURE = RecursiveCraft.id("textures/gui/recursive_crafter_gui.png");

    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 6;
    private static final int GRID_SLOT_SIZE = 18;
    private int gridLeft;
    private int gridTop;

    private EditBox searchBox;
    private EditBox amountBox;
    private Button prevButton, nextButton;
    private Button executeButton;
    private CraftableTarget selectedTarget;

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

        itemList.tryLoadFromPlanner();

        this.searchBox = new EditBox(this.font, this.leftPos + 9, this.topPos + 7, GRID_COLS * GRID_SLOT_SIZE, 12, Component.translatable("recursivecraft.gui.search"));
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

        this.amountBox = new EditBox(this.font, rightSlotX, this.topPos + 30, 30, 12, Component.translatable("recursivecraft.gui.amount_input"));
        this.amountBox.setValue("1");
        this.addRenderableWidget(this.amountBox);

        this.executeButton = this.addRenderableWidget(Button.builder(Component.translatable("recursivecraft.gui.execute"), (btn) -> {
            this.onExecutePressed();
        }).bounds(rightPanelX, executeButtonY, 60, 20).build());

        itemList.search(this.searchBox.getValue());
        updatePageButtons();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.searchBox.keyPressed(event) || this.amountBox.keyPressed(event)) {
            return true;
        }
        if ((this.searchBox.isFocused() || this.amountBox.isFocused())
                && this.minecraft.options.keyInventory.matches(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    private void updatePageButtons() {
        this.prevButton.active = itemList.hasPrevPage();
        this.nextButton.active = itemList.hasNextPage();
    }

    private void onExecutePressed() {
        if (this.selectedTarget == null) return;
        int amount = 1;
        try {
            amount = Integer.parseInt(this.amountBox.getValue());
            if (amount <= 0) amount = 1;
        } catch (NumberFormatException e) {
            amount = 1;
        }
        PacketHandler.sendToServer(new C2SExecuteCraftPacket(
                this.selectedTarget.item(),
                amount,
                this.selectedTarget.forcedRecipeId(),
                this.selectedTarget.targetOutputSpec(),
                this.selectedTarget.displayedIngredients()
        ));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);

        if (itemList.tryLoadFromPlanner()) {
            itemList.forceRefresh();
            itemList.search(this.searchBox.getValue());
            updatePageButtons();
        }

        this.searchBox.render(graphics, mouseX, mouseY, partialTicks);
        this.amountBox.render(graphics, mouseX, mouseY, partialTicks);

        renderCraftableItems(graphics, mouseX, mouseY);

        if (this.selectedTarget != null) {
            int rightSlotX = this.leftPos + 208;
            graphics.renderFakeItem(this.selectedTarget.displayStack(), rightSlotX, this.topPos + 8);
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
        List<CraftableTarget> pageItems = itemList.getCurrentPageItems();

        for (int i = 0; i < pageItems.size(); i++) {
            CraftableTarget target = pageItems.get(i);
            ItemStack stack = target.displayStack();

            int x = this.gridLeft + (i % GRID_COLS) * GRID_SLOT_SIZE;
            int y = this.gridTop + (i / GRID_COLS) * GRID_SLOT_SIZE;

            graphics.renderFakeItem(stack, x, y);

            if (ClientFavorites.isFavorite(target.item())) {
                graphics.fill(x, y, x + 4, y + 4, 0xFFFFD700);
            }

            if (target.equals(this.selectedTarget)) {
                graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            }

            if (mouseX >= x && mouseX < (x + 16) && mouseY >= y && mouseY < (y + 16)) {
                List<Component> tooltip = Screen.getTooltipFromItem(this.minecraft, stack);

                if (ClientFavorites.isFavorite(target.item())) {
                    tooltip.add(Component.translatable("recursivecraft.gui.favorited").withStyle(ChatFormatting.YELLOW));
                } else {
                    tooltip.add(Component.translatable("recursivecraft.gui.right_click_favorite").withStyle(ChatFormatting.DARK_GRAY));
                }

                List<ClientTooltipComponent> tooltipComponents = tooltip.stream()
                        .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                        .toList();
                graphics.renderTooltip(this.font, tooltipComponents, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, BACKGROUND_TEXTURE);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (checkGridClick(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    private boolean checkGridClick(double mouseX, double mouseY, int button) {
        List<CraftableTarget> pageItems = itemList.getCurrentPageItems();

        for (int i = 0; i < pageItems.size(); i++) {
            int x = this.gridLeft + (i % GRID_COLS) * GRID_SLOT_SIZE;
            int y = this.gridTop + (i / GRID_COLS) * GRID_SLOT_SIZE;

            if (mouseX >= x && mouseX < (x + 16) && mouseY >= y && mouseY < (y + 16)) {
                CraftableTarget clickedTarget = pageItems.get(i);

                if (button == 1) {
                    ClientFavorites.toggleFavorite(clickedTarget.item());
                    return true;
                }

                if (button == 0) {
                    this.selectedTarget = clickedTarget;
                    return true;
                }
            }
        }
        return false;
    }
}
