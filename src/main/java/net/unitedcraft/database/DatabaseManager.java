package net.unitedcraft.database;

import net.unitedcraft.UnitedCraft;
import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private Connection connection;
    private static final String DB_PATH = "config/unitedcraft/data.db";

    public void initialize() {
        try {
            File dir = new File("config/unitedcraft");
            if (!dir.exists()) dir.mkdirs();

            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
            }
            createTables();
            UnitedCraft.LOGGER.info("Database connected: " + DB_PATH);
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Failed to connect to database", e);
        }
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();

        // Nations
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS nations (
                id TEXT PRIMARY KEY,
                name TEXT UNIQUE NOT NULL,
                government_type TEXT NOT NULL DEFAULT 'REPUBLIC',
                capital_chunk TEXT,
                power INTEGER DEFAULT 0,
                balance REAL DEFAULT 0.0,
                tax_rate REAL DEFAULT 0.05,
                pvp_enabled INTEGER DEFAULT 0,
                open_recruitment INTEGER DEFAULT 1,
                visitor_build INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL,
                discontentment INTEGER DEFAULT 0,
                description TEXT DEFAULT ''
            )
        """);

        // Nation members — new roles
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS nation_members (
                player_uuid TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                nation_id TEXT,
                role TEXT DEFAULT 'CIVILIAN',
                joined_at INTEGER NOT NULL,
                FOREIGN KEY (nation_id) REFERENCES nations(id)
            )
        """);

        // Player wallets (personal money, separate from nation bank)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_wallets (
                player_uuid TEXT PRIMARY KEY,
                balance REAL DEFAULT 0.0
            )
        """);

        // Land claims
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS land_claims (
                chunk_key TEXT PRIMARY KEY,
                nation_id TEXT NOT NULL,
                claim_type TEXT DEFAULT 'STANDARD',
                claimed_at INTEGER NOT NULL,
                FOREIGN KEY (nation_id) REFERENCES nations(id)
            )
        """);

        // Personal plots (player-owned within their nation's land)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS personal_plots (
                chunk_key TEXT PRIMARY KEY,
                nation_id TEXT NOT NULL,
                owner_uuid TEXT NOT NULL,
                owner_name TEXT NOT NULL,
                claimed_at INTEGER NOT NULL,
                FOREIGN KEY (nation_id) REFERENCES nations(id)
            )
        """);

        // Land build permissions — per-player grants by President
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS land_permissions (
                chunk_key TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                granted_by TEXT NOT NULL,
                granted_at INTEGER NOT NULL,
                PRIMARY KEY (chunk_key, player_uuid)
            )
        """);

        // Wars
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS wars (
                id TEXT PRIMARY KEY,
                attacker_id TEXT NOT NULL,
                defender_id TEXT NOT NULL,
                attacker_score INTEGER DEFAULT 0,
                defender_score INTEGER DEFAULT 0,
                status TEXT DEFAULT 'ACTIVE',
                declared_at INTEGER NOT NULL,
                ended_at INTEGER,
                FOREIGN KEY (attacker_id) REFERENCES nations(id),
                FOREIGN KEY (defender_id) REFERENCES nations(id)
            )
        """);

        // Votes
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS votes (
                id TEXT PRIMARY KEY,
                nation_id TEXT NOT NULL,
                role TEXT NOT NULL,
                candidate_uuid TEXT NOT NULL,
                voter_uuid TEXT NOT NULL,
                voted_at INTEGER NOT NULL,
                FOREIGN KEY (nation_id) REFERENCES nations(id)
            )
        """);

        // Elections
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS elections (
                id TEXT PRIMARY KEY,
                nation_id TEXT NOT NULL,
                role TEXT NOT NULL,
                status TEXT DEFAULT 'OPEN',
                started_at INTEGER NOT NULL,
                ends_at INTEGER NOT NULL,
                FOREIGN KEY (nation_id) REFERENCES nations(id)
            )
        """);

        // Nation invites
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS invites (
                player_uuid TEXT NOT NULL,
                nation_id TEXT NOT NULL,
                invited_by TEXT NOT NULL,
                invited_at INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, nation_id)
            )
        """);

        // Rebellions
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS rebellions (
                id TEXT PRIMARY KEY,
                nation_id TEXT NOT NULL,
                status TEXT DEFAULT 'BREWING',
                started_at INTEGER NOT NULL,
                coup_ends_at INTEGER,
                FOREIGN KEY (nation_id) REFERENCES nations(id)
            )
        """);

        // Alliances
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS alliances (
                id TEXT PRIMARY KEY,
                nation_a_id TEXT NOT NULL,
                nation_b_id TEXT NOT NULL,
                status TEXT DEFAULT 'PENDING',
                created_at INTEGER NOT NULL,
                responded_at INTEGER
            )
        """);

        // Peace treaties
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS treaties (
                id TEXT PRIMARY KEY,
                war_id TEXT NOT NULL,
                proposer_nation_id TEXT NOT NULL,
                receiver_nation_id TEXT NOT NULL,
                terms TEXT NOT NULL,
                status TEXT DEFAULT 'PROPOSED',
                proposed_at INTEGER NOT NULL
            )
        """);

        // Nation chat toggle
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS nation_chat_toggle (
                player_uuid TEXT PRIMARY KEY
            )
        """);

        stmt.close();
    }

    public Connection getConnection() { return connection; }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error closing database", e);
        }
    }
}
