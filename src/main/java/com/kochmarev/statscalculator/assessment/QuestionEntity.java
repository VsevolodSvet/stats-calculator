package com.kochmarev.statscalculator.assessment;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "questions")
class QuestionEntity {

    @Id
    private String id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_version_id")
    private AssessmentVersion assessment;
    @Column(nullable = false)
    private int position;
    @Enumerated(EnumType.STRING)
    private Attribute attribute;
    @Column(columnDefinition = "text")
    private String prompt;
    @OneToMany(mappedBy = "question")
    @OrderBy("position")
    @BatchSize(size = 32)
    private List<OptionEntity> options;

    protected QuestionEntity() {
        // Required by JPA.
    }

    AssessmentDefinition.Question definition() {
        return new AssessmentDefinition.Question(id, attribute, prompt,
                options.stream().map(OptionEntity::definition).toList());
    }
}
