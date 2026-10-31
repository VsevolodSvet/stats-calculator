package com.kochmarev.statscalculator.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PersonalResultsController {

    private final JdbcTemplate jdbc;

    public PersonalResultsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/me/results")
    public History history(@AuthenticationPrincipal AccountPrincipal principal,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative; size must be 1-50.");
        }
        var rows = jdbc.query("""
                SELECT r.id, 'SPECIAL' AS kind, a.title, a.version, r.created_at,
                    NULL::integer AS correct, NULL::integer AS total
                FROM results r JOIN assessment_versions a ON r.assessment_version_id = a.id
                WHERE r.owner_id = ?
                UNION ALL
                SELECT r.id, 'PRACTICE' AS kind, a.title, a.version, r.created_at, r.correct, r.total
                FROM practice_results r JOIN practice_activities a ON r.activity_version_id = a.id
                WHERE r.owner_id = ?
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, (row, index) -> new Entry(row.getObject("id", UUID.class),
                row.getString("kind"), row.getString("title"), row.getInt("version"),
                row.getTimestamp("created_at").toInstant(),
                row.getObject("correct", Integer.class), row.getObject("total", Integer.class)),
                principal.accountId(), principal.accountId(), size + 1, (long) page * size);
        return new History(rows.stream().limit(size).toList(), page, size, rows.size() > size);
    }

    public record History(List<Entry> items, int page, int size, boolean hasNext) {
    }

    public record Entry(UUID id, String kind, String title, int version,
                        Instant createdAt, Integer correct, Integer total) {
    }
}
