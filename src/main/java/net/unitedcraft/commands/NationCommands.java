package net.unitedcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.managers.NationManager;
import net.unitedcraft.managers.RebellionManager;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;

import java.util.List;
import java.util.Optional;

public class NationCommands {

    private static final NationManager nationManager = new NationManager();
    private static final RebellionManager rebellionManager = new RebellionManager();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(CommandManager.literal("nation")

            // /nation help
            .then(CommandManager.literal("help").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                player.sendMessage(Text.literal(
                    "§6=== UnitedCraft Nation Commands ===\n" +
                    "§e/nation create <name> <gov> §7- Found a new nation (MONARCHY/DEMOCRACY/REPUBLIC)\n" +
                    "§e/nation info [name] §7- View nation info\n" +
                    "§e/nation list §7- List all nations\n" +
                    "§e/nation join <name> §7- Join a nation\n" +
                    "§e/nation leave §7- Leave your nation\n" +
                    "§e/nation invite <player> §7- Invite a player (leadership)\n" +
                    "§e/nation kick <player> §7- Kick a member (leadership)\n" +
                    "§e/nation setrole <player> <role> §7- Set role (President only)\n" +
                    "§e/nation transfer <player> §7- Transfer presidency\n" +
                    "§e/nation disband §7- Disband nation (President only)\n" +
                    "§e/nation coup §7- Attempt coup during rebellion\n" +
                    "§6Roles: §7VICE_PRESIDENT, GOVERNOR, MAYOR, KNIGHT, CITIZEN, CIVILIAN, EXILE\n" +
                    "§6Other: §e/land §7• §e/war §7• §e/gov §7• §e/alliance §7• §e/nc §7• §e/ac"
                ));
                return 1;
            }))

            // /nation create <name> <gov_type>
            .then(CommandManager.literal("create")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .then(CommandManager.argument("gov_type", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            String name = StringArgumentType.getString(ctx, "name");
                            String govStr = StringArgumentType.getString(ctx, "gov_type").toUpperCase();
                            try {
                                Nation.GovernmentType govType = Nation.GovernmentType.valueOf(govStr);
                                Nation nation = nationManager.createNation(
                                    name, player.getUuid(), player.getName().getString(), govType);
                                player.sendMessage(Text.literal(
                                    "§aYou have founded §e" + nation.getName() +
                                    " §aas a §e" + govType.name() + "§a! " +
                                    "You are now President. Use §e/nation help §ato get started."
                                ));
                                // Broadcast to server
                                ctx.getSource().getServer().getPlayerManager().broadcast(
                                    Text.literal("§6🏛 A new nation has risen: §e" + nation.getName() +
                                        " §6founded by §e" + player.getName().getString() + "§6!"),
                                    false
                                );
                            } catch (IllegalArgumentException e) {
                                player.sendMessage(Text.literal("§cInvalid government type. Choose: MONARCHY, DEMOCRACY, REPUBLIC"));
                            } catch (Exception e) {
                                player.sendMessage(Text.literal("§c" + e.getMessage()));
                            }
                            return 1;
                        }))))

            // /nation info [name]
            .then(CommandManager.literal("info")
                .executes(ctx -> showInfo(ctx.getSource(), null))
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> showInfo(ctx.getSource(),
                        StringArgumentType.getString(ctx, "name")))))

            // /nation list
            .then(CommandManager.literal("list").executes(ctx -> {
                List<Nation> nations = nationManager.getAllNations();
                if (nations.isEmpty()) {
                    ctx.getSource().sendFeedback(() ->
                        Text.literal("§7No nations exist yet. Be the first! §e/nation create <name> REPUBLIC"), false);
                } else {
                    StringBuilder sb = new StringBuilder("§6=== Nations (" + nations.size() + ") ===\n");
                    for (Nation n : nations) {
                        int memberCount = nationManager.getMembers(n.getId()).size();
                        sb.append("§e").append(n.getName())
                          .append(" §7[").append(n.getGovernmentType()).append("]")
                          .append(" §fMembers: ").append(memberCount).append("\n");
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                }
                return 1;
            }))

            // /nation join <name>
            .then(CommandManager.literal("join")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String name = StringArgumentType.getString(ctx, "name");
                        try {
                            Optional<Nation> nation = nationManager.getNationByName(name);
                            if (nation.isEmpty()) {
                                player.sendMessage(Text.literal("§cNation not found: " + name));
                                return 0;
                            }
                            nationManager.joinNation(player.getUuid(), player.getName().getString(), nation.get().getId());
                            player.sendMessage(Text.literal(
                                "§aWelcome to §e" + nation.get().getName() +
                                "§a! You joined as a §7Civilian§a. " +
                                "Leadership can promote you with §e/nation setrole§a."));
                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /nation leave
            .then(CommandManager.literal("leave").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                try {
                    nationManager.leaveNation(player.getUuid());
                    player.sendMessage(Text.literal("§aYou have left your nation."));
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()));
                }
                return 1;
            }))

            // /nation invite <player>
            .then(CommandManager.literal("invite")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity actor = ctx.getSource().getPlayer();
                        if (actor == null) return 0;
                        String targetName = StringArgumentType.getString(ctx, "player");
                        try {
                            Optional<NationMember> actorMember = nationManager.getMember(actor.getUuid());
                            if (actorMember.isEmpty() || !actorMember.get().isLeadership()) {
                                actor.sendMessage(Text.literal("§cOnly leadership can send invites."));
                                return 0;
                            }
                            Optional<Nation> nation = nationManager.getNationById(actorMember.get().getNationId());
                            var targetPlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                            if (targetPlayer == null) {
                                actor.sendMessage(Text.literal("§cPlayer not found or not online."));
                                return 0;
                            }
                            targetPlayer.sendMessage(Text.literal(
                                "§6📜 You have been invited to join §e" +
                                nation.map(Nation::getName).orElse("a nation") +
                                "§6 by §e" + actor.getName().getString() +
                                "§6! Type §e/nation join " + nation.map(Nation::getName).orElse("?") +
                                " §6to accept."
                            ));
                            actor.sendMessage(Text.literal("§aInvite sent to §e" + targetName + "§a."));
                        } catch (Exception e) {
                            actor.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /nation kick <player>
            .then(CommandManager.literal("kick")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity actor = ctx.getSource().getPlayer();
                        if (actor == null) return 0;
                        String targetName = StringArgumentType.getString(ctx, "player");
                        try {
                            Optional<NationMember> actorMember = nationManager.getMember(actor.getUuid());
                            if (actorMember.isEmpty() || !actorMember.get().isLeadership()) {
                                actor.sendMessage(Text.literal("§cOnly leadership can kick members."));
                                return 0;
                            }
                            var targetPlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                            if (targetPlayer == null) {
                                actor.sendMessage(Text.literal("§cPlayer not found or not online."));
                                return 0;
                            }
                            Optional<NationMember> targetMember = nationManager.getMember(targetPlayer.getUuid());
                            if (targetMember.isEmpty() || !actorMember.get().getNationId().equals(targetMember.get().getNationId())) {
                                actor.sendMessage(Text.literal("§cThat player is not in your nation."));
                                return 0;
                            }
                            if (targetMember.get().isPresident()) {
                                actor.sendMessage(Text.literal("§cYou cannot kick the President."));
                                return 0;
                            }
                            // Cannot kick someone of equal or higher rank (unless President doing the kicking)
                            if (!actorMember.get().isPresident() &&
                                targetMember.get().getRole().ordinal() <= actorMember.get().getRole().ordinal()) {
                                actor.sendMessage(Text.literal("§cYou cannot kick someone of equal or higher rank."));
                                return 0;
                            }
                            targetMember.get().setNationId(null);
                            targetMember.get().setRole(NationMember.Role.CIVILIAN);
                            nationManager.getRepo().saveMember(targetMember.get());
                            actor.sendMessage(Text.literal("§a" + targetName + " has been kicked from the nation."));
                            targetPlayer.sendMessage(Text.literal("§cYou have been kicked from your nation."));
                        } catch (Exception e) {
                            actor.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /nation setrole <player> <role>
            .then(CommandManager.literal("setrole")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .then(CommandManager.argument("role", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity actor = ctx.getSource().getPlayer();
                            if (actor == null) return 0;
                            String targetName = StringArgumentType.getString(ctx, "player");
                            String roleStr = StringArgumentType.getString(ctx, "role").toUpperCase();
                            try {
                                Optional<NationMember> actorMember = nationManager.getMember(actor.getUuid());
                                if (actorMember.isEmpty() || !actorMember.get().isPresident()) {
                                    actor.sendMessage(Text.literal("§cOnly the President can set roles."));
                                    return 0;
                                }
                                NationMember.Role newRole = NationMember.Role.valueOf(roleStr);
                                var targetPlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                if (targetPlayer == null) {
                                    actor.sendMessage(Text.literal("§cPlayer not online."));
                                    return 0;
                                }
                                nationManager.setRole(actorMember.get().getNationId(), targetPlayer.getUuid(), newRole);
                                actor.sendMessage(Text.literal("§a" + targetName + " is now a §e" + newRole.name() + "§a."));
                                targetPlayer.sendMessage(Text.literal("§aYour role has been changed to §e" + newRole.name() + "§a."));
                            } catch (IllegalArgumentException e) {
                                actor.sendMessage(Text.literal(
                                    "§cInvalid role. Options: VICE_PRESIDENT, GOVERNOR, MAYOR, KNIGHT, CITIZEN, CIVILIAN, EXILE"));
                            } catch (Exception e) {
                                actor.sendMessage(Text.literal("§c" + e.getMessage()));
                            }
                            return 1;
                        }))))

            // /nation transfer <player>
            .then(CommandManager.literal("transfer")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity actor = ctx.getSource().getPlayer();
                        if (actor == null) return 0;
                        String targetName = StringArgumentType.getString(ctx, "player");
                        try {
                            Optional<NationMember> actorMember = nationManager.getMember(actor.getUuid());
                            if (actorMember.isEmpty()) {
                                actor.sendMessage(Text.literal("§cYou are not in a nation."));
                                return 0;
                            }
                            var targetPlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                            if (targetPlayer == null) {
                                actor.sendMessage(Text.literal("§cPlayer not online."));
                                return 0;
                            }
                            nationManager.transferLeadership(
                                actorMember.get().getNationId(), actor.getUuid(), targetPlayer.getUuid());
                            actor.sendMessage(Text.literal("§aYou have transferred the Presidency to §e" + targetName + "§a."));
                            targetPlayer.sendMessage(Text.literal("§6👑 You are now the President of your nation!"));
                        } catch (Exception e) {
                            actor.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /nation disband
            .then(CommandManager.literal("disband").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                try {
                    Optional<NationMember> member = nationManager.getMember(player.getUuid());
                    if (member.isEmpty() || !member.get().isPresident()) {
                        player.sendMessage(Text.literal("§cOnly the President can disband the nation."));
                        return 0;
                    }
                    String nationId = member.get().getNationId();
                    Optional<Nation> nation = nationManager.getNationById(nationId);
                    nationManager.disbandNation(nationId);
                    String name = nation.map(Nation::getName).orElse("your nation");
                    player.sendMessage(Text.literal("§cYou have disbanded §e" + name + "§c."));
                    ctx.getSource().getServer().getPlayerManager().broadcast(
                        Text.literal("§7The nation of §e" + name + " §7has been disbanded."), false);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()));
                }
                return 1;
            }))

            // /nation coup
            .then(CommandManager.literal("coup").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                try {
                    rebellionManager.attemptCoup(player.getUuid(), ctx.getSource().getServer());
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()));
                }
                return 1;
            }))
        );
    }

    private static int showInfo(ServerCommandSource source, String nationName) {
        Nation nation;

        if (nationName == null) {
            ServerPlayerEntity player = source.getPlayer();
            if (player == null) return 0;
            Optional<NationMember> member = nationManager.getMember(player.getUuid());
            if (member.isEmpty() || member.get().getNationId() == null) {
                source.sendFeedback(() -> Text.literal("§cYou are not in a nation. Use §e/nation list §cto see nations."), false);
                return 0;
            }
            nation = nationManager.getNationById(member.get().getNationId()).orElse(null);
        } else {
            nation = nationManager.getNationByName(nationName).orElse(null);
        }

        if (nation == null) {
            source.sendFeedback(() -> Text.literal("§cNation not found."), false);
            return 0;
        }

        final Nation n = nation;
        List<NationMember> members = nationManager.getMembers(n.getId());
        int claims = nationManager.getRepo().getClaimCount(n.getId());

        // Find president name
        String presidentName = members.stream()
            .filter(NationMember::isPresident)
            .map(NationMember::getPlayerName)
            .findFirst().orElse("None");

        source.sendFeedback(() -> Text.literal(
            "§6========== " + n.getName() + " ==========\n" +
            "§eGovernment: §f" + n.getGovernmentType() + "\n" +
            "§ePresident: §f" + presidentName + "\n" +
            "§eMembers: §f" + members.size() + "\n" +
            "§eClaims: §f" + claims + " / " + nationManager.getClaimLimit(n.getId()) + "\n" +
            "§eTreasury: §f$" + String.format("%.2f", n.getBalance()) + "\n" +
            "§eTax Rate: §f" + (int)(n.getTaxRate() * 100) + "%\n" +
            "§ePvP in Territory: §f" + (n.isPvpEnabled() ? "§aEnabled" : "§cDisabled") + "\n" +
            "§eOpen Recruitment: §f" + (n.isOpenRecruitment() ? "§aYes" : "§6Invite Only") + "\n" +
            "§eDiscontentment: §f" + n.getDiscontentment() + "%" +
            (n.getDiscontentment() >= RebellionManager.REBELLION_THRESHOLD ? " §c⚠ REBELLION RISK!" : "")
        ), false);
        return 1;
    }
}
