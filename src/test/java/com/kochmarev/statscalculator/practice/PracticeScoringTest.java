package com.kochmarev.statscalculator.practice;

import java.util.List;

import com.kochmarev.statscalculator.result.InvalidAnswersException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeScoringTest {

    private final List<PracticeActivity.Item> items = List.of(
            new PracticeActivity.Item("one", "First", "a", List.of(
                    new PracticeActivity.Option("a", "A"), new PracticeActivity.Option("b", "B"))),
            new PracticeActivity.Item("two", "Second", "b", List.of(
                    new PracticeActivity.Option("a", "A"), new PracticeActivity.Option("b", "B"))));

    @Test
    void countsCorrectWithoutConvertingToSpecial() {
        assertThat(score("a", "b")).isEqualTo(2);
        assertThat(score("a", "a")).isEqualTo(1);
        assertThat(score("b", "a")).isZero();
    }

    @Test
    void validatesTimingModeAndBoundaries() {
        for (long valid : List.of(1L, 86_400_000L)) {
            assertThat(PracticeScoring.calculate(items, true, request(
                    SubmitPractice.Mode.TIMED, valid, selections("a", "b")))).isEqualTo(2);
        }
        for (Long invalid : new Long[]{null, 0L, -1L, 86_400_001L}) {
            assertThatThrownBy(() -> PracticeScoring.calculate(items, true,
                    request(SubmitPractice.Mode.TIMED, invalid, selections("a", "b"))))
                    .isInstanceOf(InvalidAnswersException.class);
        }
        assertThatThrownBy(() -> PracticeScoring.calculate(items, false,
                request(SubmitPractice.Mode.TIMED, 500L, selections("a", "b"))))
                .isInstanceOf(InvalidAnswersException.class);
        assertThatThrownBy(() -> PracticeScoring.calculate(items, true,
                request(SubmitPractice.Mode.UNTIMED, 500L, selections("a", "b"))))
                .isInstanceOf(InvalidAnswersException.class);
    }

    @Test
    void rejectsIncompleteDuplicateUnknownAndInvalidSelections() {
        for (var selections : List.of(
                List.of(new SubmitPractice.Selection("one", "a")),
                List.of(new SubmitPractice.Selection("one", "a"), new SubmitPractice.Selection("one", "b")),
                List.of(new SubmitPractice.Selection("unknown", "a"), new SubmitPractice.Selection("two", "b")),
                selections("unknown", "b"))) {
            assertThatThrownBy(() -> PracticeScoring.calculate(items, false,
                    request(SubmitPractice.Mode.UNTIMED, null, selections)))
                    .isInstanceOf(InvalidAnswersException.class);
        }
    }

    private int score(String first, String second) {
        return PracticeScoring.calculate(items, false,
                request(SubmitPractice.Mode.UNTIMED, null, selections(first, second)));
    }

    private List<SubmitPractice.Selection> selections(String first, String second) {
        return List.of(new SubmitPractice.Selection("one", first), new SubmitPractice.Selection("two", second));
    }

    private SubmitPractice request(SubmitPractice.Mode mode, Long elapsed, List<SubmitPractice.Selection> selections) {
        return new SubmitPractice("test", 1, mode, SubmitPractice.InputMethod.KEYBOARD, elapsed, selections);
    }
}
