package net.unitedcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.unitedcraft.commands.*;
import net.unitedcraft.database.DatabaseManager;
import net.unitedcraft.managers.*;
import net.unitedcraft.listeners.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnitedCraft implements ModInitializer {

    public static final String MOD_ID = "unitedcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static DatabaseManager db;
    public static VoteManager voteManager;
    public static WarManager warManager;
    public static LandManager landManager;
    public static AllianceManager allianceManager;
    public static TreatyManager treatyManager;
    public static ChatManager chatManager;
    public static NationManager nationManager;
    public static EconomyManager economyManager;
    public static JobManager jobManager;

    // Tick counters for scheduled tasks
    private static int maintenanceTick = 0;
    private static final int MAINTENANCE_INTERVAL_TICKS = 20 * 60 * 60 * 24 * 7; // 1 week

    @Override
    public void onInitialize() {
        LOGGER.info("UnitedCraft initializing...");

        // Init database first
        db = new DatabaseManager();
        db.initialize();

        // Init managers
        nationManager   = new NationManager();
        voteManager     = new VoteManager();
        warManager      = new WarManager();
        landManager     = new LandManager();
        allianceManager = new AllianceManager();
        treatyManager   = new TreatyManager();
        chatManager     = new ChatManager();
        economyManager  = new EconomyManager();
        jobManager      = new JobManager();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            NationCommands.register(dispatcher);
            LandCommands.register(dispatcher);
            WarCommands.register(dispatcher);
            GovCommands.register(dispatcher);
            AllianceCommands.register(dispatcher);
            ChatCommands.register(dispatcher);
            EconomyCommands.register(dispatcher, registryAccess, environment);
        });

        // Register event listeners
        PlayerEventListener.register();
        BlockEventListener.register();
        JobEventListener.register();

        // Weekly land maintenance tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            maintenanceTick++;
            if (maintenanceTick >= MAINTENANCE_INTERVAL_TICKS) {
                maintenanceTick = 0;
                LOGGER.info("Running weekly land maintenance...");
                landManager.runWeeklyMaintenance();
            }
        });

        // Server stop — close DB
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            db.close();
            LOGGER.info("UnitedCraft shut down cleanly.");
        });

        LOGGER.info("UnitedCraft loaded! Nations await.");
    }
}
