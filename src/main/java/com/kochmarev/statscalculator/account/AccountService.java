package com.kochmarev.statscalculator.account;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService implements UserDetailsService {

    private final AccountRepository repository;
    private final PasswordEncoder encoder;

    public AccountService(AccountRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPrincipal loadUserByUsername(String username) {
        return repository.findByUsername(username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password."))
                .principal();
    }

    public void register(String username, String password) {
        String normalized = username.toLowerCase(Locale.ROOT);
        try {
            repository.saveAndFlush(new Account(normalized, encoder.encode(password)));
        } catch (DataIntegrityViolationException exception) {
            if (repository.findByUsername(normalized).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is unavailable.");
            }
            throw exception;
        }
    }
}
