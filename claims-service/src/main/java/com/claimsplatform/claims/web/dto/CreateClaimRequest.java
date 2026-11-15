package com.claimsplatform.claims.web.dto;

import com.claimsplatform.claims.domain.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateClaimRequest(
        @NotNull ClaimType claimType,
        @Schema(example = "SG", description = "Market code: SG, HK, AU, TH, MY or ID")
        @NotBlank @Pattern(regexp = "SG|HK|AU|TH|MY|ID", message = "must be one of SG, HK, AU, TH, MY, ID") String market,
        @Schema(example = "SGD") @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency") String currency,
        @Schema(example = "POL-MTR-889012") @NotBlank @Size(max = 50) String policyNumber,
        @Schema(example = "CLMT-1001") @NotBlank @Size(max = 64) String claimantId,
        @NotBlank @Size(max = 255) String claimantName,
        @NotBlank @Email @Size(max = 255) String claimantEmail,
        @NotNull LocalDate incidentDate,
        @Size(max = 255) String incidentLocation,
        @Schema(description = "Vehicle registration (MOTOR) or property address (PROPERTY)") @Size(max = 255) String assetReference,
        @NotBlank @Size(max = 4000) String description,
        @Schema(example = "4500.00") @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal claimedAmount) {
}
