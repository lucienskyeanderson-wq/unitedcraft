package net.unitedcraft.managers;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.unitedcraft.UnitedCraft;

import java.sql.*;
import java.util.*;

public class JobManager {

    public enum Job {
        MINER, FARMER, LUMBERJACK, HUNTER, FISHERMAN
    }

    // Payout tables
    private static final Map<Block, Double> MINER_BLOCKS = new HashMap<>();
    private static final Map<Block, Double> FARMER_BLOCKS = new HashMap<>();
    private static final Map<Block, Double> LUMBER_BLOCKS = new HashMap<>();
    private static final Map<Item, Double> HUNTER_KILLS = new HashMap<>();
    private static final Map<Item, Double> FISHER_ITEMS = new HashMap<>();

    // Daily earn caps per job (anti-grind)
    private static final Map<Job, Double> DAILY_CAPS = new HashMap<>();

    static {
        // Miner payouts (per block broken)
        MINER_BLOCKS.put(Blocks.COAL_ORE,         1.0);
        MINER_BLOCKS.put(Blocks.DEEPSLATE_COAL_ORE, 1.5);
        MINER_BLOCKS.put(Blocks.IRON_ORE,         3.0);
        MINER_BLOCKS.put(Blocks.DEEPSLATE_IRON_ORE, 4.0);
        MINER_BLOCKS.put(Blocks.GOLD_ORE,         5.0);
        MINER_BLOCKS.put(Blocks.DEEPSLATE_GOLD_ORE, 7.0);
        MINER_BLOCKS.put(Blocks.DIAMOND_ORE,      15.0);
        MINER_BLOCKS.put(Blocks.DEEPSLATE_DIAMOND_ORE, 20.0);
        MINER_BLOCKS.put(Blocks.EMERALD_ORE,      12.0);
        MINER_BLOCKS.put(Blocks.ANCIENT_DEBRIS,   50.0);

        // Farmer payouts (per crop harvested)
        FARMER_BLOCKS.put(Blocks.WHEAT,           1.0);
        FARMER_BLOCKS.put(Blocks.POTATOES,        1.0);
        FARMER_BLOCKS.put(Blocks.CARROTS,         1.0);
        FARMER_BLOCKS.put(Blocks.BEETROOTS,       1.5);
        FARMER_BLOCKS.put(Blocks.PUMPKIN,         2.0);
        FARMER_BLOCKS.put(Blocks.MELON,           2.0);
        FARMER_BLOCKS.put(Blocks.SUGAR_CANE,      0.5);
        FARMER_BLOCKS.put(Blocks.CACTUS,          0.5);
        FARMER_BLOCKS.put(Blocks.COCOA,           1.5);

        // Lumberjack payouts (per log broken)
        LUMBER_BLOCKS.put(Blocks.OAK_LOG,         0.5);
        LUMBER_BLOCKS.put(Blocks.BIRCH_LOG,       0.5);
        LUMBER_BLOCKS.put(Blocks.SPRUCE_LOG,      0.5);
        LUMBER_BLOCKS.put(Blocks.JUNGLE_LOG,      0.5);
        LUMBER_BLOCKS.put(Blocks.ACACIA_LOG,      0.5);
        LUMBER_BLOCKS.put(Blocks.DARK_OAK_LOG,    0.5);
        LUMBER_BLOCKS.put(Blocks.MANGROVE_LOG,    0.75);
        LUMBER_BLOCKS.put(Blocks.CHERRY_LOG,      0.75);
        LUMBER_BLOCKS.put(Blocks.CRIMSON_STEM,    1.0);
        LUMBER_BLOCKS.put(Blocks.WARPED_STEM,     1.0);

        // Hunter payouts (per kill drop item)
        HUNTER_KILLS.put(Items.BEEF,              2.0);
        HUNTER_KILLS.put(Items.PORKCHOP,          2.0);
        HUNTER_KILLS.put(Items.CHICKEN,           1.5);
        HUNTER_KILLS.put(Items.MUTTON,            1.5);
        HUNTER_KILLS.put(Items.RABBIT,            1.5);
        HUNTER_KILLS.put(Items.LEATHER,           1.0);
        HUNTER_KILLS.put(Items.SPIDER_EYE,        2.0);
        HUNTER_KILLS.put(Items.BONE,              1.0);
        HUNTER_KILLS.put(Items.GUNPOWDER,         3.0);
        HUNTER_KILLS.put(Items.ENDER_PEARL,       8.0);
        HUNTER_KILLS.put(Items.BLAZE_ROD,         10.0);

        // Fisher payouts (per fish caught)
        FISHER_ITEMS.put(Items.COD,               2.0);
        FISHER_ITEMS.put(Items.SALMON,            3.0);
        FISHER_ITEMS.put(Items.TROPICAL_FISH,     4.0);
        FISHER_ITEMS.put(Items.PUFFERFISH,        3.0);

        // Daily caps
        DAILY_CAPS.put(Job.MINER,      500.0);
        DAILY_CAPS.put(Job.FARMER,     200.0);
        DAILY_CAPS.put(Job.LUMBERJACK, 150.0);
        DAILY_CAPS.put(Job.HUNTER,     300.0);
        DAILY_CAPS.put(Job.FISHERMAN,  200.0);
    }

    public JobManager() {
        createTablesIfNeeded();
    }

    private void createTablesIfNeeded() {
        try (Connection conn = UnitedCraft.db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_jobs (
                    uuid TEXT PRIMARY KEY,
                    job TEXT NOT NULL DEFAULT 'MINER'
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS job_daily_earnings (
                    uuid TEXT NOT NULL,
                    job TEXT NOT NULL,
                    date TEXT NOT NULL,
                    earned REAL DEFAULT 0.0,
                    PRIMARY KEY (uuid, date)
                )
            """);
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("JobManager table error: {}", e.getMessage());
        }
    }

    // ── Job Assignment ────────────────────────────────────────

    public Job getJob(String uuid) {
        try (Connection conn = UnitedCraft.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT job FROM player_jobs WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Job.valueOf(rs.getString("job"));
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("getJob error: {}", e.getMessage());
        }
        return null;
    }

    public void setJob(String uuid, Job job) {
        try (Connection conn = UnitedCraft.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO player_jobs (uuid, job) VALUES (?, ?)")) {
            ps.setString(1, uuid);
            ps.setString(2, job.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("setJob error: {}", e.getMessage());
        }
    }

    // ── Payout Logic ──────────────────────────────────────────

    /** Returns the payout for a miner breaking a block, or 0 if not applicable */
    public double getMinerPayout(Block block) {
        return MINER_BLOCKS.getOrDefault(block, 0.0);
    }

    public double getFarmerPayout(Block block) {
        return FARMER_BLOCKS.getOrDefault(block, 0.0);
    }

    public double getLumberjackPayout(Block block) {
        return LUMBER_BLOCKS.getOrDefault(block, 0.0);
    }

    public double getHunterPayout(Item item) {
        return HUNTER_KILLS.getOrDefault(item, 0.0);
    }

    public double getFisherPayout(Item item) {
        return FISHER_ITEMS.getOrDefault(item, 0.0);
    }

    /** Check if the player is under their daily cap, then pay them */
    public double tryPay(String uuid, Job job, double amount) {
        String today = java.time.LocalDate.now().toString();
        double cap = DAILY_CAPS.getOrDefault(job, 100.0);

        try (Connection conn = UnitedCraft.db.getConnection()) {
            // Get today's earnings
            PreparedStatement get = conn.prepareStatement(
                "SELECT earned FROM job_daily_earnings WHERE uuid = ? AND date = ?");
            get.setString(1, uuid); get.setString(2, today);
            ResultSet rs = get.executeQuery();
            double earned = rs.next() ? rs.getDouble("earned") : 0.0;

            if (earned >= cap) return 0.0; // capped

            double pay = Math.min(amount, cap - earned);

            // Upsert daily earnings
            PreparedStatement ups = conn.prepareStatement("""
                INSERT INTO job_daily_earnings (uuid, job, date, earned)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid, date) DO UPDATE SET earned = earned + ?
            """);
            ups.setString(1, uuid); ups.setString(2, job.name());
            ups.setString(3, today); ups.setDouble(4, pay); ups.setDouble(5, pay);
            ups.executeUpdate();

            return pay;
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("tryPay error: {}", e.getMessage());
            return 0.0;
        }
    }

    public double getDailyCap(Job job) {
        return DAILY_CAPS.getOrDefault(job, 100.0);
    }

    public double getDailyEarned(String uuid) {
        String today = java.time.LocalDate.now().toString();
        try (Connection conn = UnitedCraft.db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT earned FROM job_daily_earnings WHERE uuid = ? AND date = ?")) {
            ps.setString(1, uuid); ps.setString(2, today);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("earned");
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("getDailyEarned error: {}", e.getMessage());
        }
        return 0.0;
    }
}
