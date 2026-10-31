package com.kochmarev.statscalculator.photo;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IlookClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private IlookClient client;
    private int status = 200;
    private byte[] response;
    private String requestBody;
    private String authorization;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        response = validReport();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/score", exchange -> {
            calls.incrementAndGet();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requestBody = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        client = client("test-" + UUID.randomUUID());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private IlookClient client(String key) {
        return new IlookClient(key, json,
                RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
    }

    @Test
    void sendsMultipartAndReturnsValidatedVersionedFeedback() {
        var report = client.analyze(new byte[]{1, 2, 3});
        assertThat(authorization).startsWith("Bearer test-");
        assertThat(requestBody).contains("name=\"adult_and_authorized\"", "true", "filename=\"profile.png\"");
        assertThat(report.overallScore()).isEqualTo(65);
        assertThat(report.signals()).hasSize(5);
        assertThat(report.rubricVersion()).isEqualTo("test-v1");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void disabledAndOversizedRequestsNeverContactProvider() {
        assertThat(client("").available()).isFalse();
        assertStatus(client(""), new byte[]{1}, 503);
        assertStatus(client, new byte[5_000_001], 413);
        assertThat(calls.get()).isZero();
    }

    @Test
    void mapsProviderFailuresWithoutLeakingBodyOrRetrying() {
        response = "{\"secret\":\"provider internal data\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int[] mapping : new int[][]{{401, 503}, {403, 503}, {422, 422}, {429, 429}, {503, 502}, {302, 502}}) {
            status = mapping[0];
            assertStatus(client, new byte[]{1}, mapping[1]);
        }
        assertThat(calls.get()).isEqualTo(6);
    }

    @Test
    void rejectsMalformedIncompleteOutOfRangeAndOversizedReports() throws Exception {
        for (byte[] invalid : new byte[][]{
                "{".getBytes(), "{}".getBytes(), "null".getBytes(), new byte[0],
                new String(validReport(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\"overall_score_100\":65", "\"overall_score_100\":101").getBytes(),
                new byte[128 * 1024 + 1]}) {
            response = invalid;
            assertStatus(client, new byte[]{1}, 502);
        }
    }

    @Test
    void connectionFailureIsExplicitAndDoesNotLeakDetails() {
        server.stop(0);
        assertStatus(client, new byte[]{1}, 503);
    }

    private void assertStatus(IlookClient target, byte[] image, int expected) {
        assertThatThrownBy(() -> target.analyze(image)).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> {
                    assertThat(exception.getStatusCode().value()).isEqualTo(expected);
                    assertThat(exception.getReason()).doesNotContain("provider internal data");
                });
    }

    private byte[] validReport() {
        var scores = Map.of("facial_harmony", 6, "symmetry", 7, "jawline_structure", 6,
                "skin_appearance", 7, "eyes_brows", 6.5);
        var explanations = Map.of("facial_harmony", "Photo feedback.", "symmetry", "Photo feedback.",
                "jawline_structure", "Photo feedback.", "skin_appearance", "Photo feedback.", "eyes_brows", "Photo feedback.");
        return json.writeValueAsBytes(Map.of("analysis_id", UUID.randomUUID().toString(),
                "rubric_version", "test-v1", "model", "test", "overall_score_100", 65,
                "scores", scores, "score_explanations", explanations, "summary", "Subjective photo feedback.",
                "confidence", "medium", "disclaimer", "Not a personality assessment."));
    }
}
