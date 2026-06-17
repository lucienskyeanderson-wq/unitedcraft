package net.unitedcraft.models;

import java.util.UUID;

/**
 * A personal plot is a chunk claimed by a specific player *within* their nation's territory.
 * Only that player (and the President) can build there.
 * Each rank has a different maximum number of personal plots.
 */
public class PersonalPlot {

    private String chunkKey;
    private String nationId;
    private UUID ownerUuid;
    private String ownerName;
    private long claimedAt;

    public PersonalPlot(String chunkKey, String nationId, UUID ownerUuid, String ownerName, long claimedAt) {
        this.chunkKey = chunkKey;
        this.nationId = nationId;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.claimedAt = claimedAt;
    }

    public String getChunkKey() { return chunkKey; }
    public String getNationId() { return nationId; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public long getClaimedAt() { return claimedAt; }

    /**
     * Personal plot limit per rank.
     * Presidents can claim unlimited personal plots (they own the nation).
     */
    public static int getPlotLimit(NationMember.Role role) {
        return switch (role) {
            case PRESIDENT      -> Integer.MAX_VALUE; // unlimited
            case VICE_PRESIDENT -> 16;
            case GOVERNOR       -> 12;
            case MAYOR          -> 8;
            case KNIGHT         -> 4;
            case CITIZEN        -> 2;
            case CIVILIAN       -> 1;
            case EXILE          -> 0;
        };
    }

    public static String getRoleLimitDisplay(NationMember.Role role) {
        int limit = getPlotLimit(role);
        return limit == Integer.MAX_VALUE ? "Unlimited" : String.valueOf(limit);
    }
}
