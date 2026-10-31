package com.kochmarev.statscalculator.photo;

import java.io.IOException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.kochmarev.statscalculator.account.AccountPrincipal;
import com.kochmarev.statscalculator.account.ResultAccess;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/me/photo")
public class PhotoController {
    private final JdbcTemplate jdbc;
    private final IlookClient ilook;

    public PhotoController(JdbcTemplate jdbc, IlookClient ilook) {
        this.jdbc = jdbc;
        this.ilook = ilook;
    }

    @GetMapping("/ilook")
    public ProviderStatus provider(@AuthenticationPrincipal AccountPrincipal principal,
                                   @RequestHeader("X-Result-Account") String expectedAccount) {
        requireAccount(principal, expectedAccount);
        return new ProviderStatus(ilook.available());
    }

    @PostMapping(value = "/ilook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IlookClient.Report analyze(@AuthenticationPrincipal AccountPrincipal principal,
                                      @RequestHeader("X-Result-Account") String expectedAccount,
                                      @RequestBody AnalysisRequest request) {
        requireAccount(principal, expectedAccount);
        if (!request.externalConsent() || !request.adultAndAuthorized() || request.revision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirm third-party processing, adult authorization and the saved photo revision.");
        }
        byte[] image = jdbc.query("SELECT image FROM profile_photos WHERE owner_id = ? AND revision = ?",
                (row, index) -> row.getBytes("image"), principal.accountId(), request.revision()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The saved photo changed or was deleted. Reload and confirm the current photo."));
        return ilook.analyze(image);
    }

    @GetMapping
    public Metadata metadata(@AuthenticationPrincipal AccountPrincipal principal,
                             @RequestHeader("X-Result-Account") String expectedAccount) {
        requireAccount(principal, expectedAccount);
        return jdbc.query("SELECT revision, image, updated_at FROM profile_photos WHERE owner_id = ?",
                (row, index) -> new Metadata(true, row.getObject("revision", UUID.class),
                        row.getTimestamp("updated_at").toInstant(), analyzeStored(row.getBytes("image"))),
                principal.accountId()).stream().findFirst().orElse(new Metadata(false, null, null, null));
    }

    @GetMapping(value = "/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal AccountPrincipal principal,
                                        @RequestHeader("X-Result-Account") String expectedAccount) {
        requireAccount(principal, expectedAccount);
        byte[] image = jdbc.query("SELECT image FROM profile_photos WHERE owner_id = ?",
                (row, index) -> row.getBytes("image"), principal.accountId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No photo is saved for this account."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "inline; filename=\"profile.png\"")
                .contentType(MediaType.IMAGE_PNG).body(image);
    }

    @PostMapping(consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    public Metadata save(@AuthenticationPrincipal AccountPrincipal principal,
                         @RequestHeader("X-Photo-Consent") boolean consent,
                         @RequestHeader("X-Result-Account") String expectedAccount,
                         HttpServletRequest request) throws IOException {
        requireAccount(principal, expectedAccount);
        if (!consent) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit photo storage consent is required.");
        if (request.getContentLengthLong() > PhotoProcessor.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Choose an image up to 5 MiB.");
        }
        byte[] bytes = request.getInputStream().readNBytes(PhotoProcessor.MAX_BYTES + 1);
        var processed = PhotoProcessor.process(bytes);
        UUID revision = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO profile_photos (owner_id, revision, image, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (owner_id) DO UPDATE SET revision = EXCLUDED.revision,
                    image = EXCLUDED.image, updated_at = EXCLUDED.updated_at
                """, principal.accountId(), revision, processed.bytes(), Timestamp.from(now));
        return new Metadata(true, revision, now, processed.analysis());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AccountPrincipal principal,
                                      @RequestHeader("X-Result-Account") String expectedAccount) {
        requireAccount(principal, expectedAccount);
        jdbc.update("DELETE FROM profile_photos WHERE owner_id = ?", principal.accountId());
        return ResponseEntity.noContent().build();
    }

    private void requireAccount(AccountPrincipal principal, String expectedAccount) {
        if (expectedAccount.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The expected signed-in account is required.");
        }
        ResultAccess.owner(principal, "PRIVATE", expectedAccount);
    }

    private PhotoProcessor.Analysis analyzeStored(byte[] bytes) {
        return PhotoProcessor.analyzeStored(bytes);
    }

    public record Metadata(boolean stored, UUID revision, Instant updatedAt, PhotoProcessor.Analysis analysis) {
    }

    public record ProviderStatus(boolean available) {
    }

    public record AnalysisRequest(UUID revision, boolean externalConsent, boolean adultAndAuthorized) {
    }
}
