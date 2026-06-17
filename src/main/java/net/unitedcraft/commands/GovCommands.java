package net.unitedcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.managers.NationManager;
import net.unitedcraft.managers.VoteManager;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;
import net.unitedcraft.repository.NationRepository;

import java.util.*;

public class GovCommands {

    private static final NationManager nationManager = new NationManager();
    private static final VoteManager voteManager = new VoteManager();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(CommandManager.literal("gov")

            // /gov election start <role>
            .then(CommandManager.literal("election")
                .then(CommandManager.literal("start")
                    .then(CommandManager.argument("role", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            String roleStr = StringArgumentType.getString(ctx, "role").toUpperCase();
                            try {
                                Optional<NationMember> member = nationManager.getMember(player.getUuid());
                                if (member.isEmpty() || !member.get().isPresident()) {
                                    player.sendMessage(Text.literal("§cOnly the President can start elections."));
                                    return 0;
                                }
                                NationMember.Role role = NationMember.Role.valueOf(roleStr);
                                // Can't hold elections for President, Civilian, or Exile
                                if (role == NationMember.Role.PRESIDENT ||
                                    role == NationMember.Role.CIVILIAN ||
                                    role == NationMember.Role.EXILE) {
                                    player.sendMessage(Text.literal(
                                        "§cElections can only be held for: VICE_PRESIDENT, GOVERNOR, MAYOR, KNIGHT, CITIZEN"));
                                    return 0;
                                }
                                voteManager.startElection(member.get().getNationId(), role);
                                player.sendMessage(Text.literal(
                                    "§aElection for §e" + role.name() + " §ahas started! " +
                                    "Citizens vote with §e/gov vote " + role.name() + " <player>§a. " +
                                    "Ends in 24 hours."));
                            } catch (IllegalArgumentException e) {
                                player.sendMessage(Text.literal(
                                    "§cInvalid role. Options: VICE_PRESIDENT, GOVERNOR, MAYOR, KNIGHT, CITIZEN"));
                            } catch (Exception e) {
                                player.sendMessage(Text.literal("§c" + e.getMessage()));
                            }
                            return 1;
                        }))))

            // /gov vote <role> <candidate>
            .then(CommandManager.literal("vote")
                .then(CommandManager.argument("role", StringArgumentType.word())
                    .then(CommandManager.argument("candidate", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            String roleStr = StringArgumentType.getString(ctx, "role").toUpperCase();
                            String candidateName = StringArgumentType.getString(ctx, "candidate");
                            try {
                                Optional<NationMember> voterMember = nationManager.getMember(player.getUuid());
                                if (voterMember.isEmpty() || voterMember.get().getNationId() == null) {
                                    player.sendMessage(Text.literal("§cYou are not in a nation."));
                                    return 0;
                                }
                                if (voterMember.get().getRole() == NationMember.Role.CIVILIAN ||
                                    voterMember.get().getRole() == NationMember.Role.EXILE) {
                                    player.sendMessage(Text.literal("§cCivilians and Exiles cannot vote."));
                                    return 0;
                                }

                                NationMember.Role role = NationMember.Role.valueOf(roleStr);
                                String nationId = voterMember.get().getNationId();

                                var candidatePlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(candidateName);
                                if (candidatePlayer == null) {
                                    player.sendMessage(Text.literal("§cCandidate must be online to vote for them."));
                                    return 0;
                                }
                                Optional<NationMember> candidateMember = nationManager.getMember(candidatePlayer.getUuid());
                                if (candidateMember.isEmpty() || !nationId.equals(candidateMember.get().getNationId())) {
                                    player.sendMessage(Text.literal("§cThat player is not in your nation."));
                                    return 0;
                                }

                                voteManager.castVote(nationId, role, player.getUuid(), candidatePlayer.getUuid());
                                player.sendMessage(Text.literal(
                                    "§aVote cast for §e" + candidateName +
                                    " §aas §e" + role.name() + "§a! (anonymous)"));

                            } catch (IllegalArgumentException e) {
                                player.sendMessage(Text.literal("§cInvalid role."));
                            } catch (Exception e) {
                                player.sendMessage(Text.literal("§c" + e.getMessage()));
                            }
                            return 1;
                        }))))

            // /gov results <role>
            .then(CommandManager.literal("results")
                .then(CommandManager.argument("role", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String roleStr = StringArgumentType.getString(ctx, "role").toUpperCase();
                        try {
                            Optional<NationMember> member = nationManager.getMember(player.getUuid());
                            if (member.isEmpty() || member.get().getNationId() == null) {
                                player.sendMessage(Text.literal("§cYou are not in a nation."));
                                return 0;
                            }

                            NationMember.Role role = NationMember.Role.valueOf(roleStr);
                            String nationId = member.get().getNationId();
                            Map<UUID, Integer> votes = voteManager.getVoteCounts(nationId, role);

                            if (votes.isEmpty()) {
                                player.sendMessage(Text.literal("§7No votes cast yet for §e" + role.name() + "§7."));
                                return 1;
                            }

                            StringBuilder sb = new StringBuilder("§6=== Election Results: " + role.name() + " ===\n");
                            votes.entrySet().stream()
                                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                                .forEach(e -> {
                                    Optional<NationMember> m = nationManager.getMember(e.getKey());
                                    String name = m.map(NationMember::getPlayerName).orElse(e.getKey().toString());
                                    sb.append("§e").append(name)
                                      .append(" §7— §f").append(e.getValue()).append(" votes\n");
                                });

                            player.sendMessage(Text.literal(sb.toString()));

                        } catch (IllegalArgumentException e) {
                            player.sendMessage(Text.literal("§cInvalid role."));
                        }
                        return 1;
                    })))

            // /gov close <role> — close election and appoint winner
            .then(CommandManager.literal("close")
                .then(CommandManager.argument("role", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String roleStr = StringArgumentType.getString(ctx, "role").toUpperCase();
                        try {
                            Optional<NationMember> member = nationManager.getMember(player.getUuid());
                            if (member.isEmpty() || !member.get().isPresident()) {
                                player.sendMessage(Text.literal("§cOnly the President can close elections."));
                                return 0;
                            }
                            NationMember.Role role = NationMember.Role.valueOf(roleStr);
                            String nationId = member.get().getNationId();

                            Optional<UUID> winner = voteManager.getElectionWinner(nationId, role);
                            if (winner.isEmpty()) {
                                player.sendMessage(Text.literal("§cNo votes found for this election."));
                                return 0;
                            }

                            nationManager.setRole(nationId, winner.get(), role);
                            voteManager.closeElection(nationId, role);

                            Optional<NationMember> winnerMember = nationManager.getMember(winner.get());
                            String winnerName = winnerMember.map(NationMember::getPlayerName).orElse("Unknown");

                            ctx.getSource().getServer().getPlayerManager().broadcast(
                                Text.literal("§6🏛 ELECTION RESULT: §e" + winnerName +
                                    " §6has been elected §e" + role.name() +
                                    " §6of §e" +
                                    nationManager.getNationById(nationId).map(Nation::getName).orElse("their nation") + "§6!"),
                                false
                            );

                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /gov rules
            .then(CommandManager.literal("rules").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                Optional<NationMember> member = nationManager.getMember(player.getUuid());
                if (member.isEmpty() || member.get().getNationId() == null) {
                    player.sendMessage(Text.literal("§cYou are not in a nation."));
                    return 0;
                }
                Optional<Nation> nation = nationManager.getNationById(member.get().getNationId());
                nation.ifPresent(n -> player.sendMessage(Text.literal(
                    "§6=== " + n.getName() + " Rules ===\n" +
                    "§ePvP in Territory: §f" + (n.isPvpEnabled() ? "§aEnabled" : "§cDisabled") + "\n" +
                    "§eVisitor Building: §f" + (n.isVisitorBuild() ? "§aAllowed" : "§cDenied") + "\n" +
                    "§eRecruitment: §f" + (n.isOpenRecruitment() ? "§aOpen" : "§6Invite Only") + "\n" +
                    "§eTax Rate: §f" + (int)(n.getTaxRate() * 100) + "%\n" +
                    "§7Change with §e/gov set <rule> <value> §7(leadership only)"
                )));
                return 1;
            }))

            // /gov set <rule> <value>
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("rule", StringArgumentType.word())
                    .then(CommandManager.argument("value", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            String rule = StringArgumentType.getString(ctx, "rule").toLowerCase();
                            String value = StringArgumentType.getString(ctx, "value").toLowerCase();
                            try {
                                Optional<NationMember> member = nationManager.getMember(player.getUuid());
                                if (member.isEmpty() || !member.get().isLeadership()) {
                                    player.sendMessage(Text.literal("§cOnly leadership can change nation rules."));
                                    return 0;
                                }
                                Nation nation = nationManager.getNationById(member.get().getNationId())
                                    .orElseThrow(() -> new Exception("Nation not found."));

                                switch (rule) {
                                    case "pvp" -> {
                                        nation.setPvpEnabled(value.equals("true") || value.equals("on"));
                                        player.sendMessage(Text.literal("§aPvP set to §e" + nation.isPvpEnabled()));
                                    }
                                    case "visitors" -> {
                                        nation.setVisitorBuild(value.equals("true") || value.equals("on"));
                                        player.sendMessage(Text.literal("§aVisitor building set to §e" + nation.isVisitorBuild()));
                                    }
                                    case "recruitment" -> {
                                        nation.setOpenRecruitment(value.equals("open") || value.equals("true"));
                                        player.sendMessage(Text.literal("§aRecruitment: §e" + (nation.isOpenRecruitment() ? "Open" : "Invite Only")));
                                    }
                                    case "tax" -> {
                                        double rate = Double.parseDouble(value) / 100.0;
                                        if (rate < 0 || rate > 0.5) {
                                            player.sendMessage(Text.literal("§cTax must be between 0–50%."));
                                            return 0;
                                        }
                                        nation.setTaxRate(rate);
                                        player.sendMessage(Text.literal("§aTax rate set to §e" + (int)(rate * 100) + "%"));
                                    }
                                    default -> {
                                        player.sendMessage(Text.literal("§cUnknown rule. Options: pvp, visitors, recruitment, tax"));
                                        return 0;
                                    }
                                }
                                nationManager.getRepo().saveNation(nation);
                            } catch (NumberFormatException e) {
                                player.sendMessage(Text.literal("§cInvalid value for that rule."));
                            } catch (Exception e) {
                                player.sendMessage(Text.literal("§c" + e.getMessage()));
                            }
                            return 1;
                        }))))

            // /gov plotlimits — show rank plot limits
            .then(CommandManager.literal("plotlimits").executes(ctx -> {
                ctx.getSource().sendFeedback(() -> Text.literal(
                    "§6=== Personal Plot Limits by Rank ===\n" +
                    "§6President: §fUnlimited\n" +
                    "§6Vice President: §f16 plots\n" +
                    "§eGovernor: §f12 plots\n" +
                    "§eMayor: §f8 plots\n" +
                    "§bKnight: §f4 plots\n" +
                    "§aCitizen: §f2 plots\n" +
                    "§7Civilian: §f1 plot\n" +
                    "§cExile: §f0 plots\n" +
                    "§7Claim with §e/land plot claim §7(must be inside your nation's territory)"
                ), false);
                return 1;
            }))
        );
    }
}
