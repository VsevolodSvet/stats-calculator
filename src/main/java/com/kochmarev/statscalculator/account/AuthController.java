package com.kochmarev.statscalculator.account;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/csrf")
    public Token csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return new Token(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/me")
    public Session me(@AuthenticationPrincipal AccountPrincipal principal) {
        return new Session(principal != null, principal == null ? null : principal.getUsername());
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody Registration registration) {
        accounts.register(registration.username(), registration.password());
    }

    public record Registration(
            @NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,30}",
                    message = "Use 3-30 letters, numbers or underscores.") String username,
            @NotNull @Size(min = 12, max = 128, message = "Use a password of 12-128 characters.") String password) {
    }

    public record Session(boolean authenticated, String username) {
    }

    public record Token(String headerName, String token) {
    }
}
