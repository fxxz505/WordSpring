package com.wordspring.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthApiTest extends ApiTestSupport {

    @Test
    void registerReturnsToken() {
        String email = uniqueEmail();
        Response response = post("/api/v1/auth/register",
                Map.of("email", email, "password", "password123"), null);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.text("token")).isNotBlank();
        assertThat(response.text("email")).isEqualTo(email);
    }

    @Test
    void duplicateEmailIsRejected() {
        String email = uniqueEmail();
        registerAndGetToken(email);

        Response second = post("/api/v1/auth/register",
                Map.of("email", email, "password", "password123"), null);

        assertThat(second.status()).isEqualTo(409);
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        String email = uniqueEmail();
        registerAndGetToken(email);

        Response response = post("/api/v1/auth/login",
                Map.of("email", email, "password", "wrong-password"), null);

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        String email = uniqueEmail();
        registerAndGetToken(email);

        Response response = post("/api/v1/auth/login",
                Map.of("email", email, "password", "password123"), null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("token")).isNotBlank();
    }

    @Test
    void protectedEndpointRequiresToken() {
        Response response = get("/api/v1/sync/pull?since=0", null);
        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void invalidEmailIsRejectedByValidation() {
        Response response = post("/api/v1/auth/register",
                Map.of("email", "not-an-email", "password", "password123"), null);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().path("fields").has("email")).isTrue();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
