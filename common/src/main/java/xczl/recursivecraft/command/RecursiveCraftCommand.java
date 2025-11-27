package xczl.recursivecraft.command;

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
import net.minecraft.world.item.Item;
import xczl.recursivecraft.core.CraftingTaskExecutor;

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

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();

        ItemInput itemInput = ItemArgument.getItem(context, "item");
        Item targetItem = itemInput.getItem();
        int amount = IntegerArgumentType.getInteger(context, "amount");

        // [修正] 补全 null 参数，并显式指定 msg 类型
        boolean success = CraftingTaskExecutor.tryExecute(
                player,
                targetItem,
                amount,
                null, // 命令行默认不指定配方，传 null
                (Component msg) -> source.sendSuccess(() -> msg, false) // 显式类型，防止编译器发懵
        );

        return success ? 1 : 0;
    }
}