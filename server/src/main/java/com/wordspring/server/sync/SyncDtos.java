package com.wordspring.server.sync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class SyncDtos {

    private SyncDtos() {
    }

    public record PushRequest(
            @NotBlank String deviceId,
            @NotNull @Size(max = 500) List<@Valid EventItem> events) {
    }

    /**
     * One answered card. The client computes the next state with its pure
     * Scheduler and sends the resulting snapshot so the server can store it
     * without duplicating the spaced-repetition algorithm.
     */
    public record EventItem(
            @NotBlank @Size(max = 64) String answerId,
            @NotBlank @Size(max = 64) String cardUid,
            @NotBlank @Size(max = 64) String bookUid,
            @NotBlank String grade,
            @NotBlank String questionType,
            long answeredAt,
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String localDate,
            long durationMs,
            @Valid CardSnapshot card) {
    }

    public record CardSnapshot(
            @NotBlank @Size(max = 64) String cardUid,
            @NotBlank @Size(max = 64) String bookUid,
            @NotBlank String stage,
            long dueAt,
            double intervalDays,
            double ease,
            int step,
            long version,
            boolean deleted) {
    }

    public record Conflict(String cardUid, CardSnapshot serverCard) {
    }

    public record PushResponse(int accepted, int duplicates, List<Conflict> conflicts,
                               long serverTime) {
    }

    public record PullResponse(List<CardSnapshot> cards, long cursor) {
    }

    public record DailySummary(String localDate, int reviewedCount, int newCount,
                               int correctCount, long studySeconds) {
    }
}
