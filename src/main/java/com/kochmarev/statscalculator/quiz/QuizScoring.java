package com.kochmarev.statscalculator.quiz;

import java.util.HashMap;
import java.util.List;

import com.kochmarev.statscalculator.result.InvalidAnswersException;

final class QuizScoring {
    private QuizScoring() {
    }

    static int surveyPercent(StatQuiz quiz, List<QuizController.Answer> answers) {
        var choices = new HashMap<String, String>();
        for (var answer : answers) {
            if (choices.putIfAbsent(answer.questionId(), answer.optionId()) != null) {
                throw new InvalidAnswersException("Answer each question exactly once.");
            }
        }
        if (choices.size() != quiz.questions().size()) {
            throw new InvalidAnswersException("Answer every question, including the optional-context skip choices.");
        }
        int sum = 0;
        int count = 0;
        for (var question : quiz.questions()) {
            var option = question.options().stream()
                    .filter(item -> item.id().equals(choices.get(question.id())))
                    .findFirst().orElseThrow(() -> new InvalidAnswersException("Choose an option from its own question."));
            if (question.scored()) {
                sum += option.value();
                count++;
            }
        }
        return count == 0 ? 0 : (int) Math.round(sum * 25.0 / count);
    }

    static int score(int surveyPercent, int correct, int total, int taskWeightPercent) {
        double taskPercent = total == 0 ? 0 : 100.0 * correct / total;
        double combined = (surveyPercent * (100 - taskWeightPercent) + taskPercent * taskWeightPercent) / 100;
        return 1 + (int) Math.round(9 * combined / 100);
    }
}
