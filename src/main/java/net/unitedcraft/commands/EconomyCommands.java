package net.unitedcraft.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.managers.JobManager;

import java.util.Arrays;
import java.util.stream.Collectors;

public class EconomyCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {

        // /balance  — check your own balance
        dispatcher.register(CommandManager.literal("balance")
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                String uuid = player.getUuidAsString();
                double bal = UnitedCraft.economyManager.getBalance(uuid);
                player.sendMessage(Text.literal("§aYour balance: §e" +
                    UnitedCraft.economyManager.format(bal)), false);
                return 1;
            })
        );

        // /bal — alias
        dispatcher.register(CommandManager.literal("bal")
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                double bal = UnitedCraft.economyManager.getBalance(player.getUuidAsString());
                player.sendMessage(Text.literal("§aYour balance: §e" +
                    UnitedCraft.economyManager.format(bal)), false);
                return 1;
            })
        );

        // /pay <player> <amount>
        dispatcher.register(CommandManager.literal("pay")
            .then(CommandManager.argument("player", StringArgumentType.word())
            .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
            .executes(ctx -> {
                ServerPlayerEntity sender = ctx.getSource().getPlayer();
                if (sender == null) return 0;

                String targetName = StringArgumentType.getString(ctx, "player");
                double amount = DoubleArgumentType.getDouble(ctx, "amount");

                ServerPlayerEntity target = ctx.getSource().getServer()
                    .getPlayerManager().getPlayer(targetName);

                if (target == null) {
                    sender.sendMessage(Text.literal("§cPlayer §e" + targetName + "§c is not online."), false);
                    return 0;
                }
                if (target.equals(sender)) {
                    sender.sendMessage(Text.literal("§cYou can't pay yourself."), false);
                    return 0;
                }

                boolean success = UnitedCraft.economyManager.transfer(
                    sender.getUuidAsString(), target.getUuidAsString(), amount);

                if (success) {
                    sender.sendMessage(Text.literal("§aYou paid §e" + targetName +
                        " §a" + UnitedCraft.economyManager.format(amount) + "."), false);
                    target.sendMessage(Text.literal("§aYou received §e" +
                        UnitedCraft.economyManager.format(amount) +
                        "§a from §e" + sender.getName().getString() + "."), false);
                } else {
                    sender.sendMessage(Text.literal("§cInsufficient funds."), false);
                }
                return success ? 1 : 0;
            })))
        );

        // /job — view your current job + daily earnings
        dispatcher.register(CommandManager.literal("job")
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                String uuid = player.getUuidAsString();
                JobManager.Job job = UnitedCraft.jobManager.getJob(uuid);
                double earned = UnitedCraft.jobManager.getDailyEarned(uuid);
                double cap = job != null ? UnitedCraft.jobManager.getDailyCap(job) : 0;

                if (job == null) {
                    player.sendMessage(Text.literal(
                        "§eYou have no job! Use §a/job set <job>§e to pick one.\n" +
                        "§7Available: §fMINER, FARMER, LUMBERJACK, HUNTER, FISHERMAN"), false);
                } else {
                    player.sendMessage(Text.literal(
                        "§6Your Job: §e" + job.name() + "\n" +
                        "§6Today's earnings: §e" + UnitedCraft.economyManager.format(earned) +
                        " §7/ §e" + UnitedCraft.economyManager.format(cap) + " §7daily cap"), false);
                }
                return 1;
            })
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("jobname", StringArgumentType.word())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    String jobName = StringArgumentType.getString(ctx, "jobname").toUpperCase();
                    try {
                        JobManager.Job job = JobManager.Job.valueOf(jobName);
                        UnitedCraft.jobManager.setJob(player.getUuidAsString(), job);
                        player.sendMessage(Text.literal(
                            "§aYou are now a §e" + job.name() + "§a!\n" +
                            getJobDescription(job)), false);
                        return 1;
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(Text.literal(
                            "§cInvalid job. Choose from: §eMINER, FARMER, LUMBERJACK, HUNTER, FISHERMAN"), false);
                        return 0;
                    }
                })))
            .then(CommandManager.literal("list")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    player.sendMessage(Text.literal(
                        "§6=== Available Jobs ===\n" +
                        "§eMINER §7- Mine ores for pay (cap: $500/day)\n" +
                        "§eFARMER §7- Harvest crops for pay (cap: $200/day)\n" +
                        "§eLUMBERJACK §7- Chop logs for pay (cap: $150/day)\n" +
                        "§eHUNTER §7- Kill mobs for pay (cap: $300/day)\n" +
                        "§eFISHERMAN §7- Catch fish for pay (cap: $200/day)\n" +
                        "§7Use §f/job set <name>§7 to pick one."), false);
                    return 1;
                }))
        );

        // /eco give <player> <amount> — admin command
        dispatcher.register(CommandManager.literal("eco")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.literal("give")
                .then(CommandManager.argument("player", StringArgumentType.word())
                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                .executes(ctx -> {
                    String targetName = StringArgumentType.getString(ctx, "player");
                    double amount = DoubleArgumentType.getDouble(ctx, "amount");
                    ServerPlayerEntity target = ctx.getSource().getServer()
                        .getPlayerManager().getPlayer(targetName);
                    if (target == null) {
                        ctx.getSource().sendMessage(Text.literal("§cPlayer not found or offline."));
                        return 0;
                    }
                    UnitedCraft.economyManager.deposit(target.getUuidAsString(), amount, "admin_give");
                    ctx.getSource().sendMessage(Text.literal("§aGave §e" +
                        UnitedCraft.economyManager.format(amount) + "§a to §e" + targetName + "."));
                    target.sendMessage(Text.literal("§aAn admin gave you §e" +
                        UnitedCraft.economyManager.format(amount) + "."), false);
                    return 1;
                }))))
            .then(CommandManager.literal("take")
                .then(CommandManager.argument("player", StringArgumentType.word())
                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                .executes(ctx -> {
                    String targetName = StringArgumentType.getString(ctx, "player");
                    double amount = DoubleArgumentType.getDouble(ctx, "amount");
                    ServerPlayerEntity target = ctx.getSource().getServer()
                        .getPlayerManager().getPlayer(targetName);
                    if (target == null) {
                        ctx.getSource().sendMessage(Text.literal("§cPlayer not found or offline."));
                        return 0;
                    }
                    boolean ok = UnitedCraft.economyManager.withdraw(
                        target.getUuidAsString(), amount, "admin_take");
                    ctx.getSource().sendMessage(Text.literal(ok
                        ? "§aTook §e" + UnitedCraft.economyManager.format(amount) + "§a from §e" + targetName + "."
                        : "§cPlayer doesn't have enough funds."));
                    return ok ? 1 : 0;
                }))))
        );
    }

    private static String getJobDescription(JobManager.Job job) {
        return switch (job) {
            case MINER      -> "§7Mine ores to earn money. Diamonds pay the most!";
            case FARMER     -> "§7Harvest fully grown crops to earn money.";
            case LUMBERJACK -> "§7Chop logs to earn money. Nether stems pay more!";
            case HUNTER     -> "§7Kill mobs to earn money. Blazes and Endermen pay best!";
            case FISHERMAN  -> "§7Catch fish to earn money. Use a fishing rod!";
        };
    }
}
