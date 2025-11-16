// ======================================================================
// 档案: src/main/java/xczl/recursivecraft/command/RecursiveCraftCommand.java
// (已更新日志逻辑)
// ======================================================================
package xczl.recursivecraft.command;

import xczl.recursivecraft.core.CraftingPlanner;
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
        // (注册逻辑不变)
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
        // (1-4 步不变)
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        Item targetItem = itemInput.getItem();
        int amount = IntegerArgumentType.getInteger(context, "amount");
        CommandSourceStack source = context.getSource();

        if (!CraftingPlanner.isReady) {
            source.sendFailure(Component.literal(
                    "[RecursiveCraft] 合成规划器仍在启动中，请稍后几秒再试..."
            ));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("正在计算合成方案..."), false);
        TransactionCalculator calculator = new TransactionCalculator(player.getInventory());
        CraftingTransaction transaction = calculator.calculate(targetItem, amount, true);
        transaction.addProvide(targetItem, amount);

        // === [DEBUG] 打印最终事务表 (将调用更新后的 logTransaction) ===
        logTransaction(source, transaction);
        // ============================

        // (5. 检查“净需求” - 逻辑不变)
        Map<Item, Integer> missingMaterials = new HashMap<>();
        Map<Item, Integer> netDeltas = transaction.getNetDeltas(); //

        for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
            int netAmount = entry.getValue();
            if (netAmount < 0) {
                Item neededItem = entry.getKey();
                int neededAmount = -netAmount;
                int amountInInventory = player.getInventory().countItem(neededItem);
                if (amountInInventory < neededAmount) {
                    missingMaterials.put(neededItem, neededAmount - amountInInventory);
                }
            }
        }

        // (6. 材料不足提示 - 逻辑不变)
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

        // (7. 执行事务 - 逻辑不变)
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

    // <<< [修复] 调试日志辅助函数 (更新为显示“净”变化) >>>
    /**
     * [DEBUG] 调试日志辅助函数
     * (已更新) 显示 "净消耗" 和 "净产出"
     */
    private static void logTransaction(CommandSourceStack source, CraftingTransaction transaction) {
        source.sendSuccess(() -> Component.literal("--- [RecursiveCraft DEBUG] ---"), false);

        // 1. 获取净变化
        Map<Item, Integer> netDeltas = transaction.getNetDeltas(); //

        // 2. 分离 净消耗 和 净产出
        Map<Item, Integer> netNeeds = new HashMap<>();
        Map<Item, Integer> netProvides = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : netDeltas.entrySet()) {
            if (entry.getValue() < 0) { // 负数 = 净消耗
                netNeeds.put(entry.getKey(), -entry.getValue()); // 存为正数
            } else if (entry.getValue() > 0) { // 正数 = 净产出
                netProvides.put(entry.getKey(), entry.getValue());
            }
        }

        // 3. 打印净消耗
        source.sendSuccess(() -> Component.literal("【净消耗 (Net Needs)】"), false);
        if (netNeeds.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  (无)"), false);
        } else {
            netNeeds.forEach((item, amount) -> {
                source.sendSuccess(() -> Component.literal(String.format("  - %dx %s", amount, item.getDescription().getString())), false);
            });
        }

        // 4. 打印净产出
        source.sendSuccess(() -> Component.literal("【净产出 (Net Provides)】"), false);
        if (netProvides.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  (无)"), false);
        } else {
            netProvides.forEach((item, amount) -> {
                source.sendSuccess(() -> Component.literal(String.format("  - %dx %s", amount, item.getDescription().getString())), false);
            });
        }
        source.sendSuccess(() -> Component.literal("---------------------------------"), false);
    }
}