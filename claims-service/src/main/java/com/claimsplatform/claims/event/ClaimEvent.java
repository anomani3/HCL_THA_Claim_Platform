package com.claimsplatform.claims.event;

import com.claimsplatform.claims.domain.Claim;
import com.claimsplatform.claims.domain.ClaimAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * State-carrying event published to Kafka after every committed change.
 * Consumers get the full snapshot plus a monotonically increasing {@code version}, so they can
 * upsert idempotently and ignore stale / duplicate deliveries without calling back to this service.
 */
public record ClaimEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID claimId,
        String claimNumber,
        String claimType,
        String market,
        String currency,
        String status,
        String assignedOfficerId,
        BigDecimal claimedAmount,
        BigDecimal assessedAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingExposure,
        long version,
        Instant submittedAt,
        Instant closedAt) {

    public static ClaimEvent from(Claim c, ClaimAction action, Instant now) {
        return new ClaimEvent(
                UUID.randomUUID(),
                "CLAIM_" + (action == ClaimAction.SUBMIT ? "SUBMITTED" : action.name()),
                now,
                c.getId(),
                c.getClaimNumber(),
                c.getClaimType().name(),
                c.getMarket(),
                c.getCurrency(),
                c.getStatus().name(),
                c.getAssignedOfficerId(),
                c.getClaimedAmount(),
                c.getAssessedAmount(),
                c.getPaidAmount(),
                c.outstandingExposure(),
                c.getVersion() == null ? 0L : c.getVersion(),
                c.getCreatedAt(),
                c.getClosedAt());
    }
}
