package com.kochmarev.statscalculator.result;

import java.util.UUID;

import com.kochmarev.statscalculator.assessment.AssessmentDefinition;
import com.kochmarev.statscalculator.assessment.AssessmentService;
import com.kochmarev.statscalculator.account.AccountPrincipal;
import com.kochmarev.statscalculator.account.ResultAccess;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResultService {

    private final AssessmentService assessments;
    private final ResultRepository repository;

    public ResultService(AssessmentService assessments, ResultRepository repository) {
        this.assessments = assessments;
        this.repository = repository;
    }

    @Transactional
    public ResultResponse submit(SubmitResult request, UUID ownerId) {
        AssessmentDefinition assessment;
        try {
            assessment = assessments.find(request.assessmentId(), request.assessmentVersion());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw exception;
            }
            throw new InvalidAnswersException("Select an existing assessment version.");
        }
        var scores = Scoring.calculate(assessment, request.answers());
        var entity = new ResultEntity(assessment.storageId(), assessment.scoringVersion(), scores, ownerId);
        return ResultResponse.from(repository.saveAndFlush(entity), assessment);
    }

    @Transactional(readOnly = true)
    public ResultResponse get(UUID id, AccountPrincipal principal) {
        var entity = repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Result not found."));
        ResultAccess.requireVisible(entity.ownerId(), principal);
        return ResultResponse.from(entity, assessments.findByStorageId(entity.assessmentVersionId()));
    }
}
