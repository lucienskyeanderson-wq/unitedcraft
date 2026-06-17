package net.unitedcraft.repository;

import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;
import net.unitedcraft.models.LandClaim;

import java.sql.*;
import java.util.*;

public class NationRepository {

    private Connection conn() {
        return UnitedCraft.db.getConnection();
    }

    // ── Nations ──────────────────────────────────────────────

    public void saveNation(Nation n) {
        String sql = """
            INSERT OR REPLACE INTO nations
            (id, name, government_type, capital_chunk, power, balance, tax_rate,
             pvp_enabled, open_recruitment, visitor_build, created_at, discontentment)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, n.getId());
            ps.setString(2, n.getName());
            ps.setString(3, n.getGovernmentType().name());
            ps.setString(4, n.getCapitalChunk());
            ps.setInt(5, n.getPower());
            ps.setDouble(6, n.getBalance());
            ps.setDouble(7, n.getTaxRate());
            ps.setInt(8, n.isPvpEnabled() ? 1 : 0);
            ps.setInt(9, n.isOpenRecruitment() ? 1 : 0);
            ps.setInt(10, n.isVisitorBuild() ? 1 : 0);
            ps.setLong(11, n.getCreatedAt());
            ps.setInt(12, n.getDiscontentment());
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error saving nation", e);
        }
    }

    public Optional<Nation> getNationById(String id) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM nations WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapNation(rs));
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting nation by id", e);
        }
        return Optional.empty();
    }

    public Optional<Nation> getNationByName(String name) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM nations WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapNation(rs));
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting nation by name", e);
        }
        return Optional.empty();
    }

    public List<Nation> getAllNations() {
        List<Nation> nations = new ArrayList<>();
        try (Statement stmt = conn().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM nations");
            while (rs.next()) nations.add(mapNation(rs));
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting all nations", e);
        }
        return nations;
    }

    public void deleteNation(String nationId) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM nations WHERE id = ?")) {
            ps.setString(1, nationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error deleting nation", e);
        }
    }

    private Nation mapNation(ResultSet rs) throws SQLException {
        Nation n = new Nation(
            rs.getString("id"),
            rs.getString("name"),
            Nation.GovernmentType.valueOf(rs.getString("government_type")),
            rs.getLong("created_at")
        );
        n.setCapitalChunk(rs.getString("capital_chunk"));
        n.setPower(rs.getInt("power"));
        n.setBalance(rs.getDouble("balance"));
        n.setTaxRate(rs.getDouble("tax_rate"));
        n.setPvpEnabled(rs.getInt("pvp_enabled") == 1);
        n.setOpenRecruitment(rs.getInt("open_recruitment") == 1);
        n.setVisitorBuild(rs.getInt("visitor_build") == 1);
        n.setDiscontentment(rs.getInt("discontentment"));
        return n;
    }

    // ── Members ──────────────────────────────────────────────

    public void saveMember(NationMember m) {
        String sql = """
            INSERT OR REPLACE INTO nation_members
            (player_uuid, player_name, nation_id, role, joined_at)
            VALUES (?,?,?,?,?)
        """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, m.getPlayerUuid().toString());
            ps.setString(2, m.getPlayerName());
            ps.setString(3, m.getNationId());
            ps.setString(4, m.getRole().name());
            ps.setLong(5, m.getJoinedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error saving member", e);
        }
    }

    public Optional<NationMember> getMember(UUID uuid) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM nation_members WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapMember(rs));
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting member", e);
        }
        return Optional.empty();
    }

    public List<NationMember> getMembersByNation(String nationId) {
        List<NationMember> members = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM nation_members WHERE nation_id = ?")) {
            ps.setString(1, nationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) members.add(mapMember(rs));
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting members by nation", e);
        }
        return members;
    }

    public int getMemberCount(String nationId) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT COUNT(*) FROM nation_members WHERE nation_id = ?")) {
            ps.setString(1, nationId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error counting members", e);
        }
        return 0;
    }

    private NationMember mapMember(ResultSet rs) throws SQLException {
        return new NationMember(
            UUID.fromString(rs.getString("player_uuid")),
            rs.getString("player_name"),
            rs.getString("nation_id"),
            NationMember.Role.valueOf(rs.getString("role")),
            rs.getLong("joined_at")
        );
    }

    // ── Land Claims ───────────────────────────────────────────

    public void saveClaim(LandClaim claim) {
        String sql = """
            INSERT OR REPLACE INTO land_claims (chunk_key, nation_id, claim_type, claimed_at)
            VALUES (?,?,?,?)
        """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, claim.getChunkKey());
            ps.setString(2, claim.getNationId());
            ps.setString(3, claim.getClaimType().name());
            ps.setLong(4, claim.getClaimedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error saving claim", e);
        }
    }

    public Optional<LandClaim> getClaim(String chunkKey) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM land_claims WHERE chunk_key = ?")) {
            ps.setString(1, chunkKey);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapClaim(rs));
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting claim", e);
        }
        return Optional.empty();
    }

    public List<LandClaim> getClaimsByNation(String nationId) {
        List<LandClaim> claims = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM land_claims WHERE nation_id = ?")) {
            ps.setString(1, nationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) claims.add(mapClaim(rs));
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting claims by nation", e);
        }
        return claims;
    }

    public void deleteClaim(String chunkKey) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM land_claims WHERE chunk_key = ?")) {
            ps.setString(1, chunkKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error deleting claim", e);
        }
    }

    public int getClaimCount(String nationId) {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT COUNT(*) FROM land_claims WHERE nation_id = ?")) {
            ps.setString(1, nationId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error counting claims", e);
        }
        return 0;
    }

    private LandClaim mapClaim(ResultSet rs) throws SQLException {
        return new LandClaim(
            rs.getString("chunk_key"),
            rs.getString("nation_id"),
            LandClaim.ClaimType.valueOf(rs.getString("claim_type")),
            rs.getLong("claimed_at")
        );
    }
}
