package com.kochmarev.statscalculator.account;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ResultAccess {

    private ResultAccess() {
    }

    public static UUID owner(AccountPrincipal principal, String expectedPrivacy, String expectedAccount) {
        if (expectedPrivacy != null) {
            if (!expectedPrivacy.equals("PRIVATE") && !expectedPrivacy.equals("UNLISTED")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid result privacy expectation.");
            }
            if (expectedPrivacy.equals("PRIVATE") && principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Your session has expired. Sign in again before saving this private result.");
            }
            if (expectedPrivacy.equals("UNLISTED") && principal != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Your sign-in state changed. Reload and review the privacy notice before submitting.");
            }
        }
        if (expectedAccount != null && !expectedAccount.isEmpty() && principal != null
                && !expectedAccount.equals(principal.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your signed-in account changed. Sign back in to the original account before saving this draft.");
        }
        return principal == null ? null : principal.accountId();
    }

    public static void requireVisible(UUID ownerId, AccountPrincipal principal) {
        if (ownerId != null && (principal == null || !Objects.equals(ownerId, principal.accountId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Result not found or not accessible.");
        }
    }
}
