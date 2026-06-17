package net.unitedcraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.managers.NationManager;
import net.unitedcraft.managers.TreatyManager;
import net.unitedcraft.managers.WarManager;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;
import net.unitedcraft.models.Treaty;

import java.util.Optional;

public class WarCommands {

    private static final WarManager warManager = new WarManager();
    private static final NationManager nationManager = new NationManager();
    private static final TreatyManager treatyManager = new TreatyManager();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(CommandManager.literal("war")

            // /war declare <nation>
            .then(CommandManager.literal("declare")
                .then(CommandManager.argument("nation", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        String targetName = StringArgumentType.getString(ctx, "nation");
                        try {
                            Optional<NationMember> member = nationManager.getMember(player.getUuid());
                            if (member.isEmpty() || member.get().getNationId() == null) {
                                player.sendMessage(Text.literal("§cYou are not in a nation."));
                                return 0;
                            }
                            if (!member.get().isPresident()) {
                                player.sendMessage(Text.literal("§cOnly the President can declare war."));
                                return 0;
                            }
                            Optional<Nation> target = nationManager.getNationByName(targetName);
                            if (target.isEmpty()) {
                                player.sendMessage(Text.literal("§cNation not found: " + targetName));
                                return 0;
                            }

                            // Cannot declare war on an ally
                            if (UnitedCraft.allianceManager.areAllied(member.get().getNationId(), target.get().getId())) {
                                player.sendMessage(Text.literal("§cYou cannot declare war on an allied nation. Break the alliance first with §e/alliance break§c."));
                                return 0;
                            }

                            String attackerId = member.get().getNationId();
                            String defenderId = target.get().getId();
                            warManager.declareWar(attackerId, defenderId);

                            // Increase defender discontentment slightly (being attacked)
                            UnitedCraft.db.getConnection(); // ensure connection alive

                            Optional<Nation> attacker = nationManager.getNationById(attackerId);
                            String attackerName = attacker.map(Nation::getName).orElse("Unknown");

                            ctx.getSource().getServer().getPlayerManager().broadcast(
                                Text.literal("§c⚔ WAR DECLARED! §e" + attackerName +
                                    " §chas declared war on §e" + target.get().getName() + "§c!"),
                                false
                            );

                            // Notify allies
                            UnitedCraft.allianceManager.broadcastToAllies(
                                ctx.getSource().getServer(), attackerId,
                                "§c⚔ Your ally §e" + attackerName + " §chas declared war on §e" + target.get().getName() + "§c!"
                            );
                            UnitedCraft.allianceManager.broadcastToAllies(
                                ctx.getSource().getServer(), defenderId,
                                "§c⚔ Your ally §e" + target.get().getName() + " §cis under attack by §e" + attackerName + "§c!"
                            );

                        } catch (Exception e) {
                            player.sendMessage(Text.literal("§c" + e.getMessage()));
                        }
                        return 1;
                    })))

            // /war status
            .then(CommandManager.literal("status").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;
                Optional<NationMember> member = nationManager.getMember(player.getUuid());
                if (member.isEmpty() || member.get().getNationId() == null) {
                    player.sendMessage(Text.literal("§cYou are not in a nation."));
                    return 0;
                }
                String nationId = member.get().getNationId();
                boolean atWar = warManager.isAtWar(nationId);
                if (atWar) {
                    player.sendMessage(Text.literal("§c⚔ Your nation is currently AT WAR."));
                } else {
                    player.sendMessage(Text.literal("§a☮ Your nation is at peace."));
                }
                return 1;
            }))

            // /war peace propose <nation> <terms>
            .then(CommandManager.literal("peace")
                .then(CommandManager.literal("propose")
                    .then(CommandManager.argument("nation", StringArgumentType.word())
                        .then(CommandManager.argument("terms", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) return 0;
                                String targetName = StringArgumentType.getString(ctx, "nation");
                                String termsStr = StringArgumentType.getString(ctx, "terms").toUpperCase();
                                try {
                                    Optional<NationMember> member = nationManager.getMember(player.getUuid());
                                    if (member.isEmpty() || !member.get().isSeniorLeadership()) {
                                        player.sendMessage(Text.literal("§cOnly the President or Vice President can propose peace."));
                                        return 0;
                                    }
                                    Treaty.Terms terms = Treaty.Terms.valueOf(termsStr);
                                    Optional<Nation> target = nationManager.getNationByName(targetName);
                                    if (target.isEmpty()) {
                                        player.sendMessage(Text.literal("§cNation not found."));
                                        return 0;
                                    }

                                    treatyManager.proposeTreaty(member.get().getNationId(), target.get().getId(), terms);

                                    // Notify target leaders
                                    Optional<Nation> own = nationManager.getNationById(member.get().getNationId());
                                    nationManager.getMembers(target.get().getId()).stream()
                                        .filter(NationMember::isSeniorLeadership)
                                        .forEach(m -> {
                                            var p = ctx.getSource().getServer().getPlayerManager().getPlayer(m.getPlayerUuid());
                                            if (p != null) p.sendMessage(Text.literal(
                                                "§a☮ Peace proposal from §e" + own.map(Nation::getName).orElse("?") +
                                                "§a! Terms: §e" + terms + "§a. Use §e/war peace accept §ato accept or §e/war peace reject §ato decline."
                                            ));
                                        });

                                    player.sendMessage(Text.literal("§aPeace proposal sent to §e" + target.get().getName() +
                                        "§a with terms: §e" + terms));
                                } catch (IllegalArgumentException e) {
                                    player.sendMessage(Text.literal("§cInvalid terms. Options: WHITE_PEACE, CEDE_TERRITORY, PAY_REPARATIONS"));
                                } catch (Exception e) {
                                    player.sendMessage(Text.literal("§c" + e.getMessage()));
                                }
                                return 1;
                            }))))

                // /war peace accept
                .then(CommandManager.literal("accept").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    try {
                        Optional<NationMember> member = nationManager.getMember(player.getUuid());
                        if (member.isEmpty() || !member.get().isSeniorLeadership()) {
                            player.sendMessage(Text.literal("§cOnly the President or Vice President can accept peace."));
                            return 0;
                        }
                        treatyManager.acceptTreaty(member.get().getNationId(), ctx.getSource().getServer());
                    } catch (Exception e) {
                        player.sendMessage(Text.literal("§c" + e.getMessage()));
                    }
                    return 1;
                }))

                // /war peace reject
                .then(CommandManager.literal("reject").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    try {
                        Optional<NationMember> member = nationManager.getMember(player.getUuid());
                        if (member.isEmpty() || !member.get().isSeniorLeadership()) {
                            player.sendMessage(Text.literal("§cOnly the President or Vice President can reject peace."));
                            return 0;
                        }
                        treatyManager.rejectTreaty(member.get().getNationId());
                        player.sendMessage(Text.literal("§cPeace proposal rejected. The war continues."));
                    } catch (Exception e) {
                        player.sendMessage(Text.literal("§c" + e.getMessage()));
                    }
                    return 1;
                }))
            )
        );
    }
}
