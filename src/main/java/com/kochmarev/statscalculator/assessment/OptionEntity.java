package com.kochmarev.statscalculator.assessment;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "answer_options")
class OptionEntity {

    @Id
    private String id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private QuestionEntity question;
    private int position;
    private String label;
    private int scoreValue;

    protected OptionEntity() {
    }

    AssessmentDefinition.Option definition() {
        return new AssessmentDefinition.Option(id, label, scoreValue);
    }
}
