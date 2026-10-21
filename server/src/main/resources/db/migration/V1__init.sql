-- WordSpring sync backend schema (MySQL 8 / H2 MySQL mode).
-- All timestamps are UTC epoch milliseconds, matching the Android client.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    BIGINT       NOT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_users_email ON users (email);

CREATE TABLE review_cards (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    card_uid      VARCHAR(64)  NOT NULL,
    book_uid      VARCHAR(64)  NOT NULL,
    stage         VARCHAR(16)  NOT NULL,
    due_at        BIGINT       NOT NULL,
    interval_days DOUBLE       NOT NULL,
    ease          DOUBLE       NOT NULL,
    step          INT          NOT NULL,
    updated_at    BIGINT       NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cards_user_card ON review_cards (user_id, card_uid);
CREATE INDEX idx_cards_user_updated ON review_cards (user_id, updated_at);

CREATE TABLE review_events (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    answer_id     VARCHAR(64) NOT NULL,
    card_uid      VARCHAR(64) NOT NULL,
    grade         VARCHAR(16) NOT NULL,
    question_type VARCHAR(16) NOT NULL,
    answered_at   BIGINT      NOT NULL,
    local_date    VARCHAR(10) NOT NULL,
    duration_ms   BIGINT      NOT NULL,
    received_at   BIGINT      NOT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_events_user_answer ON review_events (user_id, answer_id);
CREATE INDEX idx_events_user_date ON review_events (user_id, local_date);

CREATE TABLE daily_summaries (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    local_date     VARCHAR(10) NOT NULL,
    reviewed_count INT         NOT NULL,
    new_count      INT         NOT NULL,
    correct_count  INT         NOT NULL,
    study_seconds  BIGINT      NOT NULL,
    updated_at     BIGINT      NOT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_summary_user_date ON daily_summaries (user_id, local_date);
