package net.unitedcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;

public class ChatCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        // /nc — toggle nation chat mode
        dispatcher.register(CommandManager.literal("nc")
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                boolean nowOn = UnitedCraft.chatManager.toggleNationChat(player.getUuid());
                if (nowOn) {
                    player.sendMessage(Text.literal("§6Nation chat §aENABLED§6. Your messages now go to nation only. Use §e/nc §6to toggle off."));
                } else {
                    player.sendMessage(Text.literal("§6Nation chat §cDISABLED§6. Back to public chat."));
                }
                return 1;
            })
            // /nc <message> — send a single nation message without toggling
            .then(CommandManager.argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    String message = StringArgumentType.getString(ctx, "message");
                    UnitedCraft.chatManager.sendNationMessage(player, message, ctx.getSource().getServer());
                    return 1;
                }))
        );

        // /ac <message> — ally chat
        dispatcher.register(CommandManager.literal("ac")
            .then(CommandManager.argument("message", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    String message = StringArgumentType.getString(ctx, "message");
                    UnitedCraft.chatManager.sendAllyMessage(player, message,
                        ctx.getSource().getServer(), UnitedCraft.allianceManager);
                    return 1;
                }))
        );
    }
}
