package com.kochmarev.statscalculator.account;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {

    private final UUID accountId;

    AccountPrincipal(UUID accountId, String username, String passwordHash) {
        super(username, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
