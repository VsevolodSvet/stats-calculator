package com.kochmarev.statscalculator.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
class Account {

    @Id
    private UUID id;
    private String username;
    private String passwordHash;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // Required by JPA.
    }

    Account(String username, String passwordHash) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    AccountPrincipal principal() {
        return new AccountPrincipal(id, username, passwordHash);
    }
}
