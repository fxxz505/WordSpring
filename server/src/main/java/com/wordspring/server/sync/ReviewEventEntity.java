package com.wordspring.server.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "review_events")
public class ReviewEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "answer_id", nullable = false, length = 64)
    private String answerId;

    @Column(name = "card_uid", nullable = false, length = 64)
    private String cardUid;

    @Column(nullable = false, length = 16)
    private String grade;

    @Column(name = "question_type", nullable = false, length = 16)
    private String questionType;

    @Column(name = "answered_at", nullable = false)
    private long answeredAt;

    @Column(name = "local_date", nullable = false, length = 10)
    private String localDate;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "received_at", nullable = false)
    private long receivedAt;

    protected ReviewEventEntity() {
    }

    public ReviewEventEntity(long userId, String answerId, String cardUid, String grade,
                             String questionType, long answeredAt, String localDate,
                             long durationMs, long receivedAt) {
        this.userId = userId;
        this.answerId = answerId;
        this.cardUid = cardUid;
        this.grade = grade;
        this.questionType = questionType;
        this.answeredAt = answeredAt;
        this.localDate = localDate;
        this.durationMs = durationMs;
        this.receivedAt = receivedAt;
    }

    public String getAnswerId() {
        return answerId;
    }

    public String getCardUid() {
        return cardUid;
    }

    public String getGrade() {
        return grade;
    }

    public long getAnsweredAt() {
        return answeredAt;
    }

    public String getLocalDate() {
        return localDate;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
