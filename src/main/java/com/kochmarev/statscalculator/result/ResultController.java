package com.kochmarev.statscalculator.result;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.kochmarev.statscalculator.account.AccountPrincipal;
import com.kochmarev.statscalculator.account.ResultAccess;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/results")
public class ResultController {

    private final ResultService service;

    public ResultController(ResultService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResultResponse> submit(@Valid @RequestBody SubmitResult request,
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader(value = "X-Result-Privacy", required = false) String expectedPrivacy,
            @RequestHeader(value = "X-Result-Account", required = false) String expectedAccount) {
        var result = service.submit(request, ResultAccess.owner(principal, expectedPrivacy, expectedAccount));
        return ResponseEntity.created(URI.create("/api/v1/results/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public ResultResponse get(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return service.get(id, principal);
    }
}
