package com.kochmarev.statscalculator.practice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kochmarev.statscalculator.result.InvalidAnswersException;

final class PracticeScoring {

    private PracticeScoring() {
    }

    static int calculate(List<PracticeActivity.Item> items, boolean coordination, SubmitPractice request) {
        if (request.mode() == SubmitPractice.Mode.TIMED) {
            if (!coordination || request.elapsedMs() == null
                    || request.elapsedMs() < 1 || request.elapsedMs() > 86_400_000) {
                throw new InvalidAnswersException("Timed coordination requires elapsedMs between 1 and 86400000.");
            }
        } else if (request.elapsedMs() != null) {
            throw new InvalidAnswersException("Untimed practice must not include elapsedMs.");
        }
        Map<String, String> selections = new HashMap<>();
        for (var selection : request.selections()) {
            if (selections.putIfAbsent(selection.itemId(), selection.optionId()) != null) {
                throw new InvalidAnswersException("Select exactly once for each practice item.");
            }
        }
        if (selections.size() != items.size()) {
            throw new InvalidAnswersException("Complete every item in the selected activity version.");
        }
        int correct = 0;
        for (var item : items) {
            var selection = selections.remove(item.id());
            if (selection == null || item.options().stream().noneMatch(option -> option.id().equals(selection))) {
                throw new InvalidAnswersException("Selections must use items and options from this activity version.");
            }
            if (item.correctOptionId().equals(selection)) correct++;
        }
        return correct;
    }
}
