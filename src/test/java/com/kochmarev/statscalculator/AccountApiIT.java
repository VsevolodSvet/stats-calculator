package com.kochmarev.statscalculator;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
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
class AccountApiIT {

    @Container
    static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:17");
    private static final String PASSWORD = "correct horse battery staple";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AssessmentService assessments;

    @Test
    void registrationValidatesAndStoresOnlyASaltedPasswordHash() throws Exception {
        String username = username();
        register(username);
        String hash = jdbc.queryForObject("SELECT password_hash FROM accounts WHERE username = ?", String.class, username);
        assertThat(hash).startsWith("{pbkdf2}").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        for (String invalid : List.of("ab", "bad name", "admin@example.com", "x".repeat(31))) {
            mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", invalid, "password", PASSWORD))))
                    .andExpect(status().isBadRequest());
        }
        for (String invalid : List.of("short", "x".repeat(129))) {
            mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", username(), "password", invalid))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void realCsrfFlowRotatesSessionAndLogoutInvalidatesIt() throws Exception {
        String username = username();
        register(username);
        var token = token(null);
        String previousId = token.session().getId();
        var login = mvc.perform(post("/api/v1/auth/login").session(token.session())
                        .with(request -> {
                            request.setRequestedSessionId(previousId);
                            request.setRequestedSessionIdValid(true);
                            return request;
                        })
                        .header(token.header(), token.value()).param("username", username.toUpperCase())
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(previousId);
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/v1/me/results").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(post("/api/v1/auth/logout").session(session).header(token.header(), token.value()))
                .andExpect(status().isForbidden());
        var refreshed = token(session);
        mvc.perform(post("/api/v1/auth/logout").session(session).header(refreshed.header(), refreshed.value()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/me/results")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me")).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void allWritesRequireCsrfAndBadLoginsHaveGenericErrors() throws Exception {
        for (String path : List.of("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout",
                "/api/v1/results", "/api/v1/practice/results")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        var name = username();
        register(name);
        for (String loginName : List.of(name, "missing_" + name)) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).param("username", loginName)
                            .param("password", "wrong password"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid username or password."));
        }
        mvc.perform(get("/api/v1/me/results"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void privateResultsAreOwnerOnlyForBothKindsWhileAnonymousLinksRemainAccessible() throws Exception {
        var owner = loginNewAccount();
        var stranger = loginNewAccount();
        for (String path : List.of("/api/v1/results", "/api/v1/practice/results")) {
            String body = path.contains("practice") ? practiceBody() : specialBody();
            var saved = mvc.perform(post(path).session(owner).with(csrf())
                            .header("X-Result-Privacy", "PRIVATE").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.privateResult").value(true))
                    .andReturn();
            String location = saved.getResponse().getHeader("Location");
            mvc.perform(get(location).session(owner)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.privateResult").value(true));
            mvc.perform(get(location)).andExpect(status().isNotFound());
            mvc.perform(get(location).session(stranger)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/me/results").session(stranger))
                    .andExpect(jsonPath("$.items.length()").value(0));
            mvc.perform(post(path).with(csrf()).header("X-Result-Privacy", "PRIVATE")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).session(owner).with(csrf()).header("X-Result-Privacy", "UNLISTED")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict());
            mvc.perform(post(path).session(owner).with(csrf()).header("X-Result-Privacy", "PRIVATE")
                            .header("X-Result-Account", "a_different_account")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict());
            String anonymousLocation = mvc.perform(post(path).with(csrf())
                            .header("X-Result-Privacy", "UNLISTED").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.privateResult").value(false))
                    .andReturn().getResponse().getHeader("Location");
            mvc.perform(get(anonymousLocation)).andExpect(status().isOk());
            mvc.perform(get(anonymousLocation).session(stranger)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/me/results").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].kind").value("PRACTICE"))
                .andExpect(jsonPath("$.items[1].kind").value("SPECIAL"));
    }

    @Test
    void historyPaginatesBothKindsNewestFirstWithoutExposingOtherAccounts() throws Exception {
        var owner = loginNewAccount();
        for (String path : List.of("/api/v1/results", "/api/v1/practice/results", "/api/v1/results")) {
            mvc.perform(post(path).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(path.contains("practice") ? practiceBody() : specialBody()))
                    .andExpect(status().isCreated());
        }
        var first = mvc.perform(get("/api/v1/me/results?size=2").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].kind").value("SPECIAL"))
                .andReturn().getResponse().getContentAsString();
        var second = mvc.perform(get("/api/v1/me/results?size=2&page=1").session(owner))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(first).get("items").get(0).get("id"))
                .isNotEqualTo(json.readTree(second).get("items").get(0).get("id"));
        for (String query : List.of("page=-1", "size=0", "size=51", "page=wrong")) {
            mvc.perform(get("/api/v1/me/results?" + query).session(owner))
                    .andExpect(status().isBadRequest());
        }
    }

    private String specialBody() {
        var assessment = assessments.find("special-self-assessment", 1);
        return json.writeValueAsString(new SubmitResult(assessment.assessmentId(), 1,
                assessment.questions().stream().map(q ->
                        new SubmitResult.Answer(q.id(), q.options().getFirst().id())).toList()));
    }

    private String practiceBody() {
        var selections = jdbc.query("""
                SELECT item->>'id', item->>'correctOptionId'
                FROM practice_activities, jsonb_array_elements(content->'items') item
                WHERE activity_id = 'reasoning' AND version = 1
                """, (row, index) -> Map.of("itemId", row.getString(1), "optionId", row.getString(2)));
        return json.writeValueAsString(Map.of("activityId", "reasoning", "activityVersion", 1,
                "mode", "UNTIMED", "inputMethod", "KEYBOARD", "selections", selections));
    }

    private MockHttpSession loginNewAccount() throws Exception {
        String username = username();
        register(username);
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .param("username", username).param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void register(String username) throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    private Token token(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder builder = get("/api/v1/auth/csrf");
        if (session != null) builder.session(session);
        var result = mvc.perform(builder).andExpect(status().isOk()).andReturn();
        var tree = json.readTree(result.getResponse().getContentAsString());
        return new Token((MockHttpSession) result.getRequest().getSession(false),
                tree.get("headerName").asString(), tree.get("token").asString());
    }

    private String username() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Token(MockHttpSession session, String header, String value) {
    }
}
