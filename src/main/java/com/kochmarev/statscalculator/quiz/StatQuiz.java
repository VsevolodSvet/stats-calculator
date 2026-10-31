package com.kochmarev.statscalculator.quiz;

import java.util.List;

public record StatQuiz(String stat, int version, String title, String instructions,
                       int timeLimitSeconds, int taskWeightPercent, String source,
                       List<Question> questions, List<Task> tasks) {

    public record Question(String id, String prompt, boolean scored, List<Option> options) {
    }

    public record Option(String id, String label, int value) {
    }

    public record Task(String id, String prompt, String kind, String answer, List<Option> options) {
    }
}
