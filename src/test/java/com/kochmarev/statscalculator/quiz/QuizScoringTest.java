package com.kochmarev.statscalculator.quiz;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.kochmarev.statscalculator.result.InvalidAnswersException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizScoringTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private final List<StatQuiz.Option> options = List.of(
            new StatQuiz.Option("a", "First", 0), new StatQuiz.Option("b", "Second", 4));

    private StatQuiz quiz(String kind, int seconds) {
        return new StatQuiz("perception", 1, "Perception", "Instructions", seconds, 70, "Original",
                List.of(new StatQuiz.Question("q1", "Scored", true, options),
                        new StatQuiz.Question("q2", "Context", false, options)),
                List.of(new StatQuiz.Task("t1", "Task", kind, "b", options),
                        new StatQuiz.Task("t2", "Task", kind, "b", options)));
    }

    @Test
    void scoresExactBoundsAndDoesNotAwardPointsForContext() {
        var quiz = quiz("CHOICE", 60);
        assertThat(QuizScoring.surveyPercent(quiz, List.of(
                new QuizController.Answer("q1", "a"), new QuizController.Answer("q2", "b")))).isZero();
        assertThat(QuizScoring.surveyPercent(quiz, List.of(
                new QuizController.Answer("q1", "b"), new QuizController.Answer("q2", "a")))).isEqualTo(100);
        assertThat(QuizScoring.score(0, 0, 6, 70)).isEqualTo(1);
        assertThat(QuizScoring.score(100, 6, 6, 70)).isEqualTo(10);
        assertThat(QuizScoring.score(100, 0, 0, 0)).isEqualTo(10);
        assertThat(QuizScoring.score(100, 0, 6, 70)).isEqualTo(4);
        for (int survey = 0; survey <= 100; survey++) {
            for (int correct = 0; correct <= 6; correct++) {
                assertThat(QuizScoring.score(survey, correct, 6, 70)).isBetween(1, 10);
            }
        }
    }

    @Test
    void rejectsMissingDuplicateUnknownAndForeignAnswers() {
        var quiz = quiz("CHOICE", 60);
        for (var answers : List.of(
                List.<QuizController.Answer>of(),
                List.of(new QuizController.Answer("q1", "a"), new QuizController.Answer("q1", "b")),
                List.of(new QuizController.Answer("other", "a"), new QuizController.Answer("q2", "b")),
                List.of(new QuizController.Answer("q1", "unknown"), new QuizController.Answer("q2", "b")))) {
            assertThatThrownBy(() -> QuizScoring.surveyPercent(quiz, answers))
                    .isInstanceOf(InvalidAnswersException.class);
        }
    }

    @Test
    void enforcesDeadlineIncludingExactBoundaryAndCountsUnansweredAsMisses() {
        var attempt = new QuizAttempt(quiz("CHOICE", 60), null, START);
        attempt.answer("t1", "b", START.plusSeconds(59), new Random(1));
        assertThat(attempt.correct).isEqualTo(1);
        assertThat(attempt.tasksComplete(START.plusSeconds(59))).isFalse();
        attempt.answer("t2", "b", START.plusSeconds(60), new Random(1));
        assertThat(attempt.correct).isEqualTo(1);
        assertThat(attempt.answered).isEqualTo(1);
        assertThat(attempt.tasksComplete(START.plusSeconds(60))).isTrue();
        assertThat(QuizScoring.score(100, attempt.correct, 2, 70)).isEqualTo(7);
    }

    @Test
    void retriesCannotRerollChanceOrCountTwiceAndTaskOrderIsEnforced() {
        var attempt = new QuizAttempt(quiz("CHANCE", 0), null, START);
        assertThatThrownBy(() -> attempt.answer("t2", "a", START, new Random(1)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> attempt.answer("t1", "unknown", START, new Random(1)))
                .isInstanceOf(ResponseStatusException.class);
        attempt.answer("t1", "a", START, new Random(1));
        int correct = attempt.correct;
        String feedback = attempt.feedback;
        attempt.answer("t1", "a", START.plusSeconds(300), new Random(2));
        assertThat(attempt.answered).isEqualTo(1);
        assertThat(attempt.correct).isEqualTo(correct);
        assertThat(attempt.feedback).isEqualTo(feedback);
        assertThatThrownBy(() -> attempt.answer("t1", "b", START, new Random(1)))
                .isInstanceOf(ResponseStatusException.class);
        attempt.answer("t2", "b", START.plusSeconds(900), new Random(1));
        assertThat(attempt.tasksComplete(START.plusSeconds(900))).isTrue();
    }

    @Test
    void attemptsKeepOriginalAccountAndPrivacyAndCannotChangeAfterSave() {
        UUID owner = UUID.randomUUID();
        var attempt = new QuizAttempt(quiz("CHOICE", 60), owner, START);
        attempt.requireOwner(owner);
        assertThatThrownBy(() -> attempt.requireOwner(null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> attempt.requireOwner(UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        var anonymous = new QuizAttempt(quiz("CHOICE", 60), null, START);
        assertThatThrownBy(() -> anonymous.requireOwner(owner)).isInstanceOf(ResponseStatusException.class);
        attempt.saved = true;
        assertThatThrownBy(() -> attempt.answer("t1", "a", START, new Random(1)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
