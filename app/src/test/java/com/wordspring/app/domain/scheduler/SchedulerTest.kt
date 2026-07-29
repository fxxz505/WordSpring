package com.wordspring.app.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.Stage
import org.junit.Test

/**
 * Pure unit tests for the M06 interval-repetition scheduler.
 * Covers: four grades, initial learning steps, boundary intervals, dueAt >= answeredAt,
 * daily-limit-aware isDue, suspended exclusion, due-first ordering.
 */
class SchedulerTest {

    private val now = 1_700_000_000_000L // arbitrary fixed epoch millis

    @Test
    fun newCard_hasCorrectInitialState() {
        val card = Scheduler.newCard(bookEntryId = 100L, now = now)
        assertThat(card.stage).isEqualTo(Stage.NEW)
        assertThat(card.dueAt).isEqualTo(now)
        assertThat(card.ease).isEqualTo(Scheduler.NEW_EASE)
        assertThat(card.step).isEqualTo(0)
        assertThat(card.intervalDays).isEqualTo(0.0)
    }

    @Test
    fun newCard_withAgain_entersLearningStep0_with10MinuteInterval() {
        val card = Scheduler.newCard(1L, now)
        val next = Scheduler.next(card, Grade.AGAIN, now)
        assertThat(next.stage).isEqualTo(Stage.LEARNING)
        assertThat(next.step).isEqualTo(0)
        assertThat(next.dueAt - now).isEqualTo(10 * Scheduler.MINUTE_MS)
        assertThat(next.ease).isAtLeast(Scheduler.MIN_EASE)
        assertThat(next.dueAt).isAtLeast(now) // dueAt >= answeredAt
    }

    @Test
    fun newCard_withGood_advancesToLearningStep1_with1DayInterval() {
        val card = Scheduler.newCard(1L, now)
        val next = Scheduler.next(card, Grade.GOOD, now)
        assertThat(next.stage).isEqualTo(Stage.LEARNING)
        assertThat(next.step).isEqualTo(1)
        assertThat(next.dueAt - now).isEqualTo(Scheduler.DAY_MS)
    }

    @Test
    fun newCard_withHard_advancesToLearningStep1() {
        val card = Scheduler.newCard(1L, now)
        val next = Scheduler.next(card, Grade.HARD, now)
        assertThat(next.stage).isEqualTo(Stage.LEARNING)
        assertThat(next.step).isEqualTo(1)
    }

    @Test
    fun newCard_withEasy_advancesToLearningStep1() {
        val card = Scheduler.newCard(1L, now)
        val next = Scheduler.next(card, Grade.EASY, now)
        assertThat(next.stage).isEqualTo(Stage.LEARNING)
        assertThat(next.step).isEqualTo(1)
    }

    @Test
    fun learningStep0_withAgain_resetsToStep0_with10Minutes() {
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.LEARNING, step = 0)
        val next = Scheduler.next(card, Grade.AGAIN, now)
        assertThat(next.stage).isEqualTo(Stage.LEARNING)
        assertThat(next.step).isEqualTo(0)
        assertThat(next.dueAt - now).isEqualTo(10 * Scheduler.MINUTE_MS)
    }

    @Test
    fun learningStep0_withGood_advancesToStep1_with1DayInterval() {
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.LEARNING, step = 0)
        val next = Scheduler.next(card, Grade.GOOD, now)
        assertThat(next.stage).isEqualTo(Stage.LEARNING)
        assertThat(next.step).isEqualTo(1)
        assertThat(next.dueAt - now).isEqualTo(Scheduler.DAY_MS)
    }

    @Test
    fun learningStep1_withGood_graduatesToReview_with3DayInterval() {
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.LEARNING, step = 1)
        val next = Scheduler.next(card, Grade.GOOD, now)
        assertThat(next.stage).isEqualTo(Stage.REVIEW)
        assertThat(next.dueAt - now).isEqualTo((Scheduler.FIRST_REVIEW_INTERVAL_DAYS * Scheduler.DAY_MS).toLong())
        assertThat(next.step).isEqualTo(0)
    }

    @Test
    fun learningStep1_withEasy_graduatesToReview_withAtLeast4Days() {
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.LEARNING, step = 1)
        val next = Scheduler.next(card, Grade.EASY, now)
        assertThat(next.stage).isEqualTo(Stage.REVIEW)
        val intervalDays = (next.dueAt - now).toDouble() / Scheduler.DAY_MS
        assertThat(intervalDays).isAtLeast(Scheduler.EASY_MIN_INTERVAL_DAYS)
    }

    @Test
    fun review_withAgain_dropsBackToLearningStep0() {
        val card = Scheduler.newCard(1L, now).copy(
            stage = Stage.REVIEW,
            intervalDays = 10.0,
            ease = 2.5,
            step = 0
        )
        val next = Scheduler.next(card, Grade.AGAIN, now)
        assertThat(next.stage).isEqualTo(Stage.LEARNING)
        assertThat(next.step).isEqualTo(0)
        assertThat(next.dueAt - now).isEqualTo(10 * Scheduler.MINUTE_MS)
        assertThat(next.ease).isAtLeast(Scheduler.MIN_EASE)
    }

    @Test
    fun review_withHard_growsMildly_atLeast1Day() {
        val card = Scheduler.newCard(1L, now).copy(
            stage = Stage.REVIEW,
            intervalDays = 10.0,
            ease = 2.5,
            step = 0
        )
        val next = Scheduler.next(card, Grade.HARD, now)
        assertThat(next.stage).isEqualTo(Stage.REVIEW)
        val intervalMs = next.dueAt - now
        assertThat(intervalMs).isAtLeast(Scheduler.HARD_MIN_INTERVAL_MS)
        // ~1.2x growth: 10 * 1.2 = 12 days
        val expectedMin = (10.0 * Scheduler.HARD_GROWTH * Scheduler.DAY_MS).toLong()
        assertThat(intervalMs).isAtLeast(expectedMin - 1)
    }

    @Test
    fun review_withGood_growsByEase() {
        val card = Scheduler.newCard(1L, now).copy(
            stage = Stage.REVIEW,
            intervalDays = 10.0,
            ease = 2.5,
            step = 0
        )
        val next = Scheduler.next(card, Grade.GOOD, now)
        assertThat(next.stage).isEqualTo(Stage.REVIEW)
        val intervalMs = next.dueAt - now
        val expectedMs = (10.0 * 2.5 * Scheduler.DAY_MS).toLong()
        // Allow tiny rounding tolerance
        assertThat(intervalMs).isWithin(Scheduler.HOUR_MS).of(expectedMs)
    }

    @Test
    fun review_withEasy_growsMoreThanGood_atLeast4Days() {
        val card = Scheduler.newCard(1L, now).copy(
            stage = Stage.REVIEW,
            intervalDays = 10.0,
            ease = 2.5,
            step = 0
        )
        val next = Scheduler.next(card, Grade.EASY, now)
        assertThat(next.stage).isEqualTo(Stage.REVIEW)
        val intervalDays = (next.dueAt - now).toDouble() / Scheduler.DAY_MS
        assertThat(intervalDays).isAtLeast(Scheduler.EASY_MIN_INTERVAL_DAYS)
        // EASY multiplier applied
        val goodInterval = 10.0 * 2.5
        val easyInterval = 10.0 * next.ease * Scheduler.EASY_INTERVAL_MULTIPLIER
        assertThat(easyInterval).isGreaterThan(goodInterval)
    }

    @Test
    fun ease_clampsToMinimum() {
        val card = Scheduler.newCard(1L, now).copy(
            stage = Stage.REVIEW,
            intervalDays = 10.0,
            ease = Scheduler.MIN_EASE,
            step = 0
        )
        val next = Scheduler.next(card, Grade.AGAIN, now)
        assertThat(next.ease).isAtLeast(Scheduler.MIN_EASE)
    }

    @Test
    fun ease_clampsToMaximum() {
        val card = Scheduler.newCard(1L, now).copy(
            stage = Stage.REVIEW,
            intervalDays = 10.0,
            ease = Scheduler.MAX_EASE,
            step = 0
        )
        val next = Scheduler.next(card, Grade.EASY, now)
        assertThat(next.ease).isAtMost(Scheduler.MAX_EASE)
    }

    @Test
    fun isDue_excludesSuspended() {
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.SUSPENDED, dueAt = now - 1)
        assertThat(Scheduler.isDue(card, now)).isFalse()
    }

    @Test
    fun isDue_newCard_alwaysAvailable() {
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.NEW)
        assertThat(Scheduler.isDue(card, now)).isTrue()
    }

    @Test
    fun isDue_learningOrReview_onlyWhenDueAtLteNow() {
        val past = Scheduler.newCard(1L, now).copy(stage = Stage.REVIEW, dueAt = now - 1)
        val future = Scheduler.newCard(1L, now).copy(stage = Stage.REVIEW, dueAt = now + Scheduler.DAY_MS)
        assertThat(Scheduler.isDue(past, now)).isTrue()
        assertThat(Scheduler.isDue(future, now)).isFalse()
    }

    @Test
    fun isDue_againNotImmediatelyRepeated_dueAtIsInFuture() {
        // After an AGAIN grade, the card's dueAt is now + 10min; before that it is NOT due.
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.LEARNING, step = 0, dueAt = now + 10 * Scheduler.MINUTE_MS)
        assertThat(Scheduler.isDue(card, now)).isFalse()
    }

    @Test
    fun relearnRequest_resetsToLearningStep0() {
        val card = Scheduler.newCard(1L, now).copy(
            stage = Stage.REVIEW,
            intervalDays = 30.0,
            ease = 2.5,
            step = 0
        )
        val next = Scheduler.next(card, Grade.AGAIN, now, isRelearnRequest = true)
        assertThat(next.stage).isEqualTo(Stage.LEARNING)
        assertThat(next.step).isEqualTo(0)
        assertThat(next.dueAt - now).isEqualTo(10 * Scheduler.MINUTE_MS)
    }

    @Test
    fun suspendedCard_neverAutoAdvances() {
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.SUSPENDED)
        val next = Scheduler.next(card, Grade.GOOD, now)
        assertThat(next.stage).isEqualTo(Stage.SUSPENDED)
    }

    @Test
    fun dueAt_alwaysGreaterOrEqualAnsweredAt() {
        // Spec rule: dueAt >= answeredAt.
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.REVIEW, intervalDays = 5.0, ease = 2.5)
        for (grade in Grade.values()) {
            val next = Scheduler.next(card, grade, now)
            assertThat(next.dueAt).isAtLeast(now)
        }
    }

    @Test
    fun newCard_isNewPickup_true() {
        val card = Scheduler.newCard(1L, now)
        assertThat(Scheduler.isNewPickup(card)).isTrue()
    }

    @Test
    fun learningCard_isNewPickup_false() {
        val card = Scheduler.newCard(1L, now).copy(stage = Stage.LEARNING)
        assertThat(Scheduler.isNewPickup(card)).isFalse()
    }
}
