package net.unitedcraft.managers;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;
import net.unitedcraft.models.Treaty;

import java.sql.*;
import java.util.*;

public class TreatyManager {

    private final NationManager nationManager = new NationManager();
    private final WarManager warManager = new WarManager();

    public void proposeTreaty(String proposerNationId, String receiverNationId,
                               Treaty.Terms terms) throws Exception {

        // Must be at war
        if (!warManager.areAtWar(proposerNationId, receiverNationId)) {
            throw new Exception("You can only propose a peace treaty to a nation you are at war with.");
        }

        // Find the active war ID
        String warId = getActiveWarId(proposerNationId, receiverNationId);
        if (warId == null) throw new Exception("No active war found.");

        // Check no pending treaty already
        if (hasPendingTreaty(warId)) {
            throw new Exception("There is already a pending peace treaty for this war.");
        }

        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement("""
                INSERT INTO treaties (id, war_id, proposer_nation_id, receiver_nation_id, terms, status, proposed_at)
                VALUES (?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, id);
            ps.setString(2, warId);
            ps.setString(3, proposerNationId);
            ps.setString(4, receiverNationId);
            ps.setString(5, terms.name());
            ps.setString(6, "PROPOSED");
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public void acceptTreaty(String receiverNationId, MinecraftServer server) throws Exception {
        TreatyInfo treaty = getPendingTreatyForReceiver(receiverNationId);
        if (treaty == null) throw new Exception("No pending peace treaty to accept.");

        // Mark treaty accepted
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "UPDATE treaties SET status = 'ACCEPTED' WHERE id = ?")) {
            ps.setString(1, treaty.id);
            ps.executeUpdate();
        }

        // End the war
        warManager.endWar(treaty.warId, "PEACE");

        // Apply terms
        applyTerms(treaty, server);

        // Broadcast
        Optional<Nation> proposer = nationManager.getNationById(treaty.proposerNationId);
        Optional<Nation> receiver = nationManager.getNationById(receiverNationId);
        String msg = "§a☮ PEACE TREATY SIGNED between §e"
            + proposer.map(Nation::getName).orElse("Unknown")
            + " §aand §e"
            + receiver.map(Nation::getName).orElse("Unknown")
            + "§a! Terms: §f" + treaty.terms;
        server.getPlayerManager().broadcast(Text.literal(msg), false);
    }

    public void rejectTreaty(String receiverNationId) throws Exception {
        TreatyInfo treaty = getPendingTreatyForReceiver(receiverNationId);
        if (treaty == null) throw new Exception("No pending peace treaty to reject.");
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "UPDATE treaties SET status = 'REJECTED' WHERE id = ?")) {
            ps.setString(1, treaty.id);
            ps.executeUpdate();
        }
    }

    private void applyTerms(TreatyInfo treaty, MinecraftServer server) {
        // Determine loser (lower war score)
        int attackerScore = getWarScore(treaty.warId, true);
        int defenderScore = getWarScore(treaty.warId, false);
        boolean proposerIsAttacker = isAttacker(treaty.warId, treaty.proposerNationId);
        int proposerScore = proposerIsAttacker ? attackerScore : defenderScore;
        int receiverScore = proposerIsAttacker ? defenderScore : attackerScore;
        String loserId = proposerScore < receiverScore ? treaty.proposerNationId : treaty.receiverNationId;

        switch (treaty.terms) {
            case WHITE_PEACE -> {
                // No consequences — just peace
            }
            case PAY_REPARATIONS -> {
                // Loser pays 20% of treasury to winner
                Optional<Nation> loser = nationManager.getNationById(loserId);
                String winnerId = loserId.equals(treaty.proposerNationId) ? treaty.receiverNationId : treaty.proposerNationId;
                Optional<Nation> winner = nationManager.getNationById(winnerId);
                if (loser.isPresent() && winner.isPresent()) {
                    double payment = loser.get().getBalance() * 0.20;
                    loser.get().setBalance(loser.get().getBalance() - payment);
                    winner.get().setBalance(winner.get().getBalance() + payment);
                    nationManager.getRepo().saveNation(loser.get());
                    nationManager.getRepo().saveNation(winner.get());
                }
            }
            case CEDE_TERRITORY -> {
                // Loser's BORDER chunks nearest to winner become unclaimed
                // (simplified: removes loser's 3 most recently claimed border chunks)
                nationManager.getRepo().getClaimsByNation(loserId).stream()
                    .filter(c -> c.getClaimType().name().equals("BORDER"))
                    .sorted(Comparator.comparingLong(c -> -c.getClaimedAt()))
                    .limit(3)
                    .forEach(c -> nationManager.getRepo().deleteClaim(c.getChunkKey()));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private String getActiveWarId(String a, String b) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement("""
                SELECT id FROM wars WHERE status = 'ACTIVE'
                AND ((attacker_id = ? AND defender_id = ?) OR (attacker_id = ? AND defender_id = ?))
                """)) {
            ps.setString(1, a); ps.setString(2, b);
            ps.setString(3, b); ps.setString(4, a);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("id");
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting war id", e);
        }
        return null;
    }

    private boolean hasPendingTreaty(String warId) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT id FROM treaties WHERE war_id = ? AND status = 'PROPOSED'")) {
            ps.setString(1, warId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error checking treaty", e);
        }
        return false;
    }

    private TreatyInfo getPendingTreatyForReceiver(String receiverNationId) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT * FROM treaties WHERE receiver_nation_id = ? AND status = 'PROPOSED'")) {
            ps.setString(1, receiverNationId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                TreatyInfo t = new TreatyInfo();
                t.id = rs.getString("id");
                t.warId = rs.getString("war_id");
                t.proposerNationId = rs.getString("proposer_nation_id");
                t.receiverNationId = rs.getString("receiver_nation_id");
                t.terms = Treaty.Terms.valueOf(rs.getString("terms"));
                return t;
            }
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting treaty", e);
        }
        return null;
    }

    private int getWarScore(String warId, boolean attacker) {
        String col = attacker ? "attacker_score" : "defender_score";
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT " + col + " FROM wars WHERE id = ?")) {
            ps.setString(1, warId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting war score", e);
        }
        return 0;
    }

    private boolean isAttacker(String warId, String nationId) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT attacker_id FROM wars WHERE id = ?")) {
            ps.setString(1, warId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("attacker_id").equals(nationId);
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error checking attacker", e);
        }
        return false;
    }

    // Inner class for treaty data
    static class TreatyInfo {
        String id, warId, proposerNationId, receiverNationId;
        Treaty.Terms terms;
    }
}
