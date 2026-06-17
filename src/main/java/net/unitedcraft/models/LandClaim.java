package net.unitedcraft.models;

public class LandClaim {

    public enum ClaimType {
        STANDARD,   // Normal claimed chunk
        CAPITAL,    // Capital city — highest protection, cannot be overclaimed
        BORDER,     // Outer border — first to be overclaimed
        OUTPOST     // Remote chunk — can be overclaimed
    }

    private String chunkKey; // "world,chunkX,chunkZ"
    private String nationId;
    private ClaimType claimType;
    private long claimedAt;

    public LandClaim(String chunkKey, String nationId, ClaimType claimType, long claimedAt) {
        this.chunkKey = chunkKey;
        this.nationId = nationId;
        this.claimType = claimType;
        this.claimedAt = claimedAt;
    }

    public String getChunkKey() { return chunkKey; }
    public String getNationId() { return nationId; }
    public ClaimType getClaimType() { return claimType; }
    public long getClaimedAt() { return claimedAt; }

    public void setClaimType(ClaimType claimType) { this.claimType = claimType; }

    /** CAPITAL chunks can never be overclaimed */
    public boolean isOverclaimable() {
        return claimType != ClaimType.CAPITAL;
    }

    public static String toChunkKey(String world, int chunkX, int chunkZ) {
        return world + "," + chunkX + "," + chunkZ;
    }
}
