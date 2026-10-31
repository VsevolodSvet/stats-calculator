package com.kochmarev.statscalculator.photo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class IlookClient {
    private static final Logger log = LoggerFactory.getLogger(IlookClient.class);
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private static final List<String> SIGNALS = List.of(
            "facial_harmony", "symmetry", "jawline_structure", "skin_appearance", "eyes_brows");
    private final String key;
    private final RestClient client;
    private final ObjectMapper json;
    private final Semaphore slots = new Semaphore(2);

    @Autowired
    public IlookClient(@Value("${ilook.api-key:}") String key, ObjectMapper json) {
        this(key, json, createClient());
    }

    IlookClient(String key, ObjectMapper json, RestClient client) {
        this.key = key.strip();
        this.json = json;
        this.client = client;
    }

    private static RestClient createClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(45));
        return RestClient.builder().baseUrl("https://www.ilook.fit").requestFactory(factory).build();
    }

    public boolean available() {
        return !key.isBlank();
    }

    public Report analyze(byte[] image) {
        if (!available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "iLook is disabled until the administrator configures an API key.");
        }
        // iLook documents a decimal 5 MB limit, smaller than the local storage limit.
        if (image.length > 5_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "The stored PNG exceeds iLook's 5 MB limit. Save a smaller photo.");
        }
        if (!slots.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Photo analysis is busy. Try again later.");
        }
        try {
            var form = new LinkedMultiValueMap<String, Object>();
            form.add("adult_and_authorized", "true");
            form.add("image", new ByteArrayResource(image) {
                @Override
                public String getFilename() {
                    return "profile.png";
                }
            });
            return client.post().uri("/api/v1/score").headers(headers -> headers.setBearerAuth(key))
                    .contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.APPLICATION_JSON)
                    .body(form).exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) throw providerError(status);
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) throw invalidReport();
                        return parse(body);
                    });
        } catch (ResourceAccessException exception) {
            log.warn("iLook connection or read failed; no photo or provider response logged.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "iLook could not be reached or timed out. No report was saved.");
        } finally {
            slots.release();
        }
    }

    private ResponseStatusException providerError(int status) {
        log.warn("iLook request rejected with HTTP {}.", status);
        return switch (status) {
            case 400, 415, 422 -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "iLook rejected the photo. Use one clear, authorized adult face; no report was saved.");
            case 429 -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "iLook's quota is exhausted. Try again after the provider's daily reset.");
            case 401, 403 -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "iLook rejected the server API key. Contact the administrator.");
            default -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "iLook is unavailable or returned an unexpected response. No report was saved.");
        };
    }

    private Report parse(byte[] bytes) {
        try {
            JsonNode root = json.readTree(bytes);
            String analysisId = text(root, "analysis_id");
            try {
                UUID.fromString(analysisId);
            } catch (IllegalArgumentException exception) {
                throw invalidReport();
            }
            var signals = new ArrayList<Signal>();
            for (String name : SIGNALS) {
                signals.add(new Signal(name, score(root.path("scores"), name, 10),
                        text(root.path("score_explanations"), name)));
            }
            String confidence = text(root, "confidence");
            if (!Set.of("low", "medium", "high").contains(confidence)) throw invalidReport();
            return new Report(analysisId, text(root, "rubric_version"), text(root, "model"),
                    score(root, "overall_score_100", 100), List.copyOf(signals),
                    text(root, "summary"), confidence, text(root, "disclaimer"));
        } catch (JacksonException exception) {
            throw invalidReport();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString() || value.asString().isBlank() || value.asString().length() > 4000) {
            throw invalidReport();
        }
        return value.asString();
    }

    private double score(JsonNode node, String field, int maximum) {
        JsonNode value = node.path(field);
        if (!value.isNumber() || !Double.isFinite(value.asDouble())
                || value.asDouble() < 0 || value.asDouble() > maximum) throw invalidReport();
        return value.asDouble();
    }

    private ResponseStatusException invalidReport() {
        log.warn("iLook returned an invalid report; response content not logged.");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "iLook returned an invalid report. No report was saved.");
    }

    public record Signal(String name, double score, String explanation) {
    }

    public record Report(String analysisId, String rubricVersion, String model, double overallScore,
                         List<Signal> signals, String summary, String confidence, String disclaimer) {
    }
}
