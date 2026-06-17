package net.unitedcraft.models;

public class Treaty {

    public enum Status {
        PROPOSED,  // One side proposed
        ACCEPTED,  // Both agreed — war ends
        REJECTED   // Other side rejected
    }

    public enum Terms {
        WHITE_PEACE,       // Just end the war, no consequences
        CEDE_TERRITORY,    // Loser gives up border chunks
        PAY_REPARATIONS    // Loser pays from treasury
    }

    private String id;
    private String warId;
    private String proposerNationId;
    private String receiverNationId;
    private Terms terms;
    private Status status;
    private long proposedAt;

    public Treaty(String id, String warId, String proposerNationId, String receiverNationId, Terms terms) {
        this.id = id;
        this.warId = warId;
        this.proposerNationId = proposerNationId;
        this.receiverNationId = receiverNationId;
        this.terms = terms;
        this.status = Status.PROPOSED;
        this.proposedAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getWarId() { return warId; }
    public String getProposerNationId() { return proposerNationId; }
    public String getReceiverNationId() { return receiverNationId; }
    public Terms getTerms() { return terms; }
    public Status getStatus() { return status; }
    public long getProposedAt() { return proposedAt; }

    public void setStatus(Status status) { this.status = status; }
}
