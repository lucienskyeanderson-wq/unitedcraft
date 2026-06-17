package net.unitedcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.managers.LandManager;
import net.unitedcraft.managers.NationManager;
import net.unitedcraft.models.*;

import java.util.Optional;

public class LandCommands {

    private static final LandManager landManager = new LandManager();
    private static final NationManager nationManager = new NationManager();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        // ── /land ─────────────────────────────────────────────
        dispatcher.register(CommandManager.literal("land")

            // /land claim [type]
            .then(CommandManager.literal("claim")
                .executes(ctx -> claim(ctx.getSource(), LandClaim.ClaimType.STANDARD))
                .then(CommandManager.argument("type", StringArgumentType.word())
                    .executes(ctx -> {
                        String typeStr = StringArgumentType.getString(ctx, "type").toUpperCase();
                        try {
                            return claim(ctx.getSource(), LandClaim.ClaimType.valueOf(typeStr));
                        } catch (IllegalArgumentException e) {
                            ctx.getSource().sendFeedback(() ->
                                Text.literal("§cInvalid type. Options: STANDARD, CAPITAL, BORDER, OUTPOST"), false);
                            return 0;
                        }
                    })))

            // /land unclaim
            .then(CommandManager.literal("unclaim").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                try {
                    landManager.unclaimChunk(player);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()));
                }
                return 1;
            }))

            // /land overclaim — take an insolvent enemy chunk
            .then(CommandManager.literal("overclaim").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                try {
                    landManager.overclaim(player);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()));
                }
                return 1;
            }))

            // /land info
            .then(CommandManager.literal("info").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;

                String chunkKey = landManager.getChunkKey(player);
                Optional<LandClaim> claim = landManager.getClaimAtKey(chunkKey);
                Optional<PersonalPlot> plot = landManager.getPersonalPlot(chunkKey);

                if (claim.isEmpty()) {
                    player.sendMessage(Text.literal("§7This chunk is §fWilderness §7(unclaimed). Anyone can build here."));
                    return 1;
                }

                LandClaim c = claim.get();
                Optional<Nation> nation = nationManager.getNationById(c.getNationId());
                int claims = 0;
                boolean insolvent = false;
                if (nation.isPresent()) {
                    claims = nationManager.getRepo().getClaimCount(nation.get().getId());
                    insolvent = nation.get().isInsolvent(claims);
                }

                StringBuilder sb = new StringBuilder();
                sb.append("§6=== Chunk Info ===\n");
                sb.append("§eNation: §f").append(nation.map(Nation::getName).orElse("Unknown")).append("\n");
                sb.append("§eType: §f").append(c.getClaimType()).append("\n");
                sb.append("§eOverclaimable: §f").append(c.isOverclaimable() ? "§aYes" : "§cNo (Capital)").append("\n");

                if (insolvent) {
                    sb.append("§c⚠ This nation is INSOLVENT — their land can be overclaimed!\n");
                }

                if (plot.isPresent()) {
                    sb.append("§ePersonal Plot Owner: §f").append(plot.get().getOwnerName()).append("\n");
                }

                boolean canBuild = landManager.canBuild(player, chunkKey);
                sb.append("§eYou can build here: §f").append(canBuild ? "§aYes" : "§cNo");

                player.sendMessage(Text.literal(sb.toString()));
                return 1;
            }))

            // /land map
            .then(CommandManager.literal("map").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;

                int cx = player.getBlockPos().getX() >> 4;
                int cz = player.getBlockPos().getZ() >> 4;
                String world = player.getWorld().getRegistryKey().getValue().toString();

                Optional<Nation> playerNation = nationManager.getNationOfPlayer(player.getUuid());
                String playerNationId = playerNation.map(Nation::getId).orElse(null);

                StringBuilder map = new StringBuilder("§6=== Land Map (5×5 chunks) ===\n");
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        String key = LandClaim.toChunkKey(world, cx + dx, cz + dz);
                        Optional<LandClaim> c = landManager.getClaimAtKey(key);
                        Optional<PersonalPlot> p = landManager.getPersonalPlot(key);

                        if (dx == 0 && dz == 0) {
                            map.append("§b[YOU]");
                        } else if (c.isEmpty()) {
                            map.append("§7[   ]");
                        } else if (p.isPresent() && p.get().getOwnerUuid().equals(player.getUuid())) {
                            map.append("§d[PLT]"); // your personal plot
                        } else if (c.get().getNationId().equals(playerNationId)) {
                            map.append("§a[NAT]");
                        } else {
                            map.append("§c[ENE]");
                        }
                    }
                    map.append("\n");
                }
                map.append("§7Legend: §b[YOU] §7You  §a[NAT] §7Nation  §d[PLT] §7Your Plot  §c[ENE] §7Enemy  §7[   ] Wilds");
                player.sendMessage(Text.literal(map.toString()));
                return 1;
            }))

            // /land plot claim — personal plot
            .then(CommandManager.literal("plot")
                .then(CommandManager.literal("claim").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    try {
                        landManager.claimPersonalPlot(player);
                    } catch (Exception e) {
                        player.sendMessage(Text.literal("§c" + e.getMessage()));
                    }
                    return 1;
                }))
                .then(CommandManager.literal("unclaim").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    try {
                        landManager.unclaimPersonalPlot(player);
                    } catch (Exception e) {
                        player.sendMessage(Text.literal("§c" + e.getMessage()));
                    }
                    return 1;
                }))
                .then(CommandManager.literal("info").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    Optional<NationMember> member = nationManager.getMember(player.getUuid());
                    if (member.isEmpty() || member.get().getNationId() == null) {
                        player.sendMessage(Text.literal("§cYou are not in a nation."));
                        return 0;
                    }
                    NationMember.Role role = member.get().getRole();
                    int used = landManager.countPersonalPlots(player.getUuid());
                    int limit = PersonalPlot.getPlotLimit(role);
                    player.sendMessage(Text.literal(String.format(
                        "§6=== Personal Plots ===\n" +
                        "§eRank: §f%s\n" +
                        "§ePlots used: §f%d §7/ §f%s\n" +
                        "§7Use §e/land plot claim §7standing in your nation's territory.\n" +
                        "§7Only you (and the President) can build in your plot.",
                        member.get().getRoleDisplay(), used,
                        PersonalPlot.getRoleLimitDisplay(role)
                    )));
                    return 1;
                }))
            )

            // /land perm grant <player> — President grants build perm on current chunk
            .then(CommandManager.literal("perm")
                .then(CommandManager.literal("grant")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity actor = ctx.getSource().getPlayer();
                            if (actor == null) return 0;
                            String targetName = StringArgumentType.getString(ctx, "player");
                            try {
                                var targetPlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                if (targetPlayer == null) {
                                    actor.sendMessage(Text.literal("§cPlayer not online."));
                                    return 0;
                                }
                                landManager.grantBuildPermission(actor, targetPlayer.getUuid(), targetName);
                            } catch (Exception e) {
                                actor.sendMessage(Text.literal("§c" + e.getMessage()));
                            }
                            return 1;
                        })))
                .then(CommandManager.literal("revoke")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity actor = ctx.getSource().getPlayer();
                            if (actor == null) return 0;
                            String targetName = StringArgumentType.getString(ctx, "player");
                            try {
                                var targetPlayer = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                                if (targetPlayer == null) {
                                    actor.sendMessage(Text.literal("§cPlayer not online."));
                                    return 0;
                                }
                                landManager.revokeBuildPermission(actor, targetPlayer.getUuid(), targetName);
                            } catch (Exception e) {
                                actor.sendMessage(Text.literal("§c" + e.getMessage()));
                            }
                            return 1;
                        })))
            )
        );

        // ── /nation deposit & withdraw (money commands) ───────
        dispatcher.register(CommandManager.literal("nation")
            .then(CommandManager.literal("deposit")
                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        double amount = DoubleArgumentType.getDouble(ctx, "amount");
                        try {
                            landManager.depositToNation(player, amount);
                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))
            .then(CommandManager.literal("withdraw")
                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        double amount = DoubleArgumentType.getDouble(ctx, "amount");
                        try {
                            landManager.withdrawFromNation(player, amount);
                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))
            .then(CommandManager.literal("balance").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                Optional<NationMember> member = nationManager.getMember(player.getUuid());
                if (member.isEmpty() || member.get().getNationId() == null) {
                    player.sendMessage(Text.literal("§cYou are not in a nation."));
                    return 0;
                }
                Optional<Nation> nation = nationManager.getNationById(member.get().getNationId());
                nation.ifPresent(n -> {
                    int claims = nationManager.getRepo().getClaimCount(n.getId());
                    double weeklyMaint = n.calculateWeeklyMaintenance(claims);
                    boolean insolvent = n.isInsolvent(claims);
                    player.sendMessage(Text.literal(String.format(
                        "§6=== %s Treasury ===\n" +
                        "§eBalance: §f$%.2f\n" +
                        "§eChunks Claimed: §f%d\n" +
                        "§eWeekly Maintenance: §f$%.2f\n" +
                        "§eNext Claim Cost: §f$%.2f\n" +
                        "§eStatus: %s",
                        n.getName(), n.getBalance(), claims, weeklyMaint,
                        Nation.CLAIM_COST + (claims * Nation.CHUNK_MAINTENANCE),
                        insolvent ? "§c⚠ INSOLVENT — land at risk of overclaim!" : "§aSolvent ✓"
                    )));
                });
                return 1;
            }))
            .then(CommandManager.literal("balance")
                .then(CommandManager.literal("player").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    double bal = landManager.getPlayerBalance(player.getUuid());
                    player.sendMessage(Text.literal(String.format(
                        "§6Your personal balance: §f$%.2f", bal
                    )));
                    return 1;
                })))
        );
    }

    private static int claim(ServerCommandSource source, LandClaim.ClaimType type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        try {
            landManager.claimChunk(player, type);
        } catch (Exception e) {
            player.sendMessage(Text.literal("§c" + e.getMessage()));
        }
        return 1;
    }
}
