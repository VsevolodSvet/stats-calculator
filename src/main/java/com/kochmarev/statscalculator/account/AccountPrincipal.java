package com.kochmarev.statscalculator.account;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AccountPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final String username;
    private String passwordHash;

    AccountPrincipal(UUID accountId, String username, String passwordHash) {
        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public UUID accountId() {
        return accountId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AccountPrincipal principal && accountId.equals(principal.accountId);
    }

    @Override
    public int hashCode() {
        return accountId.hashCode();
    }
}
