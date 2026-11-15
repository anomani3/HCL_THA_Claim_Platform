package com.claimsplatform.claims.repo;

import com.claimsplatform.claims.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {

    Optional<Claim> findByClaimNumber(String claimNumber);

    @Query(value = "select nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimSequence();

    /**
     * Atomic "pick up next": row-lock the oldest submitted claim and skip rows other officers
     * have already locked, so two officers never receive the same claim.
     */
    @Query(value = "select * from claims where status = 'SUBMITTED' "
            + "order by created_at asc limit 1 for update skip locked", nativeQuery = true)
    Optional<Claim> lockNextSubmitted();

    @Query(value = "select * from claims where status = 'SUBMITTED' and market = :market "
            + "order by created_at asc limit 1 for update skip locked", nativeQuery = true)
    Optional<Claim> lockNextSubmittedInMarket(@Param("market") String market);
}
