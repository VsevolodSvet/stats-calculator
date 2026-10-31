package com.kochmarev.statscalculator.assessment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

interface AssessmentRepository extends Repository<AssessmentVersion, String> {

    @EntityGraph(attributePaths = "questions")
    List<AssessmentVersion> findAllByOrderByAssessmentIdAscVersionDesc();

    @EntityGraph(attributePaths = "questions")
    Optional<AssessmentVersion> findByAssessmentIdAndVersion(String assessmentId, int version);

    @EntityGraph(attributePaths = "questions")
    Optional<AssessmentVersion> findById(String id);
}
