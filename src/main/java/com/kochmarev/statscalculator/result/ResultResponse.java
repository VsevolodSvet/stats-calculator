package com.kochmarev.statscalculator.result;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kochmarev.statscalculator.assessment.AssessmentDefinition;
import com.kochmarev.statscalculator.assessment.Attribute;

public record ResultResponse(
        UUID id, String assessmentId, int assessmentVersion, int scoringVersion,
        Instant createdAt, Map<String, Integer> scores, Map<String, String> explanations,
        String disclaimer, String sharingNotice, boolean privateResult) {

    static ResultResponse from(ResultEntity entity, AssessmentDefinition assessment) {
        return new ResultResponse(entity.id(), assessment.assessmentId(), assessment.version(),
                entity.scoringVersion(), entity.createdAt(), entity.scores(),
                Arrays.stream(Attribute.values()).collect(Collectors.toMap(Attribute::key, Attribute::explanation)),
                "Playful self-reflection only. These scores are not validated measures of ability, health, "
                        + "intelligence, or future success. Luck reflects perceived opportunity.",
                entity.ownerId() == null
                        ? "Anyone with this unlisted result link can view these scores. No individual answers are stored."
                        : "Private result: only your signed-in account can view these scores. No individual answers are stored.",
                entity.ownerId() != null);
    }
}
