package com.kochmarev.statscalculator.quiz;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StatQuizApiIT {
    @Container
    static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:17");

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final QuizService service;

    @Autowired
    StatQuizApiIT(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc, QuizService service) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
        this.service = service;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Test
    void publishesSevenOriginalQuizzesWithoutAnswerKeysOrWeights() throws Exception {
        String body = mvc.perform(get("/api/v1/stat-quizzes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("correctOptionId", "\"answer\"", "\"value\"", "\"tasks\"");
        for (var quiz : service.list()) {
            assertThat(quiz.questions()).isNotEmpty();
            assertThat(quiz.questions()).extracting(StatQuiz.Question::id).doesNotHaveDuplicates();
            assertThat(quiz.tasks()).extracting(StatQuiz.Task::id).doesNotHaveDuplicates();
            for (var question : quiz.questions()) {
                assertThat(question.options()).isNotEmpty();
                assertThat(question.options()).extracting(StatQuiz.Option::value).allMatch(value -> value >= 0 && value <= 4);
            }
            for (var task : quiz.tasks()) {
                if (!task.kind().equals("CHANCE")) {
                    assertThat(task.options()).extracting(StatQuiz.Option::id).contains(task.answer());
                }
            }
        }
        mvc.perform(get("/api/v1/stat-quizzes/strength/versions/2")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/character-sheet")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/stat-quizzes/strength/versions/1/attempts")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousSummariesPersistAndRetriesDoNotCreateAnotherResult() throws Exception {
        var session = new MockHttpSession();
        var attempt = start("strength", session);
        UUID id = UUID.fromString(attempt.get("id").asString());
        mvc.perform(post("/api/v1/stat-quiz-attempts/" + id + "/result").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[]}"))
                .andExpect(status().isBadRequest());
        String payload = submission("strength", false);
        var result = mvc.perform(post("/api/v1/stat-quiz-attempts/" + id + "/result").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.score").value(10))
                .andExpect(jsonPath("$.privateResult").value(false)).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/stat-quiz-attempts/" + id + "/result").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id.toString()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stat_quiz_results WHERE id = ?", Integer.class, id)).isEqualTo(1);
        String read = mvc.perform(get("/api/v1/stat-quiz-results/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(read)).isEqualTo(json.readTree(result));
        assertThat(read).doesNotContain("answers", "education", "password");
    }

    @Test
    void sequentialTasksCannotBeSkippedAndOnlyGridTargetsAreExposed() throws Exception {
        for (String stat : List.of("perception", "intelligence", "agility", "luck")) {
            var session = new MockHttpSession();
            var state = start(stat, session);
            String id = state.get("id").asString();
            assertThat(state.get("task").get("targetOptionId").isNull()).isEqualTo(!stat.equals("agility"));
            mvc.perform(post("/api/v1/stat-quiz-attempts/" + id + "/result").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(submission(stat, false)))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/v1/stat-quiz-attempts/" + id + "/answers").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionId\":\"wrong-task\",\"optionId\":\"1\"}"))
                    .andExpect(status().isConflict());
            for (var task : service.definition(stat, 1).tasks()) {
                String option = task.kind().equals("CHANCE") ? "1" : task.answer();
                state = json.readTree(mvc.perform(post("/api/v1/stat-quiz-attempts/" + id + "/answers")
                                .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(new QuizController.Answer(task.id(), option))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            }
            assertThat(state.get("task").isNull()).isTrue();
            mvc.perform(post("/api/v1/stat-quiz-attempts/" + id + "/result").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(submission(stat, false)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tasksAnswered").value(service.definition(stat, 1).tasks().size()));
        }
    }

    @Test
    void privateHistoryAndLatestSheetStayOwnerOnlyWithUntestedStatsAndOldHistoryRetained() throws Exception {
        var owner = login();
        var other = login();
        mvc.perform(get("/api/v1/me/character-sheet").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].score").isEmpty());
        String first = saveStrength(owner, true);
        String latest = saveStrength(owner, false);
        mvc.perform(get("/api/v1/me/character-sheet").session(owner))
                .andExpect(jsonPath("$[0].score").value(10))
                .andExpect(jsonPath("$[0].resultId").value(latest))
                .andExpect(jsonPath("$[1].score").isEmpty());
        mvc.perform(get("/api/v1/me/character-sheet").session(other))
                .andExpect(jsonPath("$[0].score").isEmpty());
        mvc.perform(get("/api/v1/me/results").session(owner))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].kind").value("STAT"));
        mvc.perform(get("/api/v1/stat-quiz-results/" + first).session(owner)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/stat-quiz-results/" + latest)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/stat-quiz-results/" + latest).session(other)).andExpect(status().isNotFound());
        var privateState = start("strength", owner);
        mvc.perform(get("/api/v1/stat-quiz-attempts/" + privateState.get("id").asString()).session(other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/logout").session(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/stat-quiz-attempts/" + privateState.get("id").asString() + "/result").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(submission("strength", false)))
                .andExpect(status().isNotFound());
    }

    @Test
    void replacingAttemptInvalidatesOldDraftAndStorageFailureCanBeRetried() throws Exception {
        var session = new MockHttpSession();
        var old = start("strength", session);
        var current = start("endurance", session);
        mvc.perform(get("/api/v1/stat-quiz-attempts/" + old.get("id").asString()).session(session))
                .andExpect(status().isNotFound());
        jdbc.execute("""
                CREATE FUNCTION reject_stat_quiz() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'Simulated stat quiz storage failure'; END; $$
                """);
        try {
            jdbc.execute("""
                    CREATE TRIGGER reject_stat_quiz BEFORE INSERT ON stat_quiz_results
                    FOR EACH ROW EXECUTE FUNCTION reject_stat_quiz()
                    """);
            mvc.perform(post("/api/v1/stat-quiz-attempts/" + current.get("id").asString() + "/result")
                            .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(submission("endurance", false)))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_stat_quiz ON stat_quiz_results");
            jdbc.execute("DROP FUNCTION reject_stat_quiz()");
        }
        mvc.perform(post("/api/v1/stat-quiz-attempts/" + current.get("id").asString() + "/result")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(submission("endurance", false)))
                .andExpect(status().isCreated());
    }

    @Test
    void expiredServerDeadlineIgnoresLateAnswersAndSavesUnansweredAsMisses() throws Exception {
        var session = new MockHttpSession();
        var expired = new QuizAttempt(service.definition("perception", 1), null, Instant.now().minusSeconds(90));
        session.setAttribute(QuizController.class.getName() + ".attempt", expired);
        mvc.perform(post("/api/v1/stat-quiz-attempts/" + expired.id + "/answers").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"perception-t1\",\"optionId\":\"2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.task").isEmpty())
                .andExpect(jsonPath("$.answered").value(0));
        mvc.perform(post("/api/v1/stat-quiz-attempts/" + expired.id + "/result").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(submission("perception", false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.score").value(4))
                .andExpect(jsonPath("$.tasksCorrect").value(0)).andExpect(jsonPath("$.tasksTotal").value(6));
    }

    @Test
    void startGuardsPrivacyAndSubmissionCannotSupplyScores() throws Exception {
        mvc.perform(post("/api/v1/stat-quizzes/strength/versions/1/attempts").with(csrf())
                        .header("X-Result-Privacy", "PRIVATE")).andExpect(status().isUnauthorized());
        var owner = login();
        mvc.perform(post("/api/v1/stat-quizzes/strength/versions/1/attempts").session(owner).with(csrf())
                        .header("X-Result-Privacy", "UNLISTED")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/stat-quizzes/strength/versions/1/attempts").session(owner).with(csrf())
                        .header("X-Result-Account", "different_account")).andExpect(status().isConflict());
        var state = start("strength", owner);
        for (String payload : List.of("{\"answers\":[],\"score\":10}", "{\"answers\":[null]}",
                "{\"answers\":null}", "{\"answers\":[{\"questionId\":\"\",\"optionId\":\"1\"}]}")) {
            mvc.perform(post("/api/v1/stat-quiz-attempts/" + state.get("id").asString() + "/result")
                            .session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    private String saveStrength(MockHttpSession session, boolean minimum) throws Exception {
        var state = start("strength", session);
        String result = mvc.perform(post("/api/v1/stat-quiz-attempts/" + state.get("id").asString() + "/result")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(submission("strength", minimum)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asString();
    }

    private JsonNode start(String stat, MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/stat-quizzes/" + stat + "/versions/1/attempts")
                        .session(session).with(csrf())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String submission(String stat, boolean minimum) {
        return json.writeValueAsString(new QuizController.Submission(service.definition(stat, 1).questions().stream()
                .map(question -> new QuizController.Answer(question.id(),
                        (minimum ? question.options().getFirst() : question.options().getLast()).id())).toList()));
    }

    private MockHttpSession login() throws Exception {
        String username = "quiz_" + UUID.randomUUID().toString().substring(0, 8);
        String secret = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", secret))))
                .andExpect(status().isCreated());
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .param("username", username).param("password", secret))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
}
