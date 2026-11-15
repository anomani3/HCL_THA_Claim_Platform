package com.claimsplatform.claims.web.dto;

import com.claimsplatform.claims.domain.ClaimAction;
import com.claimsplatform.claims.domain.ClaimHistory;
import com.claimsplatform.claims.domain.ClaimStatus;

import java.time.Instant;

public record HistoryResponse(
        ClaimAction action,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        String actorId,
        String note,
        Instant occurredAt) {

    public static HistoryResponse from(ClaimHistory h) {
        return new HistoryResponse(h.getAction(), h.getFromStatus(), h.getToStatus(),
                h.getActorId(), h.getNote(), h.getOccurredAt());
    }
}
