package com.kochmarev.statscalculator.result;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.kochmarev.statscalculator.assessment.Attribute;

@Entity
@Table(name = "results")
class ResultEntity {

    @Id
    private UUID id;
    private UUID ownerId;
    private String assessmentVersionId;
    private int scoringVersion;
    private Instant createdAt;
    private int strength;
    private int perception;
    private int endurance;
    private int charisma;
    private int intelligence;
    private int agility;
    private int luck;

    protected ResultEntity() {
    }

    ResultEntity(String assessmentVersionId, int scoringVersion, Map<Attribute, Integer> scores, UUID ownerId) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.assessmentVersionId = assessmentVersionId;
        this.scoringVersion = scoringVersion;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.strength = scores.get(Attribute.STRENGTH);
        this.perception = scores.get(Attribute.PERCEPTION);
        this.endurance = scores.get(Attribute.ENDURANCE);
        this.charisma = scores.get(Attribute.CHARISMA);
        this.intelligence = scores.get(Attribute.INTELLIGENCE);
        this.agility = scores.get(Attribute.AGILITY);
        this.luck = scores.get(Attribute.LUCK);
    }

    UUID id() {
        return id;
    }

    UUID ownerId() {
        return ownerId;
    }

    String assessmentVersionId() {
        return assessmentVersionId;
    }

    int scoringVersion() {
        return scoringVersion;
    }

    Instant createdAt() {
        return createdAt;
    }

    Map<String, Integer> scores() {
        return Map.of("strength", strength, "perception", perception, "endurance", endurance,
                "charisma", charisma, "intelligence", intelligence, "agility", agility, "luck", luck);
    }
}
