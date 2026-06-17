package net.unitedcraft.listeners;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.managers.JobManager;
import net.unitedcraft.managers.JobManager.Job;

public class JobEventListener {

    public static void register() {

        // ── Block Break (Miner, Farmer, Lumberjack) ───────────

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            if (world.isClient) return;

            String uuid = serverPlayer.getUuidAsString();
            Job job = UnitedCraft.jobManager.getJob(uuid);
            if (job == null) return;

            Block block = state.getBlock();
            double payout = 0.0;

            switch (job) {
                case MINER      -> payout = UnitedCraft.jobManager.getMinerPayout(block);
                case FARMER     -> {
                    // Only pay for fully grown crops
                    if (isFullyGrownCrop(state)) {
                        payout = UnitedCraft.jobManager.getFarmerPayout(block);
                    }
                }
                case LUMBERJACK -> payout = UnitedCraft.jobManager.getLumberjackPayout(block);
                default -> {}
            }

            if (payout > 0) {
                double actual = UnitedCraft.jobManager.tryPay(uuid, job, payout);
                if (actual > 0) {
                    UnitedCraft.economyManager.deposit(uuid, actual, "job_" + job.name().toLowerCase());
                    serverPlayer.sendMessage(Text.literal(
                        "§a+" + UnitedCraft.economyManager.format(actual) +
                        " §7[" + job.name() + "]"), true); // action bar
                } else {
                    serverPlayer.sendMessage(Text.literal(
                        "§cDaily cap reached for " + job.name() + "!"), true);
                }
            }
        });

        // ── Entity Kill (Hunter) ──────────────────────────────

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (!(entity instanceof ServerPlayerEntity serverPlayer)) return;
            if (!(killedEntity instanceof LivingEntity)) return;

            String uuid = serverPlayer.getUuidAsString();
            Job job = UnitedCraft.jobManager.getJob(uuid);
            if (job != Job.HUNTER) return;

            // Pay based on drops from the kill
            double totalPayout = 0.0;
            for (ItemStack drop : killedEntity.getArmorItems()) {
                double p = UnitedCraft.jobManager.getHunterPayout(drop.getItem());
                totalPayout += p;
            }

            // Use a fixed payout per mob type as fallback
            double mobPayout = getMobPayout(killedEntity);
            if (mobPayout > 0) totalPayout = mobPayout;

            if (totalPayout > 0) {
                double actual = UnitedCraft.jobManager.tryPay(uuid, job, totalPayout);
                if (actual > 0) {
                    UnitedCraft.economyManager.deposit(uuid, actual, "job_hunter");
                    serverPlayer.sendMessage(Text.literal(
                        "§a+" + UnitedCraft.economyManager.format(actual) +
                        " §7[HUNTER]"), true);
                }
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────

    private static boolean isFullyGrownCrop(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.WHEAT)     return state.get(net.minecraft.block.CropBlock.AGE) == 7;
        if (block == Blocks.POTATOES)  return state.get(net.minecraft.block.CropBlock.AGE) == 7;
        if (block == Blocks.CARROTS)   return state.get(net.minecraft.block.CropBlock.AGE) == 7;
        if (block == Blocks.BEETROOTS) return state.get(net.minecraft.block.BeetrootsBlock.AGE) == 3;
        if (block == Blocks.PUMPKIN)   return true;
        if (block == Blocks.MELON)     return true;
        if (block == Blocks.SUGAR_CANE) return true;
        if (block == Blocks.CACTUS)    return true;
        return false;
    }

    private static double getMobPayout(LivingEntity entity) {
        String type = entity.getType().toString();
        return switch (type) {
            case "entity.minecraft.cow"         -> 3.0;
            case "entity.minecraft.pig"         -> 2.5;
            case "entity.minecraft.chicken"     -> 2.0;
            case "entity.minecraft.sheep"       -> 2.0;
            case "entity.minecraft.rabbit"      -> 2.0;
            case "entity.minecraft.zombie"      -> 1.5;
            case "entity.minecraft.skeleton"    -> 2.0;
            case "entity.minecraft.creeper"     -> 3.0;
            case "entity.minecraft.spider"      -> 2.0;
            case "entity.minecraft.enderman"    -> 8.0;
            case "entity.minecraft.blaze"       -> 10.0;
            case "entity.minecraft.wither_skeleton" -> 15.0;
            case "entity.minecraft.elder_guardian"  -> 25.0;
            default -> 0.0;
        };
    }
}
