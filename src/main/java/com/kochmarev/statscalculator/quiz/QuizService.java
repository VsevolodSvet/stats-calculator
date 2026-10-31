package com.kochmarev.statscalculator.quiz;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.kochmarev.statscalculator.account.AccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuizService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public QuizService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<StatQuiz> list() {
        return jdbc.query("SELECT definition::text FROM stat_quizzes ORDER BY position",
                (row, index) -> json.readValue(row.getString(1), StatQuiz.class));
    }

    public StatQuiz definition(String stat, int version) {
        return jdbc.query("SELECT definition::text FROM stat_quizzes WHERE stat = ? AND version = ?",
                (row, index) -> json.readValue(row.getString(1), StatQuiz.class), stat, version)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Stat quiz version not found."));
    }

    Result save(QuizAttempt attempt, List<QuizController.Answer> answers, Instant now) {
        if (attempt.saved) return read(attempt.id, attempt.ownerId);
        if (!attempt.tasksComplete(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete the tasks before saving.");
        }
        int survey = QuizScoring.surveyPercent(attempt.quiz, answers);
        int score = QuizScoring.score(survey, attempt.correct, attempt.quiz.tasks().size(),
                attempt.quiz.taskWeightPercent());
        Instant createdAt = now.truncatedTo(ChronoUnit.MICROS);
        jdbc.update("""
                INSERT INTO stat_quiz_results (id, owner_id, stat, quiz_version, scoring_version,
                    created_at, score, survey_percent, tasks_correct, tasks_total, tasks_answered)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
                """, attempt.id, attempt.ownerId, attempt.quiz.stat(), attempt.quiz.version(),
                Timestamp.from(createdAt), score, survey, attempt.correct, attempt.quiz.tasks().size(), attempt.answered);
        attempt.saved = true;
        attempt.lastOption = null;
        attempt.lastTask = null;
        return read(attempt.id, attempt.ownerId);
    }

    public Result result(UUID id, AccountPrincipal principal) {
        return read(id, principal == null ? null : principal.accountId());
    }

    private Result read(UUID id, UUID viewer) {
        return jdbc.query("""
                SELECT r.*, q.definition->>'title' AS title
                FROM stat_quiz_results r JOIN stat_quizzes q ON r.stat = q.stat AND r.quiz_version = q.version
                WHERE r.id = ? AND (r.owner_id IS NULL OR r.owner_id = ?)
                """, (row, index) -> new Result(row.getObject("id", UUID.class), row.getString("stat"),
                row.getInt("quiz_version"), row.getInt("scoring_version"), row.getString("title"),
                row.getTimestamp("created_at").toInstant(), row.getInt("score"), row.getInt("survey_percent"),
                row.getInt("tasks_correct"), row.getInt("tasks_total"), row.getInt("tasks_answered"),
                row.getObject("owner_id") != null), id, viewer).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stat result not found or not accessible."));
    }

    public List<SheetEntry> sheet(AccountPrincipal principal) {
        return jdbc.query("""
                SELECT q.stat, q.version, q.definition->>'title' AS title, r.id, r.score, r.created_at
                FROM stat_quizzes q
                LEFT JOIN LATERAL (
                    SELECT id, score, created_at FROM stat_quiz_results
                    WHERE owner_id = ? AND stat = q.stat
                    ORDER BY created_at DESC, id DESC LIMIT 1
                ) r ON true
                WHERE q.version = (SELECT max(version) FROM stat_quizzes WHERE stat = q.stat)
                ORDER BY q.position
                """, (row, index) -> new SheetEntry(row.getString("stat"), row.getInt("version"), row.getString("title"),
                row.getObject("id", UUID.class), row.getObject("score", Integer.class),
                row.getTimestamp("created_at") == null ? null : row.getTimestamp("created_at").toInstant()),
                principal.accountId());
    }

    public record Result(UUID id, String stat, int quizVersion, int scoringVersion, String title,
                         Instant createdAt, int score, int surveyPercent, int tasksCorrect,
                         int tasksTotal, int tasksAnswered, boolean privateResult) {
    }

    public record SheetEntry(String stat, int quizVersion, String title, UUID resultId, Integer score, Instant createdAt) {
    }
}
