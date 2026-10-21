package com.wordspring.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SyncApiTest extends ApiTestSupport {

    private static final long ANSWERED_AT = 1_700_000_000_000L;
    private static final String LOCAL_DATE = "2026-10-21";

    @Test
    void pushingTheSameAnswerTwiceIsIdempotent() {
        String token = registerAndGetToken(uniqueEmail());

        Response first = push(token, event("answer-1", "card-1", 0));
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.integer("accepted")).isEqualTo(1);

        Response second = push(token, event("answer-1", "card-1", 0));
        assertThat(second.integer("accepted")).isZero();
        assertThat(second.integer("duplicates")).isEqualTo(1);
    }

    @Test
    void pullReturnsCardsChangedSinceCursor() {
        String token = registerAndGetToken(uniqueEmail());
        push(token, event("answer-1", "card-1", 0));

        Response pull = get("/api/v1/sync/pull?since=0", token);

        assertThat(pull.status()).isEqualTo(200);
        assertThat(pull.body().path("cards").size()).isEqualTo(1);
        assertThat(pull.body().path("cards").get(0).path("cardUid").asText()).isEqualTo("card-1");
        assertThat(pull.body().path("cursor").asLong()).isPositive();
    }

    @Test
    void usersOnlySeeTheirOwnCards() {
        String alice = registerAndGetToken(uniqueEmail());
        String bob = registerAndGetToken(uniqueEmail());

        push(alice, event("answer-a", "card-a", 0));
        Response bobPull = get("/api/v1/sync/pull?since=0", bob);

        assertThat(bobPull.body().path("cards").size()).isZero();
    }

    @Test
    void staleCardVersionIsReportedAsConflict() {
        String token = registerAndGetToken(uniqueEmail());
        push(token, event("answer-1", "card-1", 0));

        // The client still believes the card is at version 0, but the server is at 1.
        Response stale = push(token, event("answer-2", "card-1", 0));

        assertThat(stale.status()).isEqualTo(200);
        assertThat(stale.integer("accepted")).isEqualTo(1);
        assertThat(stale.body().path("conflicts").size()).isEqualTo(1);
        assertThat(stale.body().path("conflicts").get(0).path("serverCard").path("version").asLong())
                .isEqualTo(1);
    }

    @Test
    void pushWithoutTokenIsRejected() {
        Response response = post("/api/v1/sync/push",
                Map.of("deviceId", "device-1", "events", List.of(event("answer-x", "card-x", 0))), null);
        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void dailySummaryIsRecomputedFromEvents() {
        String token = registerAndGetToken(uniqueEmail());

        Map<String, Object> again = event("answer-1", "card-1", 0, "AGAIN");
        Map<String, Object> good = event("answer-2", "card-1", 0, "GOOD");
        Response push = post("/api/v1/sync/push",
                Map.of("deviceId", "device-1", "events", List.of(again, good)), token);
        assertThat(push.integer("accepted")).isEqualTo(2);

        Response stats = get("/api/v1/stats/daily?from=2026-10-21&to=2026-10-21", token);

        assertThat(stats.body().size()).isEqualTo(1);
        var day = stats.body().get(0);
        assertThat(day.path("reviewedCount").asInt()).isEqualTo(1);   // one distinct card
        assertThat(day.path("newCount").asInt()).isEqualTo(1);        // first seen today
        assertThat(day.path("correctCount").asInt()).isEqualTo(1);    // GOOD counts once
        assertThat(day.path("studySeconds").asLong()).isEqualTo(6);   // 2 x 3200ms
    }

    private Response push(String token, Map<String, Object> event) {
        return post("/api/v1/sync/push",
                Map.of("deviceId", "device-1", "events", List.of(event)), token);
    }

    private static Map<String, Object> event(String answerId, String cardUid, long version) {
        return event(answerId, cardUid, version, "GOOD");
    }

    private static Map<String, Object> event(String answerId, String cardUid, long version, String grade) {
        return Map.of(
                "answerId", answerId,
                "cardUid", cardUid,
                "bookUid", "cet4",
                "grade", grade,
                "questionType", "RECALL",
                "answeredAt", ANSWERED_AT,
                "localDate", LOCAL_DATE,
                "durationMs", 3200,
                "card", Map.of(
                        "cardUid", cardUid,
                        "bookUid", "cet4",
                        "stage", "REVIEW",
                        "dueAt", ANSWERED_AT + 259_200_000L,
                        "intervalDays", 3.0,
                        "ease", 2.5,
                        "step", 0,
                        "version", version,
                        "deleted", false));
    }

    private static String uniqueEmail() {
        return "sync-" + UUID.randomUUID() + "@example.com";
    }
}
