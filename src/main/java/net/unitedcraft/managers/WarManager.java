package net.unitedcraft.managers;

import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.Nation;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class WarManager {

    private static final long WAR_COOLDOWN_MS = 48 * 60 * 60 * 1000L; // 48 hours between wars

    public void declareWar(String attackerNationId, String defenderNationId) throws Exception {
        Connection conn = UnitedCraft.db.getConnection();

        if (attackerNationId.equals(defenderNationId)) {
            throw new Exception("You cannot declare war on yourself.");
        }

        // Check no active war between these two
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id FROM wars WHERE status = 'ACTIVE'
                AND ((attacker_id = ? AND defender_id = ?) OR (attacker_id = ? AND defender_id = ?))
                """)) {
            ps.setString(1, attackerNationId);
            ps.setString(2, defenderNationId);
            ps.setString(3, defenderNationId);
            ps.setString(4, attackerNationId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) throw new Exception("There is already an active war between these nations.");
        }

        // Check cooldown
        long cooldownStart = System.currentTimeMillis() - WAR_COOLDOWN_MS;
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id FROM wars WHERE ended_at > ?
                AND ((attacker_id = ? AND defender_id = ?) OR (attacker_id = ? AND defender_id = ?))
                """)) {
            ps.setLong(1, cooldownStart);
            ps.setString(2, attackerNationId);
            ps.setString(3, defenderNationId);
            ps.setString(4, defenderNationId);
            ps.setString(5, attackerNationId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) throw new Exception("War cooldown still active between these nations.");
        }

        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO wars (id, attacker_id, defender_id, attacker_score, defender_score, status, declared_at)
                VALUES (?,?,?,0,0,'ACTIVE',?)
                """)) {
            ps.setString(1, id);
            ps.setString(2, attackerNationId);
            ps.setString(3, defenderNationId);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public void addWarScore(String nationId, int points) {
        try {
            Connection conn = UnitedCraft.db.getConnection();
            // Find active war where this nation is attacker or defender
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, attacker_id FROM wars WHERE status = 'ACTIVE' AND (attacker_id = ? OR defender_id = ?)")) {
                ps.setString(1, nationId);
                ps.setString(2, nationId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String warId = rs.getString("id");
                    boolean isAttacker = rs.getString("attacker_id").equals(nationId);
                    String scoreCol = isAttacker ? "attacker_score" : "defender_score";
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE wars SET " + scoreCol + " = " + scoreCol + " + ? WHERE id = ?")) {
                        update.setInt(1, points);
                        update.setString(2, warId);
                        update.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error adding war score", e);
        }
    }

    public void endWar(String warId, String status) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "UPDATE wars SET status = ?, ended_at = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, warId);
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error ending war", e);
        }
    }

    public boolean isAtWar(String nationId) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT id FROM wars WHERE status = 'ACTIVE' AND (attacker_id = ? OR defender_id = ?)")) {
            ps.setString(1, nationId);
            ps.setString(2, nationId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error checking war status", e);
        }
        return false;
    }

    public boolean areAtWar(String nationA, String nationB) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement("""
                SELECT id FROM wars WHERE status = 'ACTIVE'
                AND ((attacker_id = ? AND defender_id = ?) OR (attacker_id = ? AND defender_id = ?))
                """)) {
            ps.setString(1, nationA);
            ps.setString(2, nationB);
            ps.setString(3, nationB);
            ps.setString(4, nationA);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error checking war between nations", e);
        }
        return false;
    }
}
