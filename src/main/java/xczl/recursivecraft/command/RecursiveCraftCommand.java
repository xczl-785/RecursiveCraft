package xczl.recursivecraft.command;

import xczl.recursivecraft.core.CraftingPlanner; // <<< 导入
import xczl.recursivecraft.core.TransactionCalculator;
import xczl.recursivecraft.data.CraftingTransaction;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

public class RecursiveCraftCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                Commands.literal("craft_recursive")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(RecursiveCraftCommand::run)
                                )
                        )
        );
    }

    /**
     * 这是 run 函数的完整代码
     */
    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // 1. 获取输入
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        Item targetItem = itemInput.getItem();
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CommandSourceStack source = context.getSource();

        // === [修复 Bug 2A: 异步竞争] ===
        // 在执行任何操作前, 检查 CraftingPlanner 是否已完成其后台计算
        if (!CraftingPlanner.isReady) {
            source.sendFailure(Component.literal(
                    "[RecursiveCraft] 合成规划器仍在启动中，请稍后几秒再试..."
            ));
            return 0;
        }
        // ============================

        // 2. 方便调试，暂时屏蔽创造模式的检测
//        if (player.isCreative()) {
//            source.sendFailure(Component.literal("[RecursiveCraft] 本功能仅在生存模式下生效。"));
//            return 0;
//        }

        // 3. A. 调用 TransactionCalculator
        source.sendSuccess(() -> Component.literal("正在计算合成方案..."), false);

        TransactionCalculator calculator = new TransactionCalculator(player.getInventory());

        // === [修复 Bug 1: 背包刷新] ===
        // 调用 calculate 时, 传入 'true'
        // 标记 'targetItem' 是一个 "最终产品", 不应从背包消耗。
        CraftingTransaction transaction = calculator.calculate(targetItem, amount, true);
        // ============================

        // 4. 将 *最终产物* 加入到 "Provides" 列表
        transaction.addProvide(targetItem, amount);

        // === [DEBUG] 打印最终事务表 ===
        logTransaction(source, transaction);
        // ============================

        // 5. B. 检查 '总事务表.Needs'
        Map<Item, Integer> missingMaterials = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : transaction.getNeeds().entrySet()) {
            Item neededItem = entry.getKey();
            int neededAmount = entry.getValue();

            // 检查玩家背包
            int amountInInventory = player.getInventory().countItem(neededItem);

            if (amountInInventory < neededAmount) {
                missingMaterials.put(neededItem, neededAmount - amountInInventory);
            }
        }

        // 6. 否 (材料不足) -> 提示
        if (!missingMaterials.isEmpty()) {
            StringBuilder message = new StringBuilder("缺少材料: ");
            for (Map.Entry<Item, Integer> missing : missingMaterials.entrySet()) {
                message.append(missing.getValue())
                        .append("x ")
                        .append(missing.getKey().getDescription().getString())
                        .append(", ");
            }
            source.sendFailure(Component.literal(message.substring(0, message.length() - 2)));
            return 0;
        }

        // 7. 是 (材料充足) -> C. 执行事务
        try {
            transaction.execute(player);
            source.sendSuccess(
                    () -> Component.literal(String.format("合成成功: %dx %s", amount, targetItem.getDescription().getString())),
                    true
            );
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("合成失败，发生未知错误: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * [DEBUG] 调试日志辅助函数 (无修改)
     */
    private static void logTransaction(CommandSourceStack source, CraftingTransaction transaction) {
        source.sendSuccess(() -> Component.literal("--- [RecursiveCraft DEBUG] ---"), false);

        // 打印消耗表
        source.sendSuccess(() -> Component.literal("【消耗表 (Needs)】"), false);
        if (transaction.getNeeds().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  (无)"), false);
        } else {
            transaction.getNeeds().forEach((item, amount) -> {
                source.sendSuccess(() -> Component.literal(String.format("  - %dx %s", amount, item.getDescription().getString())), false);
            });
        }

        // 打印产物表
        source.sendSuccess(() -> Component.literal("【产物表 (Provides)】"), false);
        if (transaction.getProvides().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  (无)"), false);
        } else {
            transaction.getProvides().forEach((item, amount) -> {
                source.sendSuccess(() -> Component.literal(String.format("  - %dx %s", amount, item.getDescription().getString())), false);
            });
        }
        source.sendSuccess(() -> Component.literal("---------------------------------"), false);
    }
}