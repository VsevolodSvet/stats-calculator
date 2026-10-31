package com.kochmarev.statscalculator.practice;

import java.util.List;
import java.util.UUID;

import com.kochmarev.statscalculator.result.InvalidAnswersException;
import com.kochmarev.statscalculator.account.AccountPrincipal;
import com.kochmarev.statscalculator.account.ResultAccess;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PracticeService {

    private final PracticeActivityRepository activities;
    private final PracticeResultRepository results;

    public PracticeService(PracticeActivityRepository activities, PracticeResultRepository results) {
        this.activities = activities;
        this.results = results;
    }

    public List<Summary> list() {
        return activities.findAllByOrderByActivityIdAscVersionDesc().stream().map(Summary::from).toList();
    }

    public Detail detail(String id, int version) {
        return Detail.from(activities.findByActivityIdAndVersion(id, version).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice activity version not found.")));
    }

    @Transactional
    public PracticeResult.Response submit(SubmitPractice request, UUID ownerId) {
        var activity = activities.findByActivityIdAndVersion(request.activityId(), request.activityVersion())
                .orElseThrow(() -> new InvalidAnswersException("Select an existing practice activity version."));
        int correct = PracticeScoring.calculate(activity.content().items(), activity.coordination(), request);
        var result = results.saveAndFlush(new PracticeResult(activity, request, correct, ownerId));
        return result.response(activity);
    }

    public PracticeResult.Response result(UUID id, AccountPrincipal principal) {
        var result = results.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice result not found."));
        ResultAccess.requireVisible(result.ownerId(), principal);
        var activity = activities.findById(result.activityVersionId()).orElseThrow(() ->
                new IllegalStateException("Practice result references a missing activity."));
        return result.response(activity);
    }

    public record Summary(String id, int version, String title, String description,
                          boolean coordination, int itemCount) {
        static Summary from(PracticeActivity activity) {
            return new Summary(activity.activityId(), activity.version(), activity.title(),
                    activity.description(), activity.coordination(), activity.content().items().size());
        }
    }

    public record Detail(String id, int version, String title, String instructions,
                         boolean coordination, List<Item> items) {
        static Detail from(PracticeActivity activity) {
            return new Detail(activity.activityId(), activity.version(), activity.title(), activity.instructions(),
                    activity.coordination(), activity.content().items().stream().map(item ->
                            new Item(item.id(), item.prompt(), item.options(),
                                    activity.coordination() ? item.correctOptionId() : null)).toList());
        }
    }

    public record Item(String id, String prompt, List<PracticeActivity.Option> options, String targetOptionId) {
    }
}
