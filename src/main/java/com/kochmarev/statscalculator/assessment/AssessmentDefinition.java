package com.kochmarev.statscalculator.assessment;

import java.util.List;

public record AssessmentDefinition(
        String storageId, String assessmentId, int version, int scoringVersion,
        String title, String description, String instructions, int estimatedMinutes,
        List<Question> questions) {

    public record Question(String id, Attribute attribute, String prompt, List<Option> options) {
    }

    public record Option(String id, String label, int value) {
    }
}
