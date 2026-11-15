package com.claimsplatform.claims.web.dto;

import com.claimsplatform.claims.domain.Claim;
import com.claimsplatform.claims.domain.ClaimAction;
import com.claimsplatform.claims.domain.ClaimStateMachine;
import com.claimsplatform.claims.domain.ClaimStatus;
import com.claimsplatform.claims.domain.ClaimType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        String claimNumber,
        ClaimType claimType,
        String market,
        String currency,
        String policyNumber,
        String claimantId,
        String claimantName,
        String claimantEmail,
        LocalDate incidentDate,
        String incidentLocation,
        String assetReference,
        String description,
        BigDecimal claimedAmount,
        BigDecimal assessedAmount,
        BigDecimal paidAmount,
        ClaimStatus status,
        String assignedOfficerId,
        Set<ClaimAction> allowedActions,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {

    public static ClaimResponse from(Claim c) {
        return new ClaimResponse(c.getId(), c.getClaimNumber(), c.getClaimType(), c.getMarket(), c.getCurrency(),
                c.getPolicyNumber(), c.getClaimantId(), c.getClaimantName(), c.getClaimantEmail(),
                c.getIncidentDate(), c.getIncidentLocation(), c.getAssetReference(), c.getDescription(),
                c.getClaimedAmount(), c.getAssessedAmount(), c.getPaidAmount(), c.getStatus(),
                c.getAssignedOfficerId(), new TreeSet<>(ClaimStateMachine.allowedActions(c.getStatus())),
                c.getVersion() == null ? 0L : c.getVersion(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getClosedAt());
    }
}
