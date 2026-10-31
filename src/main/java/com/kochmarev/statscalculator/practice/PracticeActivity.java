package com.kochmarev.statscalculator.practice;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "practice_activities")
class PracticeActivity {

    @Id
    private String id;
    private String activityId;
    private int version;
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Column(columnDefinition = "text")
    private String instructions;
    private boolean coordination;
    @JdbcTypeCode(SqlTypes.JSON)
    private Content content;

    protected PracticeActivity() {
    }

    String id() { return id; }
    String activityId() { return activityId; }
    int version() { return version; }
    String title() { return title; }
    String description() { return description; }
    String instructions() { return instructions; }
    boolean coordination() { return coordination; }
    Content content() { return content; }

    public record Content(List<Item> items) {
    }

    public record Item(String id, String prompt, String correctOptionId, List<Option> options) {
    }

    public record Option(String id, String label) {
    }
}
