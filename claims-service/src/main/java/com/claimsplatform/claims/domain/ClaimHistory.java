package com.claimsplatform.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit trail of every lifecycle step (also carries info requests / responses / decisions). */
@Entity
@Table(name = "claim_history")
public class ClaimHistory {

    @Id
    private UUID id;

    private UUID claimId;

    @Enumerated(EnumType.STRING)
    private ClaimAction action;

    @Enumerated(EnumType.STRING)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private ClaimStatus toStatus;

    private String actorId;

    @Column(length = 4000)
    private String note;

    private Instant occurredAt;

    protected ClaimHistory() {
    }

    public ClaimHistory(UUID claimId, ClaimAction action, ClaimStatus fromStatus, ClaimStatus toStatus,
                        String actorId, String note, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public ClaimAction getAction() { return action; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public String getActorId() { return actorId; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}
