package net.unitedcraft.managers;

import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.NationMember;
import net.unitedcraft.repository.NationRepository;

import java.sql.*;
import java.util.*;

public class VoteManager {

    private final NationRepository repo = new NationRepository();
    private static final long ELECTION_DURATION_MS = 24 * 60 * 60 * 1000L; // 24 hours

    public void startElection(String nationId, NationMember.Role role) throws Exception {
        Connection conn = UnitedCraft.db.getConnection();

        // Check no active election for this role already
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM elections WHERE nation_id = ? AND role = ? AND status = 'OPEN'")) {
            ps.setString(1, nationId);
            ps.setString(2, role.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) throw new Exception("An election for " + role.name() + " is already running.");
        }

        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long endsAt = now + ELECTION_DURATION_MS;

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO elections (id, nation_id, role, status, started_at, ends_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, nationId);
            ps.setString(3, role.name());
            ps.setString(4, "OPEN");
            ps.setLong(5, now);
            ps.setLong(6, endsAt);
            ps.executeUpdate();
        }
    }

    public void castVote(String nationId, NationMember.Role role,
                          UUID voterUuid, UUID candidateUuid) throws Exception {
        Connection conn = UnitedCraft.db.getConnection();

        // Check election is open
        String electionId = getOpenElectionId(nationId, role);
        if (electionId == null) throw new Exception("No open election for " + role.name() + ".");

        // Check voter hasn't already voted
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM votes WHERE nation_id = ? AND role = ? AND voter_uuid = ?")) {
            ps.setString(1, nationId);
            ps.setString(2, role.name());
            ps.setString(3, voterUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) throw new Exception("You have already voted in this election.");
        }

        String voteId = UUID.randomUUID().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO votes (id, nation_id, role, candidate_uuid, voter_uuid, voted_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, voteId);
            ps.setString(2, nationId);
            ps.setString(3, role.name());
            ps.setString(4, candidateUuid.toString());
            ps.setString(5, voterUuid.toString());
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public Map<UUID, Integer> getVoteCounts(String nationId, NationMember.Role role) {
        Map<UUID, Integer> counts = new HashMap<>();
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT candidate_uuid, COUNT(*) as cnt FROM votes WHERE nation_id = ? AND role = ? GROUP BY candidate_uuid")) {
            ps.setString(1, nationId);
            ps.setString(2, role.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                counts.put(UUID.fromString(rs.getString("candidate_uuid")), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting vote counts", e);
        }
        return counts;
    }

    public Optional<UUID> getElectionWinner(String nationId, NationMember.Role role) {
        Map<UUID, Integer> counts = getVoteCounts(nationId, role);
        return counts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey);
    }

    public void closeElection(String nationId, NationMember.Role role) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "UPDATE elections SET status = 'CLOSED' WHERE nation_id = ? AND role = ? AND status = 'OPEN'")) {
            ps.setString(1, nationId);
            ps.setString(2, role.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error closing election", e);
        }
    }

    private String getOpenElectionId(String nationId, NationMember.Role role) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT id FROM elections WHERE nation_id = ? AND role = ? AND status = 'OPEN'")) {
            ps.setString(1, nationId);
            ps.setString(2, role.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("id");
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error finding open election", e);
        }
        return null;
    }

    public boolean hasOpenElection(String nationId, NationMember.Role role) {
        return getOpenElectionId(nationId, role) != null;
    }
}
