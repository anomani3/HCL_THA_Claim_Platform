package com.claimsplatform.claims.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AccountDtos {
    private AccountDtos() { }

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 72) String password) { }

    public record CreateOfficerRequest(
            @NotBlank @Size(max = 255) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 72) String temporaryPassword) { }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) { }

    public record AccountView(String accountId, String fullName, String email,
                              AccountRole role, Instant createdAt) {
        static AccountView from(Account account) {
            return new AccountView(account.getAccountId(), account.getFullName(), account.getEmail(),
                    account.getRole(), account.getCreatedAt());
        }
    }

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, AccountView account) { }
}
