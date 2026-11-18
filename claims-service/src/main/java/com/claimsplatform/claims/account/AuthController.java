package com.claimsplatform.claims.account;

import com.claimsplatform.claims.account.AccountDtos.AuthResponse;
import com.claimsplatform.claims.account.AccountDtos.LoginRequest;
import com.claimsplatform.claims.account.AccountDtos.RegisterRequest;
import com.claimsplatform.claims.account.AccountDtos.AccountView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;

    public AuthController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return accounts.registerClaimant(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return accounts.login(request);
    }

    @GetMapping("/me")
    public AccountView me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return accounts.currentAccount(jwt.getSubject());
    }
}
