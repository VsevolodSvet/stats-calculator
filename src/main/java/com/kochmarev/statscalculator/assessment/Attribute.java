package com.kochmarev.statscalculator.assessment;

import java.util.Locale;

public enum Attribute {
    STRENGTH("Self-reported comfort with everyday physical force, not a fitness measurement."),
    PERCEPTION("Self-reported attention to details and surroundings, not a sensory assessment."),
    ENDURANCE("Self-reported everyday stamina, not a medical assessment."),
    CHARISMA("Self-reported conversational confidence and engagement, not a measure of personal worth."),
    INTELLIGENCE("Self-reported learning and reasoning confidence, not an IQ score."),
    AGILITY("Self-reported everyday coordination, not a motor-function assessment."),
    LUCK("Perceived opportunity and fortunate circumstances, not a prediction of future luck.");

    private final String explanation;

    Attribute(String explanation) {
        this.explanation = explanation;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String explanation() {
        return explanation;
    }
}
