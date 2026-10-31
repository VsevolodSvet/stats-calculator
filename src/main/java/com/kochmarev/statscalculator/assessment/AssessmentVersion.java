package com.kochmarev.statscalculator.assessment;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "assessment_versions")
class AssessmentVersion {

    @Id
    private String id;
    private String assessmentId;
    private int version;
    private int scoringVersion;
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Column(columnDefinition = "text")
    private String instructions;
    private int estimatedMinutes;
    @OneToMany(mappedBy = "assessment")
    @OrderBy("position")
    private List<QuestionEntity> questions;

    protected AssessmentVersion() {
    }

    AssessmentDefinition definition() {
        return new AssessmentDefinition(id, assessmentId, version, scoringVersion, title,
                description, instructions, estimatedMinutes,
                questions.stream().map(QuestionEntity::definition).toList());
    }
}
