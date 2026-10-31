package com.kochmarev.statscalculator.assessment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AssessmentService {

    private final AssessmentRepository repository;

    public AssessmentService(AssessmentRepository repository) {
        this.repository = repository;
    }

    public List<AssessmentDefinition> list() {
        return repository.findAllByOrderByAssessmentIdAscVersionDesc().stream()
                .map(AssessmentVersion::definition).toList();
    }

    public AssessmentDefinition find(String id, int version) {
        return repository.findByAssessmentIdAndVersion(id, version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Assessment version not found."))
                .definition();
    }

    public AssessmentDefinition findByStorageId(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Saved result references a missing assessment."))
                .definition();
    }
}
