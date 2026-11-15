package com.claimsplatform.reporting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClaimSummaryRepository extends JpaRepository<ClaimSummary, UUID> {
}
