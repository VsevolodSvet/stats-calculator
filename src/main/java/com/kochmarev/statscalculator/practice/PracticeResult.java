package com.kochmarev.statscalculator.practice;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "practice_results")
class PracticeResult {

    @Id
    private UUID id;
    private UUID ownerId;
    private String activityVersionId;
    private int scoringVersion;
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    private SubmitPractice.Mode mode;
    @Enumerated(EnumType.STRING)
    private SubmitPractice.InputMethod inputMethod;
    private Long elapsedMs;
    private int correct;
    private int total;

    protected PracticeResult() {
        // Required by JPA.
    }

    PracticeResult(PracticeActivity activity, SubmitPractice request, int correct, UUID ownerId) {
        id = UUID.randomUUID();
        this.ownerId = ownerId;
        activityVersionId = activity.id();
        scoringVersion = 1;
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        mode = request.mode();
        inputMethod = request.inputMethod();
        elapsedMs = request.elapsedMs();
        this.correct = correct;
        total = activity.content().items().size();
    }

    UUID id() { return id; }
    UUID ownerId() { return ownerId; }
    String activityVersionId() { return activityVersionId; }

    Response response(PracticeActivity activity) {
        return new Response(id, activity.activityId(), activity.version(), scoringVersion, activity.title(),
                createdAt, mode, inputMethod, elapsedMs, correct, total,
                (int) Math.round(correct * 100.0 / total),
                "Practice only, not a validated measure of perception, intelligence or agility. "
                        + "These results do not change S.P.E.C.I.A.L. scores. Repeated attempts involve learning. "
                        + "Device, input method, screen size, language and accessibility tools affect performance. "
                        + "Elapsed time is browser-reported, not verified reaction time. Do not rank people using these results.",
                ownerId == null
                        ? "Anyone with this unlisted link can view this summary. Individual selections are not stored."
                        : "Private result: only your signed-in account can view this summary. Individual selections are not stored.",
                ownerId != null);
    }

    // Keep the published flat JSON contract; the record generates its constructor and accessors.
    @SuppressWarnings({"java:S100", "java:S1186", "java:S1845", "java:S107"})
    public record Response(UUID id, String activityId, int activityVersion, int scoringVersion,
                           String title, Instant createdAt, SubmitPractice.Mode mode,
                           SubmitPractice.InputMethod inputMethod, Long elapsedMs,
                           int correct, int total, int accuracyPercent,
                           String disclaimer, String sharingNotice, boolean privateResult) {
    }
}
