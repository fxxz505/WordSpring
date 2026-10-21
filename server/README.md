# WordSpring Server

Sync backend for the WordSpring Android app: Spring Boot 4.1 + MySQL 8 + JPA + Flyway + JWT.

The Android app stays **offline-first**. The server is not required to study; it only lets a
user's progress follow them across devices.

## Run

```bash
# Dev: in-memory H2 (MySQL compatibility mode), schema created by Flyway
mvn spring-boot:run

# Prod-like: MySQL from docker-compose
docker compose up -d mysql
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Environment overrides: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
`JWT_SECRET` (must be at least 32 bytes).

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | – | Create account, returns JWT |
| POST | `/api/v1/auth/login` | – | Returns JWT |
| POST | `/api/v1/sync/push` | Bearer | Upload answer events + card snapshots |
| GET | `/api/v1/sync/pull?since=` | Bearer | Cards changed after a cursor |
| GET | `/api/v1/stats/daily?from=&to=` | Bearer | Daily summaries |

### Register

```bash
curl -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@example.com","password":"password123"}'
# 201 {"token":"...","userId":1,"email":"a@example.com"}
```

### Push

```json
{
  "deviceId": "pixel-8",
  "events": [
    {
      "answerId": "2f1c...",          // UUID generated per answer; replay-safe
      "cardUid": "cet4:01234",
      "bookUid": "cet4",
      "grade": "GOOD",
      "questionType": "RECALL",
      "answeredAt": 1700000000000,
      "localDate": "2026-10-21",      // computed in the device timezone
      "durationMs": 3200,
      "card": {
        "cardUid": "cet4:01234",
        "bookUid": "cet4",
        "stage": "REVIEW",
        "dueAt": 1700259200000,
        "intervalDays": 3.0,
        "ease": 2.5,
        "step": 0,
        "version": 3,
        "deleted": false
      }
    }
  ]
}
```

Response:

```json
{"accepted":1,"duplicates":0,"conflicts":[],"serverTime":1700000001234}
```

## Design decisions

**The server does not implement spaced repetition.** The Android client already has a pure,
well-tested `Scheduler`. Duplicating that logic server-side would create two sources of truth
and a whole class of "which one is right" bugs. Instead the client pushes the *resulting card
state* and the server stores it as a projection.

**Events are the source of truth; cards are a projection.** `review_events` is append-only and
unique on `(user_id, answer_id)`, so replaying a batch is harmless. Card rows and daily
summaries can always be rebuilt from events.

**Conflicts are returned, not resolved silently.** Each card carries a `version`. A client that
writes with a stale version gets the current server card back in `conflicts` and can rebase
(pull, re-apply, push again).

**Daily summaries are recomputed, never incremented.** `reviewedCount` counts distinct cards,
`newCount` counts cards first seen that day, `correctCount` counts distinct cards with at least
one non-`AGAIN` answer. Deriving them from events makes a retried push idempotent.

## Known limitations

- `pull` uses `updated_at > since`; two writes to different cards in the same millisecond could
  be skipped on the next pull. Fix: add a monotonic `change_seq` column and cursor on it.
- No refresh tokens; the JWT lives 7 days and the client re-logs in.
- No rate limiting or account lockout yet.
- The catalog (books/words) stays on the device; the server only stores progress keyed by a
  client-generated `cardUid`.

## Tests

```bash
mvn test
```

13 tests cover registration, duplicate email, wrong password, validation, unauthenticated
access, push idempotency, cursor pull, per-user isolation, version conflicts and summary
recomputation.
