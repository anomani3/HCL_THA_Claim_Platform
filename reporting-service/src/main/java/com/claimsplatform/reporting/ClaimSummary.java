package com.claimsplatform.reporting;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Denormalised, query-optimised projection of a claim. Owned exclusively by this service. */
@Entity
@Table(name = "claim_summary")
public class ClaimSummary {

    @Id
    private UUID claimId;
    private String claimNumber;
    private String claimType;
    private String market;
    private String currency;
    private String status;
    private String assignedOfficerId;
    private BigDecimal claimedAmount;
    private BigDecimal assessedAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingExposure;
    private long version;
    private Instant submittedAt;
    private Instant closedAt;
    private Instant lastEventAt;

    protected ClaimSummary() {
    }

    public ClaimSummary(UUID claimId) {
        this.claimId = claimId;
    }

    public void applyEvent(ClaimEventDto e) {
        this.claimNumber = e.claimNumber();
        this.claimType = e.claimType();
        this.market = e.market();
        this.currency = e.currency();
        this.status = e.status();
        this.assignedOfficerId = e.assignedOfficerId();
        this.claimedAmount = e.claimedAmount();
        this.assessedAmount = e.assessedAmount();
        this.paidAmount = e.paidAmount();
        this.outstandingExposure = e.outstandingExposure();
        this.version = e.version();
        this.submittedAt = e.submittedAt();
        this.closedAt = e.closedAt();
        this.lastEventAt = e.occurredAt();
    }

    public UUID getClaimId() { return claimId; }
    public long getVersion() { return version; }
    public String getStatus() { return status; }
}
