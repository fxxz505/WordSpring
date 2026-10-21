package com.wordspring.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Thin HTTP harness so the tests exercise the real filter chain and JSON mapping
 * instead of relying on MockMvc internals.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestSupport {

    @LocalServerPort
    protected int port;

    protected final HttpClient http = HttpClient.newHttpClient();
    protected final ObjectMapper json = new ObjectMapper();

    protected record Response(int status, JsonNode body) {
        public String text(String field) {
            return body == null ? null : body.path(field).asText(null);
        }

        public int integer(String field) {
            return body == null ? -1 : body.path(field).asInt(-1);
        }
    }

    protected Response post(String path, Object body, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), parse(response.body()));
        } catch (Exception ex) {
            throw new IllegalStateException("HTTP POST failed: " + path, ex);
        }
    }

    protected Response get(String path, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET();
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), parse(response.body()));
        } catch (Exception ex) {
            throw new IllegalStateException("HTTP GET failed: " + path, ex);
        }
    }

    protected String registerAndGetToken(String email) {
        Response response = post("/api/v1/auth/register", Map.of("email", email, "password", "password123"), null);
        if (response.status() != 201) {
            throw new IllegalStateException("register failed: " + response.status() + " " + response.body());
        }
        return response.text("token");
    }

    private JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? null : json.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("invalid JSON: " + body, ex);
        }
    }
}
