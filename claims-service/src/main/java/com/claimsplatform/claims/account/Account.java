package com.claimsplatform.claims.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true, length = 64)
    private String accountId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() { }

    public Account(String accountId, String fullName, String email, String passwordHash,
                   AccountRole role, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
