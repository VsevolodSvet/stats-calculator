package com.kochmarev.statscalculator.assessment;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Summary> list() {
        return service.list().stream().map(Summary::from).toList();
    }

    @GetMapping("/{id}/versions/{version}")
    public Detail get(@PathVariable String id, @PathVariable int version) {
        return Detail.from(service.find(id, version));
    }

    public record Summary(String id, int version, String title, String description,
                          int estimatedMinutes, int questionCount) {
        static Summary from(AssessmentDefinition definition) {
            return new Summary(definition.assessmentId(), definition.version(), definition.title(),
                    definition.description(), definition.estimatedMinutes(), definition.questions().size());
        }
    }

    public record Detail(String id, int version, String title, String description, String instructions,
                         int estimatedMinutes, List<Question> questions) {
        static Detail from(AssessmentDefinition definition) {
            return new Detail(definition.assessmentId(), definition.version(), definition.title(),
                    definition.description(), definition.instructions().replace(
                            "Anyone with the unlisted result link can view your scores. Do not submit if you need private storage.",
                            "Anonymous results are unlisted. Results saved while signed in are private to your account."),
                    definition.estimatedMinutes(),
                    definition.questions().stream().map(q -> new Question(q.id(), q.prompt(),
                            q.options().stream().map(o -> new Option(o.id(), o.label())).toList())).toList());
        }
    }

    public record Question(String id, String prompt, List<Option> options) {
    }

    public record Option(String id, String label) {
    }
}
