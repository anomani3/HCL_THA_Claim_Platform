package com.claimsplatform.claims.account;

import com.claimsplatform.claims.account.AccountDtos.AccountView;
import com.claimsplatform.claims.account.AccountDtos.CreateOfficerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/officers")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView createOfficer(@Valid @RequestBody CreateOfficerRequest request) {
        return accounts.createOfficer(request);
    }
}
