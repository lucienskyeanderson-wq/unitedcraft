package net.unitedcraft.managers;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.*;
import net.unitedcraft.repository.NationRepository;

import java.sql.*;
import java.util.*;

public class LandManager {

    private final NationRepository repo = new NationRepository();
    private final NationManager nationManager = new NationManager();

    // ═══════════════════════════════════════════════════════════
    //  NATION LAND CLAIMING
    // ═══════════════════════════════════════════════════════════

    /**
     * Claim the chunk the player is standing on for their nation.
     * Costs Nation.CLAIM_COST from the nation treasury.
     */
    public void claimChunk(ServerPlayerEntity player, LandClaim.ClaimType type) throws Exception {
        Optional<Nation> nationOpt = nationManager.getNationOfPlayer(player.getUuid());
        if (nationOpt.isEmpty()) throw new Exception("You are not in a nation.");

        Optional<NationMember> memberOpt = nationManager.getMember(player.getUuid());
        if (memberOpt.isEmpty() || !memberOpt.get().isLeadership()) {
            throw new Exception("Only nation leadership (Mayor+) can claim land.");
        }

        Nation nation = nationOpt.get();
        String chunkKey = getChunkKey(player);

        // Already claimed?
        Optional<LandClaim> existing = repo.getClaim(chunkKey);
        if (existing.isPresent()) {
            String ownerName = nationManager.getNationById(existing.get().getNationId())
                .map(Nation::getName).orElse("another nation");
            throw new Exception("This chunk is already claimed by " + ownerName + ".");
        }

        int currentClaims = repo.getClaimCount(nation.getId());

        // Check nation can afford it
        if (!nation.canAffordClaim(currentClaims)) {
            double needed = Nation.CLAIM_COST + (currentClaims * Nation.CHUNK_MAINTENANCE);
            throw new Exception(String.format(
                "Insufficient nation treasury! Need $%.2f, have $%.2f. " +
                "Deposit with §e/nation deposit <amount>§c.",
                needed, nation.getBalance()
            ));
        }

        // Capital chunk — only one allowed
        if (type == LandClaim.ClaimType.CAPITAL) {
            if (nation.getCapitalChunk() != null) {
                throw new Exception("Your nation already has a capital chunk. Unclaim it first.");
            }
            if (!memberOpt.get().isSeniorLeadership()) {
                throw new Exception("Only the President or Vice President can set a capital chunk.");
            }
            nation.setCapitalChunk(chunkKey);
        }

        // Deduct cost from treasury
        nation.setBalance(nation.getBalance() - Nation.CLAIM_COST);
        repo.saveNation(nation);

        LandClaim claim = new LandClaim(chunkKey, nation.getId(), type, System.currentTimeMillis());
        repo.saveClaim(claim);

        player.sendMessage(Text.literal(String.format(
            "§aChunk claimed as §e%s§a! §7(-$%.2f | Treasury: $%.2f)",
            type.name(), Nation.CLAIM_COST, nation.getBalance()
        )));
    }

    /**
     * Overclaim an enemy nation's BORDER or OUTPOST chunk.
     * Only possible if the enemy nation is insolvent (treasury too low).
     * Costs Nation.CLAIM_COST from the claimer's treasury.
     */
    public void overclaim(ServerPlayerEntity player) throws Exception {
        Optional<Nation> attackerNationOpt = nationManager.getNationOfPlayer(player.getUuid());
        if (attackerNationOpt.isEmpty()) throw new Exception("You are not in a nation.");

        Optional<NationMember> memberOpt = nationManager.getMember(player.getUuid());
        if (memberOpt.isEmpty() || !memberOpt.get().isLeadership()) {
            throw new Exception("Only leadership can overclaim land.");
        }

        String chunkKey = getChunkKey(player);
        Optional<LandClaim> existing = repo.getClaim(chunkKey);
        if (existing.isEmpty()) throw new Exception("This chunk is not claimed. Use §e/land claim§c to claim it normally.");

        LandClaim claim = existing.get();
        Nation attackerNation = attackerNationOpt.get();

        // Can't overclaim your own nation
        if (claim.getNationId().equals(attackerNation.getId())) {
            throw new Exception("You can't overclaim your own nation's land.");
        }

        // Capital is unclaim-able
        if (!claim.isOverclaimable()) {
            throw new Exception("Capital chunks cannot be overclaimed.");
        }

        // Check defender is insolvent
        Optional<Nation> defenderOpt = nationManager.getNationById(claim.getNationId());
        if (defenderOpt.isEmpty()) throw new Exception("Could not find owning nation.");
        Nation defender = defenderOpt.get();

        int defenderClaims = repo.getClaimCount(defender.getId());
        if (!defender.isInsolvent(defenderClaims)) {
            double maintenanceDue = defenderClaims * Nation.CHUNK_MAINTENANCE;
            double threshold = maintenanceDue * Nation.OVERCLAIM_THRESHOLD_MULTIPLIER;
            throw new Exception(String.format(
                "§e%s §cis not insolvent. Their treasury ($%.2f) must fall below $%.2f before you can overclaim.",
                defender.getName(), defender.getBalance(), threshold
            ));
        }

        // Attacker must be able to afford it
        int attackerClaims = repo.getClaimCount(attackerNation.getId());
        if (!attackerNation.canAffordClaim(attackerClaims)) {
            throw new Exception(String.format(
                "Your nation cannot afford to overclaim! Need $%.2f, have $%.2f.",
                Nation.CLAIM_COST + (attackerClaims * Nation.CHUNK_MAINTENANCE),
                attackerNation.getBalance()
            ));
        }

        // Remove any personal plots in this chunk from the defender
        deletePersonalPlotsInChunk(chunkKey);

        // Remove build permissions tied to this chunk
        deleteLandPermissionsInChunk(chunkKey);

        // Transfer the claim
        attackerNation.setBalance(attackerNation.getBalance() - Nation.CLAIM_COST);
        repo.saveNation(attackerNation);

        LandClaim newClaim = new LandClaim(chunkKey, attackerNation.getId(), LandClaim.ClaimType.BORDER, System.currentTimeMillis());
        repo.saveClaim(newClaim);

        player.sendMessage(Text.literal(String.format(
            "§aOverclaimed chunk from §e%s§a! §7(-$%.2f | Treasury: $%.2f)",
            defender.getName(), Nation.CLAIM_COST, attackerNation.getBalance()
        )));

        // Notify defender's online members
        nationManager.getMembers(defender.getId()).forEach(m -> {
            var p = player.getServer().getPlayerManager().getPlayer(m.getPlayerUuid());
            if (p != null) p.sendMessage(Text.literal(
                "§c⚠ §e" + attackerNation.getName() + " §chas overclaimed a chunk from your nation! " +
                "Deposit to your treasury to prevent further losses: §e/nation deposit <amount>"
            ));
        });
    }

    public void unclaimChunk(ServerPlayerEntity player) throws Exception {
        Optional<Nation> nationOpt = nationManager.getNationOfPlayer(player.getUuid());
        if (nationOpt.isEmpty()) throw new Exception("You are not in a nation.");

        Optional<NationMember> memberOpt = nationManager.getMember(player.getUuid());
        if (memberOpt.isEmpty() || !memberOpt.get().isLeadership()) {
            throw new Exception("Only leadership can unclaim land.");
        }

        String chunkKey = getChunkKey(player);
        Optional<LandClaim> claim = repo.getClaim(chunkKey);
        if (claim.isEmpty()) throw new Exception("This chunk is not claimed.");
        if (!claim.get().getNationId().equals(nationOpt.get().getId())) {
            throw new Exception("You can only unclaim your own nation's land.");
        }

        // Clear capital reference if needed
        if (claim.get().getClaimType() == LandClaim.ClaimType.CAPITAL) {
            Nation nation = nationOpt.get();
            nation.setCapitalChunk(null);
            repo.saveNation(nation);
        }

        // Remove personal plots and permissions in this chunk
        deletePersonalPlotsInChunk(chunkKey);
        deleteLandPermissionsInChunk(chunkKey);

        repo.deleteClaim(chunkKey);
        player.sendMessage(Text.literal("§aChunk unclaimed."));
    }

    // ═══════════════════════════════════════════════════════════
    //  PERSONAL PLOTS
    // ═══════════════════════════════════════════════════════════

    /**
     * Claim a personal plot within your nation's territory.
     * Rank determines how many personal plots you can have.
     */
    public void claimPersonalPlot(ServerPlayerEntity player) throws Exception {
        Optional<NationMember> memberOpt = nationManager.getMember(player.getUuid());
        if (memberOpt.isEmpty() || memberOpt.get().getNationId() == null) {
            throw new Exception("You are not in a nation.");
        }

        NationMember member = memberOpt.get();
        String chunkKey = getChunkKey(player);

        // Must be inside their nation's claimed territory
        Optional<LandClaim> nationClaim = repo.getClaim(chunkKey);
        if (nationClaim.isEmpty() || !nationClaim.get().getNationId().equals(member.getNationId())) {
            throw new Exception("You can only claim personal plots within your nation's territory.");
        }

        // Check if already a personal plot
        if (getPersonalPlot(chunkKey).isPresent()) {
            throw new Exception("This chunk already has a personal plot.");
        }

        // Exile can't claim plots
        if (member.getRole() == NationMember.Role.EXILE) {
            throw new Exception("Exiles cannot claim personal plots.");
        }

        // Check plot limit
        int limit = PersonalPlot.getPlotLimit(member.getRole());
        int current = countPersonalPlots(player.getUuid());
        if (current >= limit) {
            throw new Exception(String.format(
                "Personal plot limit reached! Your rank (§e%s§c) allows %s plots. You have %d.",
                member.getRole().name(),
                PersonalPlot.getRoleLimitDisplay(member.getRole()),
                current
            ));
        }

        savePersonalPlot(new PersonalPlot(
            chunkKey, member.getNationId(),
            player.getUuid(), player.getName().getString(),
            System.currentTimeMillis()
        ));

        player.sendMessage(Text.literal(String.format(
            "§aPersonal plot claimed! (§e%d§a/§e%s§a used)",
            current + 1, PersonalPlot.getRoleLimitDisplay(member.getRole())
        )));
    }

    public void unclaimPersonalPlot(ServerPlayerEntity player) throws Exception {
        String chunkKey = getChunkKey(player);
        Optional<PersonalPlot> plot = getPersonalPlot(chunkKey);

        if (plot.isEmpty()) throw new Exception("No personal plot here.");

        // Must be owner or President
        boolean isOwner = plot.get().getOwnerUuid().equals(player.getUuid());
        boolean isPresident = nationManager.getMember(player.getUuid())
            .map(NationMember::isPresident).orElse(false);

        if (!isOwner && !isPresident) {
            throw new Exception("Only the plot owner or the President can unclaim this plot.");
        }

        deletePersonalPlot(chunkKey);
        player.sendMessage(Text.literal("§aPersonal plot removed."));
    }

    // ═══════════════════════════════════════════════════════════
    //  LAND PERMISSIONS (President grants)
    // ═══════════════════════════════════════════════════════════

    /**
     * President grants a specific player build permission on the current chunk.
     */
    public void grantBuildPermission(ServerPlayerEntity president, UUID targetUuid, String targetName) throws Exception {
        Optional<NationMember> presidentMember = nationManager.getMember(president.getUuid());
        if (presidentMember.isEmpty() || !presidentMember.get().isPresident()) {
            throw new Exception("Only the President can grant build permissions.");
        }

        String chunkKey = getChunkKey(president);
        Optional<LandClaim> claim = repo.getClaim(chunkKey);
        if (claim.isEmpty() || !claim.get().getNationId().equals(presidentMember.get().getNationId())) {
            throw new Exception("This chunk doesn't belong to your nation.");
        }

        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement("""
                INSERT OR REPLACE INTO land_permissions (chunk_key, player_uuid, granted_by, granted_at)
                VALUES (?,?,?,?)
                """)) {
            ps.setString(1, chunkKey);
            ps.setString(2, targetUuid.toString());
            ps.setString(3, president.getUuid().toString());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
        president.sendMessage(Text.literal("§aBuild permission granted to §e" + targetName + " §afor this chunk."));
    }

    /**
     * President revokes build permission from a player on the current chunk.
     */
    public void revokeBuildPermission(ServerPlayerEntity president, UUID targetUuid, String targetName) throws Exception {
        Optional<NationMember> presidentMember = nationManager.getMember(president.getUuid());
        if (presidentMember.isEmpty() || !presidentMember.get().isPresident()) {
            throw new Exception("Only the President can revoke build permissions.");
        }

        String chunkKey = getChunkKey(president);
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "DELETE FROM land_permissions WHERE chunk_key = ? AND player_uuid = ?")) {
            ps.setString(1, chunkKey);
            ps.setString(2, targetUuid.toString());
            ps.executeUpdate();
        }
        president.sendMessage(Text.literal("§cBuild permission revoked from §e" + targetName + "§c for this chunk."));
    }

    public boolean hasExplicitPermission(UUID playerUuid, String chunkKey) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT 1 FROM land_permissions WHERE chunk_key = ? AND player_uuid = ?")) {
            ps.setString(1, chunkKey);
            ps.setString(2, playerUuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error checking land permission", e);
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════
    //  BUILD / INTERACT PERMISSION CHECK
    // ═══════════════════════════════════════════════════════════

    /**
     * Master permission check.
     * Returns true if the player is allowed to build/interact in the chunk.
     *
     * Priority order:
     * 1. Wilderness (unclaimed) → anyone can build
     * 2. President of the owning nation → always allowed
     * 3. Personal plot owner → allowed in their own plot
     * 4. Nation member → allowed in all national land (not personal plots of others)
     * 5. Explicit President-granted permission → allowed
     * 6. Visitor build enabled on the nation → allowed
     * 7. Otherwise → blocked
     */
    public boolean canBuild(ServerPlayerEntity player, String chunkKey) {
        Optional<LandClaim> claim = repo.getClaim(chunkKey);
        if (claim.isEmpty()) return true; // Wilderness

        String claimingNationId = claim.get().getNationId();
        Optional<NationMember> member = nationManager.getMember(player.getUuid());

        // ── 1. President bypass ───────────────────────────────
        if (member.isPresent() && member.get().isPresident() &&
            claimingNationId.equals(member.get().getNationId())) {
            return true;
        }

        // ── 2. Personal plot check ────────────────────────────
        Optional<PersonalPlot> plot = getPersonalPlot(chunkKey);
        if (plot.isPresent()) {
            // Personal plot exists — only owner or President can build here
            return plot.get().getOwnerUuid().equals(player.getUuid());
        }

        // ── 3. Own nation member ──────────────────────────────
        if (member.isPresent() && claimingNationId.equals(member.get().getNationId())) {
            // Exiles cannot build
            return member.get().getRole() != NationMember.Role.EXILE;
        }

        // ── 4. Explicit President-granted permission ──────────
        if (hasExplicitPermission(player.getUuid(), chunkKey)) return true;

        // ── 5. Visitor build enabled ──────────────────────────
        Optional<Nation> nation = nationManager.getNationById(claimingNationId);
        return nation.map(Nation::isVisitorBuild).orElse(false);
    }

    // ═══════════════════════════════════════════════════════════
    //  ECONOMY — DEPOSITS / WITHDRAWALS
    // ═══════════════════════════════════════════════════════════

    /** Deposit personal money into the nation treasury */
    public void depositToNation(ServerPlayerEntity player, double amount) throws Exception {
        if (amount <= 0) throw new Exception("Amount must be positive.");

        Optional<NationMember> member = nationManager.getMember(player.getUuid());
        if (member.isEmpty() || member.get().getNationId() == null) {
            throw new Exception("You are not in a nation.");
        }

        double personalBalance = getPlayerBalance(player.getUuid());
        if (personalBalance < amount) {
            throw new Exception(String.format("Insufficient funds. You have $%.2f.", personalBalance));
        }

        Optional<Nation> nation = nationManager.getNationById(member.get().getNationId());
        if (nation.isEmpty()) throw new Exception("Nation not found.");

        setPlayerBalance(player.getUuid(), personalBalance - amount);
        nation.get().addBalance(amount);
        repo.saveNation(nation.get());

        player.sendMessage(Text.literal(String.format(
            "§aDeposited §e$%.2f §ato §e%s§a's treasury. " +
            "§7(Your balance: $%.2f | Treasury: $%.2f)",
            amount, nation.get().getName(),
            personalBalance - amount, nation.get().getBalance()
        )));
    }

    /** Withdraw from the nation treasury (leadership only) */
    public void withdrawFromNation(ServerPlayerEntity player, double amount) throws Exception {
        if (amount <= 0) throw new Exception("Amount must be positive.");

        Optional<NationMember> member = nationManager.getMember(player.getUuid());
        if (member.isEmpty() || !member.get().isSeniorLeadership()) {
            throw new Exception("Only the President or Vice President can withdraw from the treasury.");
        }

        Optional<Nation> nation = nationManager.getNationById(member.get().getNationId());
        if (nation.isEmpty()) throw new Exception("Nation not found.");

        if (nation.get().getBalance() < amount) {
            throw new Exception(String.format(
                "Insufficient treasury funds. Treasury has $%.2f.", nation.get().getBalance()
            ));
        }

        nation.get().setBalance(nation.get().getBalance() - amount);
        repo.saveNation(nation.get());
        addPlayerBalance(player.getUuid(), amount);

        player.sendMessage(Text.literal(String.format(
            "§aWithdrew §e$%.2f §afrom §e%s§a's treasury. " +
            "§7(Your balance: $%.2f | Treasury: $%.2f)",
            amount, nation.get().getName(),
            getPlayerBalance(player.getUuid()), nation.get().getBalance()
        )));
    }

    // ═══════════════════════════════════════════════════════════
    //  MAINTENANCE (called weekly by scheduler)
    // ═══════════════════════════════════════════════════════════

    /**
     * Deducts weekly maintenance from all nations.
     * Nations that can't pay lose border/outpost chunks until they can.
     */
    public void runWeeklyMaintenance() {
        for (Nation nation : nationManager.getAllNations()) {
            int claims = repo.getClaimCount(nation.getId());
            if (claims == 0) continue;

            double maintenance = nation.calculateWeeklyMaintenance(claims);

            if (nation.getBalance() >= maintenance) {
                nation.setBalance(nation.getBalance() - maintenance);
                repo.saveNation(nation);
                UnitedCraft.LOGGER.info(String.format(
                    "[Maintenance] %s paid $%.2f (treasury: $%.2f)",
                    nation.getName(), maintenance, nation.getBalance()
                ));
            } else {
                // Can't pay full — pay what they can, mark insolvent
                nation.setBalance(0.0);
                repo.saveNation(nation);
                UnitedCraft.LOGGER.info(
                    "[Maintenance] " + nation.getName() + " INSOLVENT — treasury emptied. Land at risk of overclaim!");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INFO & HELPERS
    // ═══════════════════════════════════════════════════════════

    public Optional<LandClaim> getClaimAt(ServerPlayerEntity player) {
        return repo.getClaim(getChunkKey(player));
    }

    public Optional<LandClaim> getClaimAtKey(String chunkKey) {
        return repo.getClaim(chunkKey);
    }

    public Optional<PersonalPlot> getPersonalPlotAt(ServerPlayerEntity player) {
        return getPersonalPlot(getChunkKey(player));
    }

    public List<LandClaim> getNationClaims(String nationId) {
        return repo.getClaimsByNation(nationId);
    }

    public String getChunkKey(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        int chunkX = player.getBlockPos().getX() >> 4;
        int chunkZ = player.getBlockPos().getZ() >> 4;
        return LandClaim.toChunkKey(world.getRegistryKey().getValue().toString(), chunkX, chunkZ);
    }

    // ── Personal Plot DB ──────────────────────────────────────

    private void savePersonalPlot(PersonalPlot plot) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement("""
                INSERT OR REPLACE INTO personal_plots
                (chunk_key, nation_id, owner_uuid, owner_name, claimed_at)
                VALUES (?,?,?,?,?)
                """)) {
            ps.setString(1, plot.getChunkKey());
            ps.setString(2, plot.getNationId());
            ps.setString(3, plot.getOwnerUuid().toString());
            ps.setString(4, plot.getOwnerName());
            ps.setLong(5, plot.getClaimedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error saving personal plot", e);
        }
    }

    public Optional<PersonalPlot> getPersonalPlot(String chunkKey) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT * FROM personal_plots WHERE chunk_key = ?")) {
            ps.setString(1, chunkKey);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new PersonalPlot(
                    rs.getString("chunk_key"),
                    rs.getString("nation_id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("owner_name"),
                    rs.getLong("claimed_at")
                ));
            }
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting personal plot", e);
        }
        return Optional.empty();
    }

    public int countPersonalPlots(UUID playerUuid) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM personal_plots WHERE owner_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error counting personal plots", e);
        }
        return 0;
    }

    private void deletePersonalPlot(String chunkKey) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "DELETE FROM personal_plots WHERE chunk_key = ?")) {
            ps.setString(1, chunkKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error deleting personal plot", e);
        }
    }

    private void deletePersonalPlotsInChunk(String chunkKey) {
        deletePersonalPlot(chunkKey);
    }

    private void deleteLandPermissionsInChunk(String chunkKey) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "DELETE FROM land_permissions WHERE chunk_key = ?")) {
            ps.setString(1, chunkKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error deleting land permissions", e);
        }
    }

    // ── Player Wallet DB ──────────────────────────────────────

    public double getPlayerBalance(UUID uuid) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "SELECT balance FROM player_wallets WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error getting player balance", e);
        }
        return 0.0;
    }

    public void setPlayerBalance(UUID uuid, double amount) {
        try (PreparedStatement ps = UnitedCraft.db.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO player_wallets (player_uuid, balance) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, Math.max(0.0, amount));
            ps.executeUpdate();
        } catch (SQLException e) {
            UnitedCraft.LOGGER.error("Error setting player balance", e);
        }
    }

    public void addPlayerBalance(UUID uuid, double amount) {
        setPlayerBalance(uuid, getPlayerBalance(uuid) + amount);
    }
}
