package net.unitedcraft.models;

public class Nation {

    public enum GovernmentType {
        MONARCHY, DEMOCRACY, REPUBLIC
    }

    // ── Land Economy Constants ────────────────────────────────
    /** Cost to claim one chunk */
    public static final double CLAIM_COST = 50.0;
    /** Weekly maintenance cost per chunk */
    public static final double CHUNK_MAINTENANCE = 5.0;
    /** If treasury falls below this multiplier * claims, chunks become overclaim-able */
    public static final double OVERCLAIM_THRESHOLD_MULTIPLIER = 0.5;

    private String id;
    private String name;
    private GovernmentType governmentType;
    private String capitalChunk;
    private int power;
    private double balance;
    private double taxRate;
    private boolean pvpEnabled;
    private boolean openRecruitment;
    private boolean visitorBuild;
    private long createdAt;
    private int discontentment;
    private String description;

    public Nation(String id, String name, GovernmentType governmentType, long createdAt) {
        this.id = id;
        this.name = name;
        this.governmentType = governmentType;
        this.createdAt = createdAt;
        this.power = 0;
        this.balance = 0.0;
        this.taxRate = 0.05;
        this.pvpEnabled = false;
        this.openRecruitment = true;
        this.visitorBuild = false;
        this.discontentment = 0;
        this.description = "";
    }

    // ── Land Economy Helpers ──────────────────────────────────

    /** Returns true if the nation can afford to claim another chunk */
    public boolean canAffordClaim(int currentClaimCount) {
        double requiredBalance = CLAIM_COST + (currentClaimCount * CHUNK_MAINTENANCE);
        return balance >= requiredBalance;
    }

    /**
     * Returns true if this nation's treasury is too low to protect its land.
     * Chunks become overclaim-able by enemies when this is true.
     */
    public boolean isInsolvent(int currentClaimCount) {
        if (currentClaimCount == 0) return false;
        double maintenanceDue = currentClaimCount * CHUNK_MAINTENANCE;
        return balance < (maintenanceDue * OVERCLAIM_THRESHOLD_MULTIPLIER);
    }

    /** Weekly maintenance deduction — call from a scheduled task */
    public double calculateWeeklyMaintenance(int claimCount) {
        return claimCount * CHUNK_MAINTENANCE;
    }

    // ── Getters ───────────────────────────────────────────────
    public String getId() { return id; }
    public String getName() { return name; }
    public GovernmentType getGovernmentType() { return governmentType; }
    public String getCapitalChunk() { return capitalChunk; }
    public int getPower() { return power; }
    public double getBalance() { return balance; }
    public double getTaxRate() { return taxRate; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public boolean isOpenRecruitment() { return openRecruitment; }
    public boolean isVisitorBuild() { return visitorBuild; }
    public long getCreatedAt() { return createdAt; }
    public int getDiscontentment() { return discontentment; }
    public String getDescription() { return description; }

    // ── Setters ───────────────────────────────────────────────
    public void setCapitalChunk(String capitalChunk) { this.capitalChunk = capitalChunk; }
    public void setPower(int power) { this.power = power; }
    public void setBalance(double balance) { this.balance = Math.max(0.0, balance); }
    public void addBalance(double amount) { this.balance += amount; }
    public void setTaxRate(double taxRate) { this.taxRate = taxRate; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public void setOpenRecruitment(boolean openRecruitment) { this.openRecruitment = openRecruitment; }
    public void setVisitorBuild(boolean visitorBuild) { this.visitorBuild = visitorBuild; }
    public void setDiscontentment(int discontentment) { this.discontentment = discontentment; }
    public void addDiscontentment(int amount) { this.discontentment = Math.min(100, this.discontentment + amount); }
    public void setDescription(String description) { this.description = description; }
}
