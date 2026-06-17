package net.unitedcraft.models;

import java.util.UUID;

/** Represents a nation chat message (stored in memory only, not persisted) */
public class NationChat {
    private UUID senderUuid;
    private String senderName;
    private String nationId;
    private String message;
    private long sentAt;

    public NationChat(UUID senderUuid, String senderName, String nationId, String message) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.nationId = nationId;
        this.message = message;
        this.sentAt = System.currentTimeMillis();
    }

    public UUID getSenderUuid() { return senderUuid; }
    public String getSenderName() { return senderName; }
    public String getNationId() { return nationId; }
    public String getMessage() { return message; }
    public long getSentAt() { return sentAt; }
}
