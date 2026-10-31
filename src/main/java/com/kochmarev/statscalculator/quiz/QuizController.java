package com.kochmarev.statscalculator.quiz;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kochmarev.statscalculator.account.AccountPrincipal;
import com.kochmarev.statscalculator.account.ResultAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class QuizController {
    private static final String ATTEMPT = QuizController.class.getName() + ".attempt";
    private final SecureRandom random = new SecureRandom();
    private final QuizService service;

    public QuizController(QuizService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/stat-quizzes")
    public List<Detail> list() {
        return service.list().stream().map(Detail::from).toList();
    }

    @GetMapping("/api/v1/stat-quizzes/{stat}/versions/{version}")
    public Detail detail(@PathVariable String stat, @PathVariable int version) {
        return Detail.from(service.definition(stat, version));
    }

    @PostMapping("/api/v1/stat-quizzes/{stat}/versions/{version}/attempts")
    public State start(@PathVariable String stat, @PathVariable int version, HttpSession session,
                       @AuthenticationPrincipal AccountPrincipal principal,
                       @RequestHeader(value = "X-Result-Privacy", required = false) String privacy,
                       @RequestHeader(value = "X-Result-Account", required = false) String account) {
        var owner = ResultAccess.owner(principal, privacy, account);
        var attempt = new QuizAttempt(service.definition(stat, version), owner, Instant.now());
        session.setAttribute(ATTEMPT, attempt);
        return state(attempt, Instant.now());
    }

    @GetMapping("/api/v1/stat-quiz-attempts/{id}")
    public State status(@PathVariable UUID id, HttpSession session,
                        @AuthenticationPrincipal AccountPrincipal principal) {
        var attempt = attempt(session, id, principal);
        synchronized (attempt) {
            return state(attempt, Instant.now());
        }
    }

    @PostMapping("/api/v1/stat-quiz-attempts/{id}/answers")
    public State answer(@PathVariable UUID id, HttpSession session,
                        @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody Answer answer) {
        var attempt = attempt(session, id, principal);
        synchronized (attempt) {
            attempt.answer(answer.questionId(), answer.optionId(), Instant.now(), random);
            return state(attempt, Instant.now());
        }
    }

    @PostMapping("/api/v1/stat-quiz-attempts/{id}/result")
    public ResponseEntity<QuizService.Result> save(@PathVariable UUID id, HttpSession session,
                        @AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody Submission submission) {
        var attempt = attempt(session, id, principal);
        synchronized (attempt) {
            var result = service.save(attempt, submission.answers(), Instant.now());
            return ResponseEntity.created(URI.create("/api/v1/stat-quiz-results/" + result.id())).body(result);
        }
    }

    @GetMapping("/api/v1/stat-quiz-results/{id}")
    public QuizService.Result result(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal principal) {
        return service.result(id, principal);
    }

    @GetMapping("/api/v1/me/character-sheet")
    public List<QuizService.SheetEntry> sheet(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.sheet(principal);
    }

    private QuizAttempt attempt(HttpSession session, UUID id, AccountPrincipal principal) {
        var stored = session.getAttribute(ATTEMPT);
        if (!(stored instanceof QuizAttempt attempt) || !attempt.id.equals(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Attempt expired or was replaced by another quiz in this browser session. Start again.");
        }
        attempt.requireOwner(principal == null ? null : principal.accountId());
        return attempt;
    }

    private State state(QuizAttempt attempt, Instant now) {
        var task = attempt.tasksComplete(now) ? null : attempt.quiz.tasks().get(attempt.answered);
        return new State(attempt.id, now, attempt.deadline, attempt.answered, attempt.quiz.tasks().size(),
                task == null ? null : new Task(task.id(), task.prompt(), task.kind(),
                        task.kind().equals("GRID") ? task.answer() : null,
                        task.options().stream().map(option -> new Choice(option.id(), option.label())).toList()),
                attempt.feedback, attempt.saved);
    }

    public record Answer(@NotBlank @Size(max = 100) String questionId,
                         @NotBlank @Size(max = 100) String optionId) {
    }

    public record Submission(@NotNull @Size(max = 30) List<@NotNull @Valid Answer> answers) {
    }

    public record Choice(String id, String label) {
    }

    public record Question(String id, String prompt, boolean scored, List<Choice> options) {
    }

    public record Task(String id, String prompt, String kind, String targetOptionId, List<Choice> options) {
    }

    public record State(UUID id, Instant serverNow, Instant deadline, int answered, int total,
                        Task task, String feedback, boolean saved) {
    }

    public record Detail(String stat, int version, String title, String instructions, int timeLimitSeconds,
                         int taskWeightPercent, String source, int taskCount, List<Question> questions) {
        static Detail from(StatQuiz quiz) {
            return new Detail(quiz.stat(), quiz.version(), quiz.title(), quiz.instructions(), quiz.timeLimitSeconds(),
                    quiz.taskWeightPercent(), quiz.source(), quiz.tasks().size(), quiz.questions().stream()
                    .map(question -> new Question(question.id(), question.prompt(), question.scored(),
                            question.options().stream().map(option -> new Choice(option.id(), option.label())).toList()))
                    .toList());
        }
    }
}
