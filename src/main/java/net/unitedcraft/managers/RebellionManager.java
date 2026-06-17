package net.unitedcraft.managers;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;
import net.unitedcraft.repository.NationRepository;

import java.sql.*;
import java.util.*;

public class RebellionManager {

    public static final int REBELLION_THRESHOLD = 75;
    private static final long COUP_WINDOW_MS = 2 * 60 * 60 * 1000L; // 2 hours

    private final NationRepository repo = new NationRepository();
    private final NationManager nationManager = new NationManager();

    public void checkRebellions(MinecraftServer server) {
        for (Nation nation : nationManager.getAllNations()) {
            if (nation.getDiscontentment() >= REBELLION_THRESHOLD) {
                if (!hasActiveRebellion(nation.getId())) {
                    startRebellion(nation, server);
                }
            }
        }
    }

    public void startRebellion(Nation nation, MinecraftServer server) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long coupEnds = now + COUP_WINDOW_MS;

        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "INSERT INTO rebellions (id, nation_id, status, started_at, coup_ends_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, nation.getId());
            ps.setString(3, "BREWING");
            ps.setLong(4, now);
            ps.setLong(5, coupEnds);
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error starting rebellion", e);
            return;
        }

        broadcastToNation(server, nation.getId(),
            "§c⚠ REBELLION BREWING in §e" + nation.getName() + "§c! " +
            "Citizens are unhappy! A 2-hour coup window is now open. " +
            "Use §e/nation coup §cto attempt to overthrow the President!");
    }

    public void attemptCoup(UUID instigatorUuid, MinecraftServer server) throws Exception {
        Optional<NationMember> memberOpt = nationManager.getMember(instigatorUuid);
        if (memberOpt.isEmpty() || memberOpt.get().getNationId() == null) {
            throw new Exception("You are not in a nation.");
        }

        NationMember member = memberOpt.get();
        if (member.isPresident()) throw new Exception("You are already the President.");

        // Exiles cannot coup
        if (member.getRole() == NationMember.Role.EXILE) {
            throw new Exception("Exiles cannot attempt a coup.");
        }

        String nationId = member.getNationId();
        Nation nation = nationManager.getNationById(nationId)
            .orElseThrow(() -> new Exception("Nation not found."));

        if (nation.getDiscontentment() < REBELLION_THRESHOLD) {
            throw new Exception("The nation is not unhappy enough for a coup (need " +
                REBELLION_THRESHOLD + "%, currently " + nation.getDiscontentment() + "%).");
        }

        if (!hasActiveRebellion(nationId)) {
            throw new Exception("No active rebellion in your nation.");
        }

        // Success chance: 40% base + bonus for discontentment above threshold, capped at 85%
        int successChance = 40 + (nation.getDiscontentment() - REBELLION_THRESHOLD);
        successChance = Math.min(85, successChance);
        boolean success = new Random().nextInt(100) < successChance;

        if (success) {
            // Demote current president
            List<NationMember> members = nationManager.getMembers(nationId);
            for (NationMember m : members) {
                if (m.isPresident()) {
                    m.setRole(NationMember.Role.CITIZEN);
                    repo.saveMember(m);
                }
            }
            // Instigator becomes President
            member.setRole(NationMember.Role.PRESIDENT);
            repo.saveMember(member);

            // Reset discontentment
            nation.setDiscontentment(20);
            repo.saveNation(nation);
            closeRebellion(nationId);

            broadcastToNation(server, nationId,
                "§6✦ COUP SUCCESSFUL! §f" + member.getPlayerName() +
                " §6has seized power in §e" + nation.getName() +
                "§6! A new era begins. An election for Vice President has been called.");

            // Auto-start VP election
            UnitedCraft.voteManager.startElection(nationId, NationMember.Role.VICE_PRESIDENT);

        } else {
            // Coup failed — exile the instigator
            member.setRole(NationMember.Role.EXILE);
            repo.saveMember(member);

            nation.addDiscontentment(5);
            repo.saveNation(nation);

            broadcastToNation(server, nationId,
                "§4✗ COUP FAILED! §f" + member.getPlayerName() +
                " §4attempted to overthrow §e" + nation.getName() +
                "§4's President and has been exiled!");
        }
    }

    public void increaseDiscontentment(String nationId, int amount, String reason) {
        Optional<Nation> nation = nationManager.getNationById(nationId);
        nation.ifPresent(n -> {
            n.addDiscontentment(amount);
            repo.saveNation(n);
            UnitedCraft.LOGGER.info("Discontentment +" + amount + " for " + n.getName() + " (" + reason + ") → " + n.getDiscontentment() + "%");
        });
    }

    public void decreaseDiscontentment(String nationId, int amount) {
        Optional<Nation> nation = nationManager.getNationById(nationId);
        nation.ifPresent(n -> {
            n.setDiscontentment(Math.max(0, n.getDiscontentment() - amount));
            repo.saveNation(n);
        });
    }

    private boolean hasActiveRebellion(String nationId) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT id FROM rebellions WHERE nation_id = ? AND status = 'BREWING'")) {
            ps.setString(1, nationId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error checking rebellion", e);
        }
        return false;
    }

    private void closeRebellion(String nationId) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "UPDATE rebellions SET status = 'RESOLVED' WHERE nation_id = ? AND status = 'BREWING'")) {
            ps.setString(1, nationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error closing rebellion", e);
        }
    }

    private void broadcastToNation(MinecraftServer server, String nationId, String message) {
        new NationRepository().getMembersByNation(nationId).forEach(m -> {
            var player = server.getPlayerManager().getPlayer(m.getPlayerUuid());
            if (player != null) player.sendMessage(Text.literal(message));
        });
    }
}
