package com.claimsplatform.reporting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side copy of the event contract. Deliberately NOT a shared class/jar with claims-service:
 * the two services are coupled only by the JSON schema, and unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimEventDto(
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
}
