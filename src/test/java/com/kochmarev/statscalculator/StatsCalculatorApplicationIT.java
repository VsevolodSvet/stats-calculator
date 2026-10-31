package com.kochmarev.statscalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import com.kochmarev.statscalculator.assessment.AssessmentService;
import com.kochmarev.statscalculator.result.SubmitResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(StatsCalculatorApplicationIT.TestController.class)
@Testcontainers
class StatsCalculatorApplicationIT {

    @Container
    static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    private final ApplicationContext context;
    private final MockMvc mvc;
    private final AssessmentService assessments;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    @Autowired
    StatsCalculatorApplicationIT(ApplicationContext context, MockMvc mvc, AssessmentService assessments,
                                 ObjectMapper json, JdbcTemplate jdbc) {
        this.context = context;
        this.mvc = mvc;
        this.assessments = assessments;
        this.json = json;
        this.jdbc = jdbc;
    }

    @Test
    void applicationStartsWithoutDemoOrGeneratedUsers() {
        assertThat(context.getBean(StatsCalculatorApplication.class)).isNotNull();
        assertThat(context.getBeansOfType(UserDetailsService.class)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)).isZero();
    }

    @Test
    void readsAreAccessibleWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/foundation-test"))
                .andExpect(status().isOk())
                .andExpect(content().string("ready"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void headRequestsAreAccessibleWithoutAuthentication() throws Exception {
        mvc.perform(head("/api/v1/foundation-test"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void legacyGreetingIsRemoved() throws Exception {
        mvc.perform(get("/main"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void browserShellAndAssetsAreServedWithoutAuthentication() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PERSONNEL EVALUATION TERMINAL")))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("index.html"));
        mvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1")));
        mvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void writesWithoutACsrfTokenAreDenied() throws Exception {
        mvc.perform(post("/api/v1/foundation-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void writesRemainDeniedEvenWithACsrfToken() throws Exception {
        mvc.perform(post("/api/v1/foundation-test").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void migrationsSeedOrderedQuestionsWithoutExposingScoring() throws Exception {
        mvc.perform(get("/api/v1/assessments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("special-self-assessment"))
                .andExpect(jsonPath("$[0].questionCount").value(21))
                .andExpect(jsonPath("$[0].estimatedMinutes").value(5));

        mvc.perform(get("/api/v1/assessments/special-self-assessment/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(21))
                .andExpect(jsonPath("$.questions[0].id").value("strength-1-v1"))
                .andExpect(jsonPath("$.questions[20].id").value("luck-3-v1"))
                .andExpect(jsonPath("$.questions[0].options.length()").value(5))
                .andExpect(jsonPath("$.questions[0].options[0].label").value("Strongly disagree"))
                .andExpect(jsonPath("$.questions[0].options[4].label").value("Strongly agree"))
                .andExpect(jsonPath("$.questions[0].attribute").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[0].value").doesNotExist())
                .andExpect(jsonPath("$.questions[0].options[0].scoreValue").doesNotExist())
                .andExpect(jsonPath("$.scoringVersion").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM answer_options", Integer.class)).isEqualTo(105);
        assertThat(jdbc.queryForList(
                "SELECT count(*) AS count FROM questions GROUP BY attribute", Integer.class))
                .containsOnly(3).hasSize(7);
    }

    @Test
    void submissionPersistsScoresAndCanBeReadWithoutSessionOrAnswers() throws Exception {
        String body = mvc.perform(post("/api/v1/results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(5))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/api/v1/results/")))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.scores.strength").value(10))
                .andExpect(jsonPath("$.scores.luck").value(10))
                .andExpect(jsonPath("$.answers").doesNotExist())
                .andExpect(jsonPath("$.assessmentVersion").value(1))
                .andExpect(jsonPath("$.scoringVersion").value(1))
                .andExpect(jsonPath("$.explanations.intelligence").isString())
                .andExpect(jsonPath("$.disclaimer").isString())
                .andExpect(jsonPath("$.sharingNotice").isString())
                .andReturn().getResponse().getContentAsString();
        var saved = json.readTree(body);
        String id = saved.get("id").asString();
        UUID.fromString(id);

        String fetched = mvc.perform(get("/api/v1/results/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(fetched)).isEqualTo(saved);
        assertThat(saved.get("scores").size()).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT strength FROM results WHERE id = ?", Integer.class,
                UUID.fromString(id))).isEqualTo(10);

        mvc.perform(post("/api/v1/results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scores.strength").value(1))
                .andExpect(jsonPath("$.scores.luck").value(1))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(id)));
        mvc.perform(get("/api/v1/results"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void malformedOrUnsupportedSubmissionsAreRejectedWithoutSaving() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM results", Integer.class);
        var valid = request(3);
        var missing = new ArrayList<>(valid.answers());
        missing.removeLast();
        assertInvalid(new SubmitResult(valid.assessmentId(), 1, missing));

        var duplicate = new ArrayList<>(valid.answers());
        duplicate.set(20, duplicate.getFirst());
        assertInvalid(new SubmitResult(valid.assessmentId(), 1, duplicate));

        var foreignQuestion = new ArrayList<>(valid.answers());
        foreignQuestion.set(0, new SubmitResult.Answer("strength-1-v2", "strength-1-v2-option-3"));
        assertInvalid(new SubmitResult(valid.assessmentId(), 1, foreignQuestion));

        var foreignOption = new ArrayList<>(valid.answers());
        foreignOption.set(0, new SubmitResult.Answer("strength-1-v1", "luck-1-v1-option-3"));
        assertInvalid(new SubmitResult(valid.assessmentId(), 1, foreignOption));

        assertInvalid(new SubmitResult(valid.assessmentId(), 2, valid.answers()));
        assertInvalid(new SubmitResult("unknown", 1, valid.answers()));
        assertInvalid(new SubmitResult(valid.assessmentId(), 0, valid.answers()));
        assertInvalid(new SubmitResult("", 1, valid.answers()));
        assertInvalid(new SubmitResult(valid.assessmentId(), 1, null));
        assertInvalid(new SubmitResult(valid.assessmentId(), 1, List.of()));
        var nullAnswer = new ArrayList<>(valid.answers());
        nullAnswer.set(0, null);
        assertInvalid(new SubmitResult(valid.assessmentId(), 1, nullAnswer));
        var blankAnswer = new ArrayList<>(valid.answers());
        blankAnswer.set(0, new SubmitResult.Answer(" ", " "));
        assertInvalid(new SubmitResult(valid.assessmentId(), 1, blankAnswer));

        mvc.perform(post("/api/v1/results").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/v1/results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("assessmentId", valid.assessmentId(),
                                "assessmentVersion", 1, "answers", valid.answers(), "scores", Map.of("luck", 10)))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM results", Integer.class)).isEqualTo(before);
    }

    @Test
    void missingResourcesAndInvalidIdentifiersHaveProblemResponses() throws Exception {
        for (String path : List.of(
                "/api/v1/assessments/unknown/versions/1",
                "/api/v1/assessments/special-self-assessment/versions/2",
                "/api/v1/results/" + UUID.randomUUID())) {
            mvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get("/api/v1/results/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/v1/results").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/v1/results").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("assessmentId=special-self-assessment"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void storageFailureReturnsAnExplicitErrorAndDoesNotSave() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM results", Integer.class);
        jdbc.execute("""
                CREATE FUNCTION reject_test_result() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'Simulated storage failure'; END; $$
                """);
        try {
            jdbc.execute("""
                    CREATE TRIGGER reject_test_result BEFORE INSERT ON results
                    FOR EACH ROW EXECUTE FUNCTION reject_test_result()
                    """);
            mvc.perform(post("/api/v1/results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request(3))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Data storage is unavailable. Please try again later."))
                    .andExpect(header().doesNotExist("Location"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM results", Integer.class)).isEqualTo(before);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_result ON results");
            jdbc.execute("DROP FUNCTION reject_test_result()");
        }
    }

    @Test
    void databaseRejectsOutOfRangeScoresAndUnknownAssessmentReferences() {
        String insert = """
                INSERT INTO results (id, assessment_version_id, scoring_version, created_at,
                    strength, perception, endurance, charisma, intelligence, agility, luck)
                VALUES (?, ?, 1, now(), ?, 5, 5, 5, 5, 5, 5)
                """;
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "special-self-assessment-v1", 11))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "special-self-assessment-v1", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "unknown-version", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'
                """, String.class)).containsExactlyInAnyOrder(
                "flyway_schema_history", "assessment_versions", "questions", "answer_options", "results",
                "practice_activities", "practice_results", "accounts");
    }

    private SubmitResult request(int optionValue) {
        var assessment = assessments.find("special-self-assessment", 1);
        return new SubmitResult(assessment.assessmentId(), assessment.version(),
                assessment.questions().stream().map(q ->
                        new SubmitResult.Answer(q.id(), q.options().get(optionValue - 1).id())).toList());
    }

    private void assertInvalid(SubmitResult request) throws Exception {
        mvc.perform(post("/api/v1/results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist("Location"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/foundation-test")
        String read() {
            return "ready";
        }

        @PostMapping("/api/v1/foundation-test")
        String write() {
            return "not exposed";
        }
    }
}
