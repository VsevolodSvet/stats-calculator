package com.kochmarev.statscalculator.photo;

import java.awt.Color;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PhotoApiIT {
    @Container
    static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:17");
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    @MockitoBean
    private IlookClient ilook;

    @Autowired
    PhotoApiIT(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Test
    void privatePhotoCanBeSavedReplacedAndDeletedWithoutLeakingToAnotherAccount() throws Exception {
        var owner = account();
        var other = account();
        mvc.perform(get("/api/v1/me/photo").session(owner.session()).header("X-Result-Account", owner.username()))
                .andExpect(jsonPath("$.stored").value(false));
        byte[] original = PhotoProcessorTest.image(100, 100, Color.GRAY, "jpeg");
        mvc.perform(post("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).header("X-Photo-Consent", "true")
                        .contentType(MediaType.IMAGE_JPEG).content(original))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stored").value(true))
                .andExpect(jsonPath("$.analysis.width").value(100));
        byte[] normalized = mvc.perform(get("/api/v1/me/photo/image").session(owner.session())
                        .header("X-Result-Account", owner.username()))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(normalized).startsWith((byte) 137, (byte) 80, (byte) 78, (byte) 71);
        mvc.perform(get("/api/v1/me/photo/image")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/photo/image").session(other.session()).header("X-Result-Account", other.username()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/photo").session(other.session()).with(csrf())
                        .header("X-Result-Account", owner.username())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/me/photo").session(other.session()).with(csrf())
                        .header("X-Result-Account", other.username())).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).header("X-Photo-Consent", "true")
                        .contentType(MediaType.IMAGE_PNG).content(PhotoProcessorTest.image(128, 128, Color.BLACK, "png")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analysis.brightnessPercent").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_photos WHERE owner_id = "
                + "(SELECT id FROM accounts WHERE username = ?)", Integer.class, owner.username())).isEqualTo(1);
        mvc.perform(delete("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/photo").session(owner.session()).header("X-Result-Account", owner.username()))
                .andExpect(jsonPath("$.stored").value(false));
        mvc.perform(get("/api/v1/me/photo/image").session(owner.session()).header("X-Result-Account", owner.username()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingConsentCsrfChangedAccountAndInvalidInputWithoutReplacingPhoto() throws Exception {
        var owner = account();
        byte[] image = PhotoProcessorTest.image(64, 64, Color.GRAY, "png");
        mvc.perform(post("/api/v1/me/photo").session(owner.session())
                        .header("X-Result-Account", owner.username()).header("X-Photo-Consent", "true")
                        .contentType(MediaType.IMAGE_PNG).content(image)).andExpect(status().isForbidden());
        for (String consent : new String[]{"false", ""}) {
            var builder = post("/api/v1/me/photo").session(owner.session()).with(csrf())
                    .header("X-Result-Account", owner.username()).contentType(MediaType.IMAGE_PNG).content(image);
            if (!consent.isEmpty()) builder.header("X-Photo-Consent", consent);
            mvc.perform(builder).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", "different_account").header("X-Photo-Consent", "true")
                        .contentType(MediaType.IMAGE_PNG).content(image)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).header("X-Photo-Consent", "true")
                        .contentType(MediaType.IMAGE_PNG).content(image)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).header("X-Photo-Consent", "true")
                        .contentType(MediaType.IMAGE_PNG).content("not an image")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).header("X-Photo-Consent", "true")
                        .contentType(MediaType.IMAGE_PNG).content(new byte[PhotoProcessor.MAX_BYTES + 1]))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).header("X-Photo-Consent", "true")
                        .contentType("image/svg+xml").content("<svg/>")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(delete("/api/v1/me/photo").session(owner.session()).header("X-Result-Account", owner.username()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/photo").session(owner.session()).header("X-Result-Account", owner.username()))
                .andExpect(jsonPath("$.stored").value(true)).andExpect(jsonPath("$.analysis.width").value(64));
    }

    private Login account() throws Exception {
        String username = "photo_" + UUID.randomUUID().toString().substring(0, 8);
        String secret = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", secret))))
                .andExpect(status().isCreated());
        var session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .param("username", username).param("password", secret))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
        return new Login(username, session);
    }

    @Test
    void ilookRequiresCsrfSeparateConsentOwnerAndExactPhotoRevision() throws Exception {
        var owner = account();
        var other = account();
        byte[] image = PhotoProcessorTest.image(64, 64, Color.GRAY, "png");
        mvc.perform(post("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).header("X-Photo-Consent", "true")
                        .contentType(MediaType.IMAGE_PNG).content(image)).andExpect(status().isOk());
        UUID revision = jdbc.queryForObject("SELECT revision FROM profile_photos WHERE owner_id = "
                + "(SELECT id FROM accounts WHERE username = ?)", UUID.class, owner.username());
        String body = json.writeValueAsString(Map.of("revision", revision,
                "externalConsent", true, "adultAndAuthorized", true));
        mvc.perform(get("/api/v1/me/photo/ilook")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/photo/ilook").session(owner.session())
                        .header("X-Result-Account", owner.username()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        for (String field : new String[]{"externalConsent", "adultAndAuthorized"}) {
            mvc.perform(post("/api/v1/me/photo/ilook").session(owner.session()).with(csrf())
                            .header("X-Result-Account", owner.username()).contentType(MediaType.APPLICATION_JSON)
                            .content(body.replace("\"" + field + "\":true", "\"" + field + "\":false")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/me/photo/ilook").session(other.session()).with(csrf())
                        .header("X-Result-Account", other.username()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/me/photo/ilook").session(owner.session()).with(csrf())
                        .header("X-Result-Account", "different_account").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/me/photo/ilook").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace(revision.toString(), UUID.randomUUID().toString())))
                .andExpect(status().isConflict());
        verifyNoInteractions(ilook);
        when(ilook.available()).thenReturn(true);
        mvc.perform(get("/api/v1/me/photo/ilook").session(owner.session())
                        .header("X-Result-Account", owner.username()))
                .andExpect(jsonPath("$.available").value(true));
        when(ilook.analyze(any(byte[].class))).thenReturn(new IlookClient.Report(
                UUID.randomUUID().toString(), "v1", "test", 65, java.util.List.of(),
                "Subjective.", "medium", "Not charisma."));
        mvc.perform(post("/api/v1/me/photo/ilook").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overallScore").value(65));
        byte[] stored = jdbc.queryForObject("SELECT image FROM profile_photos WHERE revision = ?", byte[].class, revision);
        verify(ilook).analyze(stored);
        mvc.perform(delete("/api/v1/me/photo").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username())).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/me/photo/ilook").session(owner.session()).with(csrf())
                        .header("X-Result-Account", owner.username()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    private record Login(String username, MockHttpSession session) {
    }
}
