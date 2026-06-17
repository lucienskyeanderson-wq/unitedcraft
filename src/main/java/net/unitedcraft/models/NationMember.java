package net.unitedcraft.models;

import java.util.UUID;

public class NationMember {

    public enum Role {
        PRESIDENT,      // Top leader of the nation
        VICE_PRESIDENT, // Second in command
        GOVERNOR,       // Regional leader
        MAYOR,          // City/town leader
        KNIGHT,         // Trusted military rank
        CITIZEN,        // Standard member
        CIVILIAN,       // New/unverified member, limited perms
        EXILE           // Banned from nation
    }

    private UUID playerUuid;
    private String playerName;
    private String nationId;
    private Role role;
    private long joinedAt;

    public NationMember(UUID playerUuid, String playerName, String nationId, Role role, long joinedAt) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.nationId = nationId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getNationId() { return nationId; }
    public Role getRole() { return role; }
    public long getJoinedAt() { return joinedAt; }

    public void setRole(Role role) { this.role = role; }
    public void setNationId(String nationId) { this.nationId = nationId; }

    /** Returns true if this member's role is equal to or higher than the minimum required */
    public boolean hasPermission(Role minimumRole) {
        return this.role.ordinal() <= minimumRole.ordinal();
    }

    public boolean isPresident() { return role == Role.PRESIDENT; }

    /** Leadership = President, Vice President, Governor, Mayor */
    public boolean isLeadership() {
        return role == Role.PRESIDENT || role == Role.VICE_PRESIDENT
            || role == Role.GOVERNOR || role == Role.MAYOR;
    }

    /** Senior leadership = President or Vice President */
    public boolean isSeniorLeadership() {
        return role == Role.PRESIDENT || role == Role.VICE_PRESIDENT;
    }

    public String getRoleDisplay() {
        return switch (role) {
            case PRESIDENT      -> "§6President";
            case VICE_PRESIDENT -> "§6Vice President";
            case GOVERNOR       -> "§eGovernor";
            case MAYOR          -> "§eMayor";
            case KNIGHT         -> "§bKnight";
            case CITIZEN        -> "§aCitizen";
            case CIVILIAN       -> "§7Civilian";
            case EXILE          -> "§cExile";
        };
    }
}
