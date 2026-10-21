package com.wordspring.server.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wordspring.server.sync.SyncDtos.CardSnapshot;
import com.wordspring.server.sync.SyncDtos.Conflict;
import com.wordspring.server.sync.SyncDtos.EventItem;
import com.wordspring.server.sync.SyncDtos.PullResponse;
import com.wordspring.server.sync.SyncDtos.PushRequest;
import com.wordspring.server.sync.SyncDtos.PushResponse;

/**
 * Server side of the sync protocol.
 *
 * <p>Design: the client owns the spaced-repetition algorithm (it already has a pure
 * {@code Scheduler}) and pushes immutable answer events plus the resulting card snapshot.
 * The server is the shared, append-only log; card rows are a projection that any device
 * can pull to rebuild its local Room database.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Event replay is idempotent — {@code (user_id, answer_id)} is unique.</li>
 *   <li>Card writes are guarded by a client-visible version; stale writes return a conflict
 *       with the current server state instead of silently overwriting.</li>
 *   <li>Daily summaries are always recomputed from the event log, never incremented.</li>
 * </ul>
 */
@Service
public class SyncService {

    private final ReviewCardRepository cards;
    private final ReviewEventRepository events;
    private final DailySummaryRepository summaries;

    public SyncService(ReviewCardRepository cards,
                       ReviewEventRepository events,
                       DailySummaryRepository summaries) {
        this.cards = cards;
        this.events = events;
        this.summaries = summaries;
    }

    @Transactional
    public PushResponse push(long userId, PushRequest request) {
        long now = System.currentTimeMillis();
        int accepted = 0;
        int duplicates = 0;
        Set<String> affectedDates = new TreeSet<>();

        // Latest snapshot per card, so several answers for one card in a batch
        // collapse into a single write.
        Map<String, CardSnapshot> latestPerCard = new LinkedHashMap<>();

        for (EventItem item : request.events()) {
            if (events.existsByUserIdAndAnswerId(userId, item.answerId())) {
                duplicates++;
                continue;
            }
            events.save(new ReviewEventEntity(userId, item.answerId(), item.cardUid(),
                    item.grade(), item.questionType(), item.answeredAt(),
                    item.localDate(), item.durationMs(), now));
            affectedDates.add(item.localDate());
            accepted++;
            if (item.card() != null) {
                latestPerCard.put(item.card().cardUid(), item.card());
            }
        }

        List<Conflict> conflicts = new ArrayList<>();
        for (CardSnapshot snapshot : latestPerCard.values()) {
            Conflict conflict = applySnapshot(userId, snapshot, now);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }

        for (String date : affectedDates) {
            recomputeSummary(userId, date, now);
        }

        return new PushResponse(accepted, duplicates, List.copyOf(conflicts), now);
    }

    @Transactional(readOnly = true)
    public PullResponse pull(long userId, long since) {
        List<ReviewCardEntity> changed =
                cards.findByUserIdAndUpdatedAtGreaterThanOrderByUpdatedAtAsc(userId, since);
        long cursor = changed.isEmpty()
                ? since
                : changed.get(changed.size() - 1).getUpdatedAt();
        return new PullResponse(changed.stream().map(SyncService::toSnapshot).toList(), cursor);
    }

    private Conflict applySnapshot(long userId, CardSnapshot snapshot, long now) {
        ReviewCardEntity card =
                cards.findByUserIdAndCardUid(userId, snapshot.cardUid()).orElse(null);

        if (card == null) {
            card = new ReviewCardEntity(userId, snapshot.cardUid(), snapshot.bookUid());
            card.apply(snapshot.bookUid(), snapshot.stage(), snapshot.dueAt(),
                    snapshot.intervalDays(), snapshot.ease(), snapshot.step(),
                    now, snapshot.deleted());
            card.setVersion(snapshot.version());
            cards.save(card);
            return null;
        }

        if (snapshot.version() < card.getVersion()) {
            return new Conflict(snapshot.cardUid(), toSnapshot(card));
        }

        // The client version acts as a logical clock: accept equal or newer writes and
        // store the client's value, so both sides converge without an extra round trip.
        card.apply(snapshot.bookUid(), snapshot.stage(), snapshot.dueAt(),
                snapshot.intervalDays(), snapshot.ease(), snapshot.step(),
                now, snapshot.deleted());
        card.setVersion(snapshot.version());
        cards.save(card);
        return null;
    }

    private void recomputeSummary(long userId, String localDate, long now) {
        DailySummaryEntity summary = summaries
                .findByUserIdAndLocalDate(userId, localDate)
                .orElseGet(() -> new DailySummaryEntity(userId, localDate));
        summary.apply(
                events.countDistinctCards(userId, localDate),
                events.countCardsFirstSeenOn(userId, localDate),
                events.countDistinctCorrectCards(userId, localDate),
                events.sumDurationMs(userId, localDate) / 1000,
                now);
        summaries.save(summary);
    }

    private static CardSnapshot toSnapshot(ReviewCardEntity card) {
        return new CardSnapshot(card.getCardUid(), card.getBookUid(), card.getStage(),
                card.getDueAt(), card.getIntervalDays(), card.getEase(), card.getStep(),
                card.getVersion(), card.isDeleted());
    }
}
