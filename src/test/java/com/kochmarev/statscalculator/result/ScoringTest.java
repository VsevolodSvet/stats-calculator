package com.kochmarev.statscalculator.result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import com.kochmarev.statscalculator.assessment.AssessmentDefinition;
import com.kochmarev.statscalculator.assessment.Attribute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringTest {

    private final AssessmentDefinition assessment = definition();

    @ParameterizedTest
    @CsvSource({"3,1", "4,2", "5,3", "6,3", "7,4", "8,5", "9,6",
            "10,6", "11,7", "12,8", "13,9", "14,9", "15,10"})
    void roundsEveryPossibleRawTotal(int raw, int expected) {
        int first = Math.min(5, raw - 2);
        int second = Math.min(5, raw - first - 1);
        int third = raw - first - second;
        var scores = Scoring.calculate(assessment, answers(first, second, third));
        assertThat(scores).hasSize(7);
        assertThat(scores.values()).containsOnly(expected);
    }

    @Test
    void allAnswerCombinationsStayWithinBounds() {
        for (int first = 1; first <= 5; first++) {
            for (int second = 1; second <= 5; second++) {
                for (int third = 1; third <= 5; third++) {
                    assertThat(Scoring.calculate(assessment, answers(first, second, third)).values())
                            .allSatisfy(score -> assertThat(score).isBetween(1, 10));
                }
            }
        }
    }

    @Test
    void scoresAttributesIndependentlyAndIgnoresAnswerOrder() {
        var answers = new ArrayList<>(answers(1, 1, 1));
        answers.set(0, answer(Attribute.STRENGTH, 0, 5));
        answers.set(1, answer(Attribute.STRENGTH, 1, 5));
        answers.set(2, answer(Attribute.STRENGTH, 2, 5));
        var scores = Scoring.calculate(assessment, answers.reversed());
        assertThat(scores.get(Attribute.STRENGTH)).isEqualTo(10);
        for (Attribute attribute : Attribute.values()) {
            if (attribute != Attribute.STRENGTH) {
                assertThat(scores.get(attribute)).isEqualTo(1);
            }
        }
    }

    @Test
    void rejectsDuplicateMissingUnknownQuestionsAndForeignOptions() {
        var valid = answers(3, 3, 3);
        assertThatThrownBy(() -> Scoring.calculate(assessment, valid.subList(1, valid.size())))
                .isInstanceOf(InvalidAnswersException.class);
        var modified = new ArrayList<>(valid);
        modified.set(0, valid.get(1));
        assertThatThrownBy(() -> Scoring.calculate(assessment, modified))
                .isInstanceOf(InvalidAnswersException.class);
        modified.set(0, new SubmitResult.Answer("unknown", "unknown"));
        assertThatThrownBy(() -> Scoring.calculate(assessment, modified))
                .isInstanceOf(InvalidAnswersException.class);
        modified.set(0, new SubmitResult.Answer(valid.getFirst().questionId(), valid.get(1).optionId()));
        assertThatThrownBy(() -> Scoring.calculate(assessment, modified))
                .isInstanceOf(InvalidAnswersException.class);
    }

    private List<SubmitResult.Answer> answers(int first, int second, int third) {
        int[] values = {first, second, third};
        return Arrays.stream(Attribute.values()).flatMap(attribute ->
                IntStream.range(0, 3).mapToObj(index -> answer(attribute, index, values[index]))).toList();
    }

    private SubmitResult.Answer answer(Attribute attribute, int index, int value) {
        String questionId = attribute.key() + "-" + index;
        return new SubmitResult.Answer(questionId, questionId + "-option-" + value);
    }

    private AssessmentDefinition definition() {
        var questions = Arrays.stream(Attribute.values()).flatMap(attribute ->
                IntStream.range(0, 3).mapToObj(index -> {
                    String id = attribute.key() + "-" + index;
                    return new AssessmentDefinition.Question(id, attribute, "Prompt",
                            IntStream.rangeClosed(1, 5).mapToObj(value ->
                                    new AssessmentDefinition.Option(id + "-option-" + value, "Choice", value)).toList());
                })).toList();
        return new AssessmentDefinition("test-v1", "test", 1, 1, "Test", "Description",
                "Instructions", 5, questions);
    }
}
