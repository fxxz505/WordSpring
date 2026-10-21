package com.wordspring.server.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "daily_summaries")
public class DailySummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "local_date", nullable = false, length = 10)
    private String localDate;

    @Column(name = "reviewed_count", nullable = false)
    private int reviewedCount;

    @Column(name = "new_count", nullable = false)
    private int newCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "study_seconds", nullable = false)
    private long studySeconds;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    protected DailySummaryEntity() {
    }

    public DailySummaryEntity(long userId, String localDate) {
        this.userId = userId;
        this.localDate = localDate;
    }

    public void apply(int reviewedCount, int newCount, int correctCount,
                      long studySeconds, long updatedAt) {
        this.reviewedCount = reviewedCount;
        this.newCount = newCount;
        this.correctCount = correctCount;
        this.studySeconds = studySeconds;
        this.updatedAt = updatedAt;
    }

    public long getUserId() {
        return userId;
    }

    public String getLocalDate() {
        return localDate;
    }

    public int getReviewedCount() {
        return reviewedCount;
    }

    public int getNewCount() {
        return newCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public long getStudySeconds() {
        return studySeconds;
    }
}
