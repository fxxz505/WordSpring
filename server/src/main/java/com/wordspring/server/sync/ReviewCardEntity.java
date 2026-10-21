package com.wordspring.server.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "review_cards")
public class ReviewCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "card_uid", nullable = false, length = 64)
    private String cardUid;

    @Column(name = "book_uid", nullable = false, length = 64)
    private String bookUid;

    @Column(nullable = false, length = 16)
    private String stage;

    @Column(name = "due_at", nullable = false)
    private long dueAt;

    @Column(name = "interval_days", nullable = false)
    private double intervalDays;

    @Column(nullable = false)
    private double ease;

    @Column(nullable = false)
    private int step;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private boolean deleted;

    protected ReviewCardEntity() {
    }

    public ReviewCardEntity(long userId, String cardUid, String bookUid) {
        this.userId = userId;
        this.cardUid = cardUid;
        this.bookUid = bookUid;
    }

    public void apply(String bookUid, String stage, long dueAt, double intervalDays,
                      double ease, int step, long updatedAt, boolean deleted) {
        this.bookUid = bookUid;
        this.stage = stage;
        this.dueAt = dueAt;
        this.intervalDays = intervalDays;
        this.ease = ease;
        this.step = step;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getCardUid() {
        return cardUid;
    }

    public String getBookUid() {
        return bookUid;
    }

    public String getStage() {
        return stage;
    }

    public long getDueAt() {
        return dueAt;
    }

    public double getIntervalDays() {
        return intervalDays;
    }

    public double getEase() {
        return ease;
    }

    public int getStep() {
        return step;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
