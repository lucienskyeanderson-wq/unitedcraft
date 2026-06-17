package net.unitedcraft.managers;

import net.unitedcraft.UnitedCraft;
import net.unitedcraft.database.DatabaseManager;

import java.sql.*;

public class EconomyManager {

    public EconomyManager() {
        createTablesIfNeeded();
    }

    private void createTablesIfNeeded() {
        try (Connection conn = UnitedCraft.db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_balance (
                    uuid TEXT PRIMARY KEY,
                    username TEXT NOT NULL,
                    balance REAL DEFAULT 0.0
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS economy_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    from_uuid TEXT,
                    to_uuid TEXT,
                    amount REAL NOT NULL,
                    reason TEXT
                )
            """);
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("EconomyManager table error: {}", e.getMessage());
        }
    }

    // ── Balance ───────────────────────────────────────────────

    public double getBalance(String uuid) {
        try (Connection conn = UnitedCraft.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT balance FROM player_balance WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("getBalance error: {}", e.getMessage());
        }
        return 0.0;
    }

    public void ensureAccount(String uuid, String username) {
        try (Connection conn = UnitedCraft.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO player_balance (uuid, username, balance) VALUES (?, ?, 0.0)")) {
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("ensureAccount error: {}", e.getMessage());
        }
    }

    public boolean deposit(String uuid, double amount, String reason) {
        if (amount <= 0) return false;
        try (Connection conn = UnitedCraft.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE player_balance SET balance = balance + ? WHERE uuid = ?")) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid);
            ps.executeUpdate();
            log(conn, null, uuid, amount, reason);
            return true;
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("deposit error: {}", e.getMessage());
            return false;
        }
    }

    public boolean withdraw(String uuid, double amount, String reason) {
        if (amount <= 0) return false;
        if (getBalance(uuid) < amount) return false;
        try (Connection conn = UnitedCraft.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE player_balance SET balance = balance - ? WHERE uuid = ?")) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid);
            ps.executeUpdate();
            log(conn, uuid, null, amount, reason);
            return true;
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("withdraw error: {}", e.getMessage());
            return false;
        }
    }

    public boolean transfer(String fromUuid, String toUuid, double amount) {
        if (getBalance(fromUuid) < amount) return false;
        try (Connection conn = UnitedCraft.db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PreparedStatement sub = conn.prepareStatement(
                    "UPDATE player_balance SET balance = balance - ? WHERE uuid = ?");
                sub.setDouble(1, amount); sub.setString(2, fromUuid); sub.executeUpdate();

                PreparedStatement add = conn.prepareStatement(
                    "UPDATE player_balance SET balance = balance + ? WHERE uuid = ?");
                add.setDouble(1, amount); add.setString(2, toUuid); add.executeUpdate();

                log(conn, fromUuid, toUuid, amount, "player_transfer");
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("transfer error: {}", e.getMessage());
            return false;
        }
    }

    private void log(Connection conn, String from, String to, double amount, String reason) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO economy_log (timestamp, from_uuid, to_uuid, amount, reason) VALUES (?,?,?,?,?)");
        ps.setLong(1, System.currentTimeMillis());
        ps.setString(2, from);
        ps.setString(3, to);
        ps.setDouble(4, amount);
        ps.setString(5, reason);
        ps.executeUpdate();
    }

    // ── Formatting ────────────────────────────────────────────

    public String format(double amount) {
        return String.format("$%.2f", amount);
    }
}
