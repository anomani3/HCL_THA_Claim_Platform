package com.claimsplatform.claims.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapManager implements ApplicationRunner {
    private final AccountService accounts;
    private final String email;
    private final String password;
    private final String name;

    public BootstrapManager(AccountService accounts,
                            @Value("${security.bootstrap-manager.email:}") String email,
                            @Value("${security.bootstrap-manager.password:}") String password,
                            @Value("${security.bootstrap-manager.name:Platform Manager}") String name) {
        this.accounts = accounts;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!email.isBlank() && !password.isBlank()) accounts.bootstrapManager(name, email, password);
    }
}
