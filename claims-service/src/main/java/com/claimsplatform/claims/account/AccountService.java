package com.claimsplatform.claims.account;

import com.claimsplatform.claims.account.AccountDtos.AccountView;
import com.claimsplatform.claims.account.AccountDtos.AuthResponse;
import com.claimsplatform.claims.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration tokenTtl;

    public AccountService(AccountRepository accounts, PasswordEncoder passwords, JwtEncoder jwtEncoder,
                          Clock clock, @Value("${security.jwt.ttl:PT8H}") Duration tokenTtl) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
    }

    @Transactional
    public AuthResponse registerClaimant(AccountDtos.RegisterRequest request) {
        Account account = create(request.fullName(), request.email(), request.password(), AccountRole.CLAIMANT);
        return tokenFor(account);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AccountDtos.LoginRequest request) {
        Account account = accounts.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(candidate -> passwords.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Email or password is incorrect"));
        return tokenFor(account);
    }

    @Transactional(readOnly = true)
    public AccountView currentAccount(String accountId) {
        return AccountView.from(accounts.findByAccountId(accountId)
                .orElseThrow(() -> new BadCredentialsException("Account no longer exists")));
    }

    @Transactional
    public AccountView createOfficer(AccountDtos.CreateOfficerRequest request) {
        return AccountView.from(create(request.fullName(), request.email(), request.temporaryPassword(),
                AccountRole.OFFICER));
    }

    @Transactional
    public AccountView bootstrapManager(String fullName, String email, String password) {
        String normalized = normalizeEmail(email);
        return accounts.findByEmailIgnoreCase(normalized).map(existing -> {
            if (existing.getRole() != AccountRole.MANAGER) {
                throw new IllegalStateException("Bootstrap manager email belongs to a non-manager account");
            }
            return AccountView.from(existing);
        }).orElseGet(() -> AccountView.from(create(fullName, normalized, password, AccountRole.MANAGER)));
    }

    private Account create(String fullName, String email, String rawPassword, AccountRole role) {
        String normalizedEmail = normalizeEmail(email);
        if (accounts.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        String accountId = role == AccountRole.CLAIMANT
                ? "CLMT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : role.name() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return accounts.save(new Account(accountId, fullName.trim(), normalizedEmail,
                passwords.encode(rawPassword), role, clock.instant()));
    }

    private AuthResponse tokenFor(Account account) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(tokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("claims-platform")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(account.getAccountId())
                .claim("email", account.getEmail())
                .claim("name", account.getFullName())
                .claim("roles", List.of(account.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expiresAt, AccountView.from(account));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
