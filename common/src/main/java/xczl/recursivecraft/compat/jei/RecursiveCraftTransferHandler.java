package xczl.recursivecraft.compat.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft; // 引入 Minecraft
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import xczl.recursivecraft.networking.C2SExecuteCraftPacket;
import xczl.recursivecraft.networking.PacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecursiveCraftTransferHandler<C extends AbstractContainerMenu> implements IRecipeTransferHandler<C, CraftingRecipe> {

    private final Class<C> containerClass;
    private final IRecipeTransferHandlerHelper transferHelper;

    public RecursiveCraftTransferHandler(Class<C> containerClass, IRecipeTransferHandlerHelper transferHelper) {
        this.containerClass = containerClass;
        this.transferHelper = transferHelper;
    }

    @Override
    public Class<C> getContainerClass() {
        return containerClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            C container,
            CraftingRecipe recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer
    ) {
        // 1. 基础检查
        ItemStack output = recipe.getResultItem(player.level().registryAccess());
        if (output.isEmpty()) return new SimpleError(IRecipeTransferError.Type.USER_FACING, Component.literal("配方无效"));

        boolean isCtrlDown = Screen.hasControlDown();

        // === 1. 递归合成器 或 按住 Ctrl (强制执行) ===
        // 在这些情况下，我们完全接管，忽略原版逻辑
        if (isCtrlDown || !(container instanceof InventoryMenu)) {
            if (!doTransfer) return null; // 验证通过 (按钮显示为蓝色)

            // 执行合成
            int craftAmount = maxTransfer ? 64 : 1;
            PacketHandler.CHANNEL.sendToServer(new C2SExecuteCraftPacket(output.getItem(), craftAmount));
            return null;
        }

        // === 2. 玩家背包 (未按 Ctrl) ===
        // 这里我们需要“智能分流”
        if (container instanceof InventoryMenu) {
            // 判断是否为大配方 (2x2 放不下)
            boolean isBigRecipe = !recipe.canCraftInDimensions(2, 2);

            if (isBigRecipe) {
                // 情况 A: 3x3 配方 (背包放不下) -> 我们必须接管
                // 此时原版 Handler 会隐藏按钮，所以我们要负责报错

                // 1. 计算缺少的材料
                List<IRecipeSlotView> missingSlots = calculateMissingSlots(recipe, recipeSlots, player);

                Component warningText = Component.literal("配方过大，请按 Ctrl + 点击 进行递归合成")
                        .withStyle(net.minecraft.ChatFormatting.RED);

                // 2. 返回 JEI 标准错误 (会自动处理 Tooltip 和高亮)
                if (!missingSlots.isEmpty()) {
                    // 如果有缺材料，高亮它们 + 显示提示
                    return transferHelper.createUserErrorForMissingSlots(warningText, missingSlots);
                } else {
                    // 如果材料齐了 (只是配方大)，只显示提示
                    return transferHelper.createUserErrorWithTooltip(warningText);
                }
            } else {
                // 情况 B: 2x2 配方 (背包能做) -> 放行给原版
                // 返回 INTERNAL 错误，JEI 会自动去试下一个 Handler (即原版 Handler)
                return new SimpleError(IRecipeTransferError.Type.INTERNAL, Component.empty());
            }
        }

        return null;
    }

    /**
     * 计算配方中缺失材料对应的 JEI 槽位视图
     */
    private List<IRecipeSlotView> calculateMissingSlots(CraftingRecipe recipe, IRecipeSlotsView recipeSlots, Player player) {
        List<IRecipeSlotView> missingViews = new ArrayList<>();
        List<ItemStack> inventoryCopy = new ArrayList<>();

        // 复制玩家背包 (模拟扣除)
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                inventoryCopy.add(stack.copy());
            }
        }

        // 获取 JEI 界面上的所有输入槽位
        List<IRecipeSlotView> inputSlots = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        List<Ingredient> ingredients = recipe.getIngredients();

        // 遍历配方原料
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) continue;

            boolean found = false;
            // 在模拟背包中寻找匹配项
            for (ItemStack invStack : inventoryCopy) {
                if (!invStack.isEmpty() && ingredient.test(invStack)) {
                    invStack.shrink(1); // 模拟扣除
                    if (invStack.isEmpty()) {
                        // 如果堆叠用完了，从列表中移除或者设为 Empty，避免重复使用 (这里简化处理)
                        // 严谨的写法是移除，但 shrink 已经修改了 count，下次 test 会失败或 count 为 0
                    }
                    found = true;
                    break;
                }
            }

            if (!found) {
                // 如果没找到，把对应的 JEI 槽位加到缺失列表
                if (i < inputSlots.size()) {
                    missingViews.add(inputSlots.get(i));
                }
            }
        }
        return missingViews;
    }

    // 自定义错误类，用于处理 INTERNAL 类型
    private static class SimpleError implements IRecipeTransferError {
        private final Type type;
        private final Component message;

        public SimpleError(Type type, Component message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public Type getType() { return type; }

        @Override
        public void showError(GuiGraphics graphics, int mouseX, int mouseY, IRecipeSlotsView recipeSlotsView, int recipeX, int recipeY) {
            // INTERNAL 类型的错误，JEI 不会调用这个方法，它会直接跳过
            // USER_FACING 类型的错误，我们上面使用了 Helper，所以也不会走到这里
            // 但为了保险，我们可以加上绘制逻辑
            if (type == Type.USER_FACING) {
                graphics.renderTooltip(Minecraft.getInstance().font, message, mouseX, mouseY);
            }
        }
    }
}