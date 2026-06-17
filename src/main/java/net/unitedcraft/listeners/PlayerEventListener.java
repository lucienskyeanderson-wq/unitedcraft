package net.unitedcraft.listeners;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.managers.LandManager;
import net.unitedcraft.managers.NationManager;
import net.unitedcraft.managers.RebellionManager;
import net.unitedcraft.managers.WarManager;
import net.unitedcraft.models.LandClaim;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;

import java.util.Optional;

public class PlayerEventListener {

    private static final NationManager nationManager = new NationManager();
    private static final LandManager landManager = new LandManager();
    private static final WarManager warManager = new WarManager();
    private static final RebellionManager rebellionManager = new RebellionManager();

    private static int tickCounter = 0;
    private static final int REBELLION_CHECK_INTERVAL = 20 * 60 * 5; // every 5 minutes

    public static void register() {

        // ── Player Join ───────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            Optional<Nation> nation = nationManager.getNationOfPlayer(player.getUuid());
            Optional<NationMember> member = nationManager.getMember(player.getUuid());

            if (nation.isPresent() && member.isPresent()) {
                player.sendMessage(Text.literal(
                    "§6Welcome back! §e" + nation.get().getName() +
                    " §6| §f" + member.get().getRoleDisplay() +
                    "§6 | Use §e/nc §6for nation chat."
                ));
            } else {
                player.sendMessage(Text.literal(
                    "§6Welcome to §eUnitedCraft§6! " +
                    "Join or found a nation with §e/nation help§6. " +
                    "Type §e/nation list §6to see existing nations."
                ));
            }
        });

        // ── Chat Intercept — nation chat toggle ───────────────
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (UnitedCraft.chatManager.isNationChatOn(sender.getUuid())) {
                // Redirect to nation chat
                UnitedCraft.chatManager.sendNationMessage(sender, message.getContent().getString(),
                    sender.getServer());
                return false; // Block public chat
            }
            return true; // Allow normal chat
        });

        // ── PvP Protection ────────────────────────────────────
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return true;
            if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) return true;

            // Check if in claimed land
            Optional<LandClaim> claim = landManager.getClaimAt(victim);
            if (claim.isEmpty()) return true; // Wilderness — PvP always allowed

            String claimingNationId = claim.get().getNationId();
            Optional<Nation> claimNation = nationManager.getNationById(claimingNationId);

            // Nation allows PvP in territory
            if (claimNation.isPresent() && claimNation.get().isPvpEnabled()) return true;

            Optional<Nation> attackerNation = nationManager.getNationOfPlayer(attacker.getUuid());
            Optional<Nation> victimNation = nationManager.getNationOfPlayer(victim.getUuid());

            // Enemies at war — PvP allowed, award war score
            if (attackerNation.isPresent() && victimNation.isPresent()) {
                if (warManager.areAtWar(attackerNation.get().getId(), victimNation.get().getId())) {
                    warManager.addWarScore(attackerNation.get().getId(), 1);
                    return true;
                }
            }

            // Same nation — no friendly fire
            if (attackerNation.isPresent() && victimNation.isPresent() &&
                attackerNation.get().getId().equals(victimNation.get().getId())) {
                attacker.sendMessage(Text.literal("§cFriendly fire is disabled."), true);
                return false;
            }

            // Non-pvp territory
            if (!claimNation.map(Nation::isPvpEnabled).orElse(false)) {
                attacker.sendMessage(Text.literal("§cPvP is disabled in this territory."), true);
                return false;
            }

            return true;
        });

        // ── Tick — periodic rebellion checks ─────────────────
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            tickCounter++;
            if (tickCounter >= REBELLION_CHECK_INTERVAL) {
                tickCounter = 0;
                rebellionManager.checkRebellions(server);
            }
        });
    }
}
