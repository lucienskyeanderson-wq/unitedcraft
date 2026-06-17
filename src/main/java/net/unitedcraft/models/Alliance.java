package net.unitedcraft.models;

public class Alliance {

    public enum Status {
        PENDING,   // Sent, awaiting acceptance
        ACTIVE,    // Both nations accepted
        DISSOLVED  // Ended
    }

    private String id;
    private String nationAId;
    private String nationBId;
    private Status status;
    private long createdAt;
    private long respondedAt;

    public Alliance(String id, String nationAId, String nationBId, Status status, long createdAt) {
        this.id = id;
        this.nationAId = nationAId;
        this.nationBId = nationBId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getNationAId() { return nationAId; }
    public String getNationBId() { return nationBId; }
    public Status getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public long getRespondedAt() { return respondedAt; }

    public void setStatus(Status status) { this.status = status; }
    public void setRespondedAt(long t) { this.respondedAt = t; }

    public boolean involves(String nationId) {
        return nationAId.equals(nationId) || nationBId.equals(nationId);
    }

    public String getOtherNation(String nationId) {
        return nationAId.equals(nationId) ? nationBId : nationAId;
    }
}
