package com.kochmarev.statscalculator.practice;

import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/v1/practice")
public class PracticeController {

    private final PracticeService service;

    public PracticeController(PracticeService service) {
        this.service = service;
    }

    @GetMapping("/activities")
    public List<PracticeService.Summary> list() {
        return service.list();
    }

    @GetMapping("/activities/{id}/versions/{version}")
    public PracticeService.Detail detail(@PathVariable String id, @PathVariable int version) {
        return service.detail(id, version);
    }

    @PostMapping(value = "/results", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PracticeResult.Response> submit(@Valid @RequestBody SubmitPractice request,
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader(value = "X-Result-Privacy", required = false) String expectedPrivacy,
            @RequestHeader(value = "X-Result-Account", required = false) String expectedAccount) {
        var result = service.submit(request, ResultAccess.owner(principal, expectedPrivacy, expectedAccount));
        return ResponseEntity.created(URI.create("/api/v1/practice/results/" + result.id())).body(result);
    }

    @GetMapping("/results/{id}")
    public PracticeResult.Response result(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return service.result(id, principal);
    }
}
