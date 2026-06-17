package net.unitedcraft.managers;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.Alliance;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;

import java.sql.*;
import java.util.*;

public class AllianceManager {

    private final NationManager nationManager = new NationManager();

    // ── Propose Alliance ─────────────────────────────────────

    public void proposeAlliance(String proposerNationId, String targetNationId) throws Exception {
        if (proposerNationId.equals(targetNationId)) {
            throw new Exception("You cannot ally with yourself.");
        }

        // Check not already allied
        if (areAllied(proposerNationId, targetNationId)) {
            throw new Exception("You are already allied with this nation.");
        }

        // Check no pending request already
        if (hasPendingRequest(proposerNationId, targetNationId)) {
            throw new Exception("There is already a pending alliance request between these nations.");
        }

        // Cannot ally with a nation you are at war with
        if (UnitedCraft.warManager.areAtWar(proposerNationId, targetNationId)) {
            throw new Exception("You cannot propose an alliance while at war with this nation.");
        }

        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "INSERT INTO alliances (id, nation_a_id, nation_b_id, status, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, proposerNationId);
            ps.setString(3, targetNationId);
            ps.setString(4, "PENDING");
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public void acceptAlliance(String receiverNationId, String proposerNationId) throws Exception {
        String allianceId = getPendingAllianceId(proposerNationId, receiverNationId);
        if (allianceId == null) {
            throw new Exception("No pending alliance request from that nation.");
        }
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "UPDATE alliances SET status = 'ACTIVE', responded_at = ? WHERE id = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, allianceId);
            ps.executeUpdate();
        }
    }

    public void denyAlliance(String receiverNationId, String proposerNationId) throws Exception {
        String allianceId = getPendingAllianceId(proposerNationId, receiverNationId);
        if (allianceId == null) throw new Exception("No pending alliance request from that nation.");
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "UPDATE alliances SET status = 'DISSOLVED', responded_at = ? WHERE id = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, allianceId);
            ps.executeUpdate();
        }
    }

    public void breakAlliance(String nationAId, String nationBId) throws Exception {
        if (!areAllied(nationAId, nationBId)) {
            throw new Exception("You are not allied with that nation.");
        }
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement("""
                UPDATE alliances SET status = 'DISSOLVED'
                WHERE status = 'ACTIVE'
                AND ((nation_a_id = ? AND nation_b_id = ?) OR (nation_a_id = ? AND nation_b_id = ?))
                """)) {
            ps.setString(1, nationAId);
            ps.setString(2, nationBId);
            ps.setString(3, nationBId);
            ps.setString(4, nationAId);
            ps.executeUpdate();
        }
    }

    // ── Queries ───────────────────────────────────────────────

    public boolean areAllied(String nationA, String nationB) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement("""
                SELECT id FROM alliances WHERE status = 'ACTIVE'
                AND ((nation_a_id = ? AND nation_b_id = ?) OR (nation_a_id = ? AND nation_b_id = ?))
                """)) {
            ps.setString(1, nationA); ps.setString(2, nationB);
            ps.setString(3, nationB); ps.setString(4, nationA);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error checking alliance", e);
        }
        return false;
    }

    public List<String> getAlliedNationIds(String nationId) {
        List<String> allies = new ArrayList<>();
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT nation_a_id, nation_b_id FROM alliances WHERE status = 'ACTIVE' AND (nation_a_id = ? OR nation_b_id = ?)")) {
            ps.setString(1, nationId);
            ps.setString(2, nationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String a = rs.getString("nation_a_id");
                String b = rs.getString("nation_b_id");
                allies.add(a.equals(nationId) ? b : a);
            }
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting allies", e);
        }
        return allies;
    }

    private boolean hasPendingRequest(String a, String b) {
        return getPendingAllianceId(a, b) != null;
    }

    private String getPendingAllianceId(String proposer, String receiver) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement("""
                SELECT id FROM alliances WHERE status = 'PENDING'
                AND ((nation_a_id = ? AND nation_b_id = ?) OR (nation_a_id = ? AND nation_b_id = ?))
                """)) {
            ps.setString(1, proposer); ps.setString(2, receiver);
            ps.setString(3, receiver); ps.setString(4, proposer);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("id");
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error finding pending alliance", e);
        }
        return null;
    }

    // Notify all online members of allied nations when something major happens
    public void broadcastToAllies(MinecraftServer server, String nationId, String message) {
        for (String allyId : getAlliedNationIds(nationId)) {
            nationManager.getMembers(allyId).forEach(m -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.getPlayerUuid());
                if (p != null) p.sendMessage(Text.literal(message));
            });
        }
    }
}
