package com.claimsplatform.claims.repo;

import com.claimsplatform.claims.domain.ClaimHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimHistoryRepository extends JpaRepository<ClaimHistory, UUID> {
    List<ClaimHistory> findByClaimIdOrderByOccurredAtAsc(UUID claimId);
}
