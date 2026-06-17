package net.unitedcraft.listeners;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.managers.LandManager;
import net.unitedcraft.managers.NationManager;
import net.unitedcraft.models.LandClaim;
import net.unitedcraft.models.NationMember;
import net.unitedcraft.models.PersonalPlot;

import java.util.Optional;

public class BlockEventListener {

    private static final LandManager landManager = new LandManager();
    private static final NationManager nationManager = new NationManager();

    public static void register() {

        // ── Block Break Protection ────────────────────────────
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return true;
            // Server ops always bypass
            if (serverPlayer.hasPermissionLevel(2)) return true;

            String chunkKey = LandClaim.toChunkKey(
                world.getRegistryKey().getValue().toString(),
                pos.getX() >> 4, pos.getZ() >> 4
            );

            if (!landManager.canBuild(serverPlayer, chunkKey)) {
                String reason = getDeniedReason(serverPlayer, chunkKey);
                serverPlayer.sendMessage(Text.literal("§c" + reason), true);
                return false;
            }
            return true;
        });

        // ── Block Place / Interaction Protection ──────────────
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (serverPlayer.hasPermissionLevel(2)) return ActionResult.PASS;

            var pos = hitResult.getBlockPos();
            String chunkKey = LandClaim.toChunkKey(
                world.getRegistryKey().getValue().toString(),
                pos.getX() >> 4, pos.getZ() >> 4
            );

            // Wilderness — pass
            if (landManager.getClaimAtKey(chunkKey).isEmpty()) return ActionResult.PASS;

            if (!landManager.canBuild(serverPlayer, chunkKey)) {
                String reason = getDeniedReason(serverPlayer, chunkKey);
                serverPlayer.sendMessage(Text.literal("§c" + reason), true);
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });
    }

    /**
     * Generates a helpful message explaining why a player was denied.
     */
    private static String getDeniedReason(ServerPlayerEntity player, String chunkKey) {
        Optional<LandClaim> claim = landManager.getClaimAtKey(chunkKey);
        if (claim.isEmpty()) return "You cannot build here.";

        // Check if it's a personal plot belonging to someone else
        Optional<PersonalPlot> plot = landManager.getPersonalPlot(chunkKey);
        if (plot.isPresent()) {
            return "This is §e" + plot.get().getOwnerName() + "§c's personal plot. You cannot build here.";
        }

        // Check if player is an exile
        Optional<NationMember> member = nationManager.getMember(player.getUuid());
        if (member.isPresent() && member.get().getRole() == NationMember.Role.EXILE) {
            return "You are exiled from this nation and cannot build in their territory.";
        }

        // General territory denial
        String nationId = claim.get().getNationId();
        String nationName = UnitedCraft.nationManager.getNationById(nationId)
            .map(n -> n.getName()).orElse("this nation");
        return "You cannot build in §e" + nationName + "§c's territory. Ask the President for permission.";
    }
}
