package com.kochmarev.statscalculator.result;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kochmarev.statscalculator.assessment.AssessmentDefinition;
import com.kochmarev.statscalculator.assessment.Attribute;

public final class Scoring {

    private Scoring() {
    }

    public static Map<Attribute, Integer> calculate(
            AssessmentDefinition assessment, List<SubmitResult.Answer> answers) {
        if (assessment.scoringVersion() != 1) {
            throw new IllegalStateException("Unsupported scoring version: " + assessment.scoringVersion());
        }
        Map<String, String> selections = new HashMap<>();
        for (var answer : answers) {
            if (selections.putIfAbsent(answer.questionId(), answer.optionId()) != null) {
                throw new InvalidAnswersException("Each question must be answered exactly once.");
            }
        }
        if (selections.size() != assessment.questions().size()) {
            throw new InvalidAnswersException("Answer every question in the selected assessment version.");
        }

        Map<Attribute, Integer> totals = new EnumMap<>(Attribute.class);
        Map<Attribute, Integer> counts = new EnumMap<>(Attribute.class);
        for (var question : assessment.questions()) {
            String optionId = selections.remove(question.id());
            if (optionId == null) {
                throw new InvalidAnswersException("Answers must belong to the selected assessment version.");
            }
            int value = question.options().stream().filter(option -> option.id().equals(optionId))
                    .findFirst().orElseThrow(() ->
                            new InvalidAnswersException("Selected option does not belong to its question.")).value();
            if (value < 1 || value > 5) {
                throw new IllegalStateException("Assessment contains an invalid option value.");
            }
            totals.merge(question.attribute(), value, Integer::sum);
            counts.merge(question.attribute(), 1, Integer::sum);
        }

        Map<Attribute, Integer> scores = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            Integer count = counts.get(attribute);
            if (count == null) {
                throw new IllegalStateException("Assessment does not cover " + attribute);
            }
            int score = 1 + (int) Math.round(9 * (totals.get(attribute) - count) / (4.0 * count));
            scores.put(attribute, score);
        }
        return Map.copyOf(scores);
    }
}
