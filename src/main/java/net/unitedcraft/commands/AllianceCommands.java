package net.unitedcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.managers.AllianceManager;
import net.unitedcraft.managers.NationManager;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;

import java.util.List;
import java.util.Optional;

public class AllianceCommands {

    private static final NationManager nationManager = new NationManager();
    private static final AllianceManager allianceManager = new AllianceManager();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(CommandManager.literal("alliance")

            // /alliance propose <nation>
            .then(CommandManager.literal("propose")
                .then(CommandManager.argument("nation", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String targetName = StringArgumentType.getString(ctx, "nation");
                        try {
                            Optional<NationMember> member = nationManager.getMember(player.getUuid());
                            if (member.isEmpty() || !member.get().isSeniorLeadership()) {
                                player.sendMessage(Text.literal("§cOnly the President or Vice President can propose alliances."));
                                return 0;
                            }
                            Optional<Nation> target = nationManager.getNationByName(targetName);
                            if (target.isEmpty()) {
                                player.sendMessage(Text.literal("§cNation not found: " + targetName));
                                return 0;
                            }
                            allianceManager.proposeAlliance(member.get().getNationId(), target.get().getId());

                            Optional<Nation> own = nationManager.getNationById(member.get().getNationId());
                            String ownName = own.map(Nation::getName).orElse("Your nation");

                            // Notify target nation's online leaders
                            nationManager.getMembers(target.get().getId()).stream()
                                .filter(NationMember::isSeniorLeadership)
                                .forEach(m -> {
                                    var p = ctx.getSource().getServer().getPlayerManager().getPlayer(m.getPlayerUuid());
                                    if (p != null) p.sendMessage(Text.literal(
                                        "§6📜 Alliance Proposal: §e" + ownName + " §6has proposed an alliance with §e" +
                                        target.get().getName() + "§6! Use §e/alliance accept " + ownName + " §6to accept."));
                                });

                            player.sendMessage(Text.literal("§aAlliance proposal sent to §e" + target.get().getName() + "§a."));
                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /alliance accept <nation>
            .then(CommandManager.literal("accept")
                .then(CommandManager.argument("nation", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String proposerName = StringArgumentType.getString(ctx, "nation");
                        try {
                            Optional<NationMember> member = nationManager.getMember(player.getUuid());
                            if (member.isEmpty() || !member.get().isSeniorLeadership()) {
                                player.sendMessage(Text.literal("§cOnly the President or Vice President can accept alliances."));
                                return 0;
                            }
                            Optional<Nation> proposer = nationManager.getNationByName(proposerName);
                            if (proposer.isEmpty()) {
                                player.sendMessage(Text.literal("§cNation not found: " + proposerName));
                                return 0;
                            }

                            allianceManager.acceptAlliance(member.get().getNationId(), proposer.get().getId());

                            Optional<Nation> own = nationManager.getNationById(member.get().getNationId());
                            String msg = "§6🤝 ALLIANCE FORMED between §e" +
                                own.map(Nation::getName).orElse("?") + " §6and §e" + proposer.get().getName() + "§6!";
                            ctx.getSource().getServer().getPlayerManager().broadcast(Text.literal(msg), false);

                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /alliance deny <nation>
            .then(CommandManager.literal("deny")
                .then(CommandManager.argument("nation", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String proposerName = StringArgumentType.getString(ctx, "nation");
                        try {
                            Optional<NationMember> member = nationManager.getMember(player.getUuid());
                            if (member.isEmpty() || !member.get().isSeniorLeadership()) {
                                player.sendMessage(Text.literal("§cOnly the President or Vice President can deny alliances."));
                                return 0;
                            }
                            Optional<Nation> proposer = nationManager.getNationByName(proposerName);
                            if (proposer.isEmpty()) {
                                player.sendMessage(Text.literal("§cNation not found: " + proposerName));
                                return 0;
                            }
                            allianceManager.denyAlliance(member.get().getNationId(), proposer.get().getId());
                            player.sendMessage(Text.literal("§cAlliance proposal from §e" + proposerName + " §cdenied."));
                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /alliance break <nation>
            .then(CommandManager.literal("break")
                .then(CommandManager.argument("nation", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String targetName = StringArgumentType.getString(ctx, "nation");
                        try {
                            Optional<NationMember> member = nationManager.getMember(player.getUuid());
                            if (member.isEmpty() || !member.get().isPresident()) {
                                player.sendMessage(Text.literal("§cOnly the President can dissolve an alliance."));
                                return 0;
                            }
                            Optional<Nation> target = nationManager.getNationByName(targetName);
                            if (target.isEmpty()) {
                                player.sendMessage(Text.literal("§cNation not found: " + targetName));
                                return 0;
                            }

                            Optional<Nation> own = nationManager.getNationById(member.get().getNationId());
                            allianceManager.breakAlliance(member.get().getNationId(), target.get().getId());

                            ctx.getSource().getServer().getPlayerManager().broadcast(
                                Text.literal("§c💔 Alliance DISSOLVED between §e" +
                                    own.map(Nation::getName).orElse("?") + " §cand §e" + target.get().getName() + "§c."),
                                false
                            );
                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /alliance list
            .then(CommandManager.literal("list").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                try {
                    Optional<NationMember> member = nationManager.getMember(player.getUuid());
                    if (member.isEmpty() || member.get().getNationId() == null) {
                        player.sendMessage(Text.literal("§cYou are not in a nation."));
                        return 0;
                    }
                    List<String> allyIds = allianceManager.getAlliedNationIds(member.get().getNationId());
                    if (allyIds.isEmpty()) {
                        player.sendMessage(Text.literal("§7Your nation has no active alliances."));
                    } else {
                        StringBuilder sb = new StringBuilder("§6=== Alliances ===\n");
                        for (String id : allyIds) {
                            nationManager.getNationById(id).ifPresent(n ->
                                sb.append("§e").append(n.getName()).append("\n"));
                        }
                        player.sendMessage(Text.literal(sb.toString()));
                    }
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()));
                }
                return 1;
            }))
        );
    }
}
