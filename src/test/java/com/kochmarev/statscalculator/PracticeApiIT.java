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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PracticeApiIT {

    @Container
    static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void exposesThreeActivitiesWithoutChoiceAnswerKeys() throws Exception {
        mvc.perform(get("/api/v1/practice/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        for (var id : List.of("visual-matching", "reasoning")) {
            String body = mvc.perform(get("/api/v1/practice/activities/" + id + "/versions/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(6))
                    .andExpect(jsonPath("$.items[0].options.length()").value(3))
                    .andExpect(jsonPath("$.coordination").value(false))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("correctOptionId");
            assertThat(json.readTree(body).get("items").get(0).get("targetOptionId").isNull()).isTrue();
        }
        mvc.perform(get("/api/v1/practice/activities/coordination/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(12))
                .andExpect(jsonPath("$.items[0].options.length()").value(9))
                .andExpect(jsonPath("$.items[0].targetOptionId").value("5"));
    }

    @Test
    void savesEveryActivitySeparatelyAndReopensWithoutSelections() throws Exception {
        int specialBefore = jdbc.queryForObject("SELECT count(*) FROM results", Integer.class);
        for (String id : List.of("visual-matching", "reasoning", "coordination")) {
            var request = validRequest(id);
            if (id.equals("coordination")) {
                request.put("mode", "TIMED");
                request.put("elapsedMs", 2500);
            }
            String saved = mvc.perform(post("/api/v1/practice/results").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.startsWith("/api/v1/practice/results/")))
                    .andExpect(jsonPath("$.accuracyPercent").value(100))
                    .andExpect(jsonPath("$.selections").doesNotExist())
                    .andExpect(jsonPath("$.scoringVersion").value(1))
                    .andReturn().getResponse().getContentAsString();
            var tree = json.readTree(saved);
            String path = "/api/v1/practice/results/" + tree.get("id").asString();
            String fetched = mvc.perform(get(path)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(fetched)).isEqualTo(tree);
            var repeated = mvc.perform(post("/api/v1/practice/results").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(repeated).get("id")).isNotEqualTo(tree.get("id"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM results", Integer.class)).isEqualTo(specialBefore);
    }

    @Test
    void rejectsInvalidSelectionsModesVersionsAndTiming() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM practice_results", Integer.class);
        var request = validRequest("visual-matching");
        request.put("activityVersion", 2);
        invalid(request);
        request = validRequest("reasoning");
        request.put("mode", "TIMED");
        request.put("elapsedMs", 20);
        invalid(request);
        request = validRequest("coordination");
        request.put("elapsedMs", 20);
        invalid(request);
        request.put("mode", "TIMED");
        for (Object value : new Object[]{0, -1, 86_400_001L, "nonsense"}) {
            request.put("elapsedMs", value);
            invalid(request);
        }
        request.remove("elapsedMs");
        invalid(request);
        for (String field : List.of("inputMethod", "mode", "activityId", "selections")) {
            request = validRequest("reasoning");
            request.put(field, null);
            invalid(request);
        }
        request = validRequest("reasoning");
        request.put("inputMethod", "GAMEPAD");
        invalid(request);
        for (var selections : List.of(
                List.of(Map.of("itemId", "reason-1", "optionId", "b")),
                List.of(Map.of("itemId", "reason-1", "optionId", "b"),
                        Map.of("itemId", "reason-1", "optionId", "a")),
                List.of(Map.of("itemId", "visual-1", "optionId", "b")),
                List.of(Map.of("itemId", "reason-1", "optionId", "unknown")))) {
            request = validRequest("reasoning");
            request.put("selections", selections);
            invalid(request);
        }
        request = validRequest("reasoning");
        request.put("correct", 6);
        invalid(request);
        mvc.perform(post("/api/v1/practice/results").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/v1/practice/results"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/practice/results/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/practice/results/not-a-uuid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/practice/activities/reasoning/versions/2"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM practice_results", Integer.class)).isEqualTo(before);
    }

    @Test
    void failedPracticeWriteIsNotReportedAsSuccess() throws Exception {
        jdbc.execute("""
                CREATE FUNCTION reject_practice() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'Simulated practice storage failure'; END; $$
                """);
        try {
            jdbc.execute("""
                    CREATE TRIGGER reject_practice BEFORE INSERT ON practice_results
                    FOR EACH ROW EXECUTE FUNCTION reject_practice()
                    """);
            mvc.perform(post("/api/v1/practice/results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(validRequest("reasoning"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_practice ON practice_results");
            jdbc.execute("DROP FUNCTION reject_practice()");
        }
    }

    @Test
    void databaseEnforcesSummaryAndTimingBounds() {
        String insert = """
                INSERT INTO practice_results (id, activity_version_id, scoring_version, created_at,
                    mode, input_method, elapsed_ms, correct, total)
                VALUES (?, 'coordination-v1', 1, now(), ?, 'POINTER', ?, ?, 12)
                """;
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "UNTIMED", null, 13))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "UNTIMED", null, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "UNTIMED", 100, 12))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "TIMED", null, 12))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "TIMED", 0, 12))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "TIMED", 86_400_001L, 12))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Map<String, Object> validRequest(String id) {
        List<Map<String, String>> selections = jdbc.query("""
                SELECT item->>'id', item->>'correctOptionId'
                FROM practice_activities, jsonb_array_elements(content->'items') item
                WHERE activity_id = ? AND version = 1
                """, (row, index) -> Map.of("itemId", row.getString(1), "optionId", row.getString(2)), id);
        var request = new java.util.HashMap<String, Object>();
        request.put("activityId", id);
        request.put("activityVersion", 1);
        request.put("mode", "UNTIMED");
        request.put("inputMethod", "KEYBOARD");
        request.put("selections", new ArrayList<>(selections));
        return request;
    }

    private void invalid(Map<String, Object> request) throws Exception {
        mvc.perform(post("/api/v1/practice/results").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
