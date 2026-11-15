package com.claimsplatform.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    private UUID id;

    private String claimNumber;

    @Enumerated(EnumType.STRING)
    private ClaimType claimType;

    private String market;
    private String currency;
    private String policyNumber;
    private String claimantId;
    private String claimantName;
    private String claimantEmail;
    private LocalDate incidentDate;
    private String incidentLocation;
    private String assetReference;

    @Column(length = 4000)
    private String description;

    private BigDecimal claimedAmount;
    private BigDecimal assessedAmount;
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    private ClaimStatus status;

    private String assignedOfficerId;

    /** Optimistic lock: two officers racing for the same claim -> one gets 409. */
    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    protected Claim() {
    }

    public Claim(UUID id, String claimNumber, ClaimType claimType, String market, String currency,
                 String policyNumber, String claimantId, String claimantName, String claimantEmail,
                 LocalDate incidentDate, String incidentLocation, String assetReference,
                 String description, BigDecimal claimedAmount, Instant now) {
        this.id = id;
        this.claimNumber = claimNumber;
        this.claimType = claimType;
        this.market = market;
        this.currency = currency;
        this.policyNumber = policyNumber;
        this.claimantId = claimantId;
        this.claimantName = claimantName;
        this.claimantEmail = claimantEmail;
        this.incidentDate = incidentDate;
        this.incidentLocation = incidentLocation;
        this.assetReference = assetReference;
        this.description = description;
        this.claimedAmount = claimedAmount;
        this.status = ClaimStatus.SUBMITTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ---- behaviour ----

    public void moveTo(ClaimStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
        if (next.isTerminal()) {
            this.closedAt = now;
        }
    }

    public void assignTo(String officerId) {
        this.assignedOfficerId = officerId;
    }

    public void assess(BigDecimal amount) {
        this.assessedAmount = amount;
    }

    public void settle() {
        this.paidAmount = this.assessedAmount;
    }

    /**
     * Liability the insurer still carries on this claim: the assessed amount once known,
     * otherwise the claimed amount. Zero once the claim is closed.
     */
    public BigDecimal outstandingExposure() {
        if (status.isTerminal()) {
            return BigDecimal.ZERO;
        }
        return assessedAmount != null ? assessedAmount : claimedAmount;
    }

    // ---- getters ----

    public UUID getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public ClaimType getClaimType() { return claimType; }
    public String getMarket() { return market; }
    public String getCurrency() { return currency; }
    public String getPolicyNumber() { return policyNumber; }
    public String getClaimantId() { return claimantId; }
    public String getClaimantName() { return claimantName; }
    public String getClaimantEmail() { return claimantEmail; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getIncidentLocation() { return incidentLocation; }
    public String getAssetReference() { return assetReference; }
    public String getDescription() { return description; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public BigDecimal getAssessedAmount() { return assessedAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public ClaimStatus getStatus() { return status; }
    public String getAssignedOfficerId() { return assignedOfficerId; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
}
