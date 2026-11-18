package com.claimsplatform.claims.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmailIgnoreCase(String email);
    Optional<Account> findByAccountId(String accountId);
    boolean existsByEmailIgnoreCase(String email);
}
