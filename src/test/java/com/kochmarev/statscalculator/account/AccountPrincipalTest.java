package com.kochmarev.statscalculator.account;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPrincipalTest {

    @Test
    void identityUsesAccountIdAndSurvivesCredentialErasure() {
        UUID id = UUID.randomUUID();
        var principal = new AccountPrincipal(id, "owner", UUID.randomUUID().toString());
        var sameAccount = new AccountPrincipal(id, "owner", UUID.randomUUID().toString());
        var differentAccount = new AccountPrincipal(UUID.randomUUID(), "owner", UUID.randomUUID().toString());

        assertThat(principal).isEqualTo(principal).isEqualTo(sameAccount).isNotEqualTo(differentAccount)
                .isNotEqualTo(null).isNotEqualTo("owner");
        assertThat(sameAccount).isEqualTo(principal).hasSameHashCodeAs(principal);
        principal.eraseCredentials();
        assertThat(principal.getPassword()).isNull();
        assertThat(principal).isEqualTo(sameAccount).hasSameHashCodeAs(sameAccount);
        assertThat(principal.accountId()).isEqualTo(id);
        assertThat(principal.getUsername()).isEqualTo("owner");
    }

    @Test
    void grantsOnlyUserAuthorityAndKeepsDefaultAccountStatus() {
        var principal = new AccountPrincipal(UUID.randomUUID(), "owner", UUID.randomUUID().toString());

        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
    }
}
