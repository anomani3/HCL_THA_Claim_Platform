package com.claimsplatform.claims.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One payload shape for every lifecycle action. Which fields are mandatory depends on the action
 * (enforced in the service): note for request-info / provide-info / reject, amount for assess.
 */
public record ActionRequest(
        @Schema(example = "OFFICER-01", description = "Officer id for staff actions; claimant id for provide-info / withdraw")
        @NotBlank @Size(max = 64) String actorId,
        @Size(max = 4000) String note,
        @Schema(description = "Assessed amount (assess only)", example = "4200.00") BigDecimal amount) {
}
