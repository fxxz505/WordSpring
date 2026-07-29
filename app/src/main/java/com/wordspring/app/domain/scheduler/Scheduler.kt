package com.wordspring.app.domain.scheduler

import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.Stage
import kotlin.math.max
import kotlin.math.min

/**
 * Pure interval-repetition scheduler (M06).
 *
 * - No Android Context, no DB, no system clock. All time comes in as `now` (epoch millis, UTC).
 * - Initial learning steps: 10 min, 1 day; first review after that = 3 days.
 * - AGAIN → LEARNING with 10-min interval, ease floor 1.3.
 * - HARD → at least 1 day, mild growth (~1.2× current interval), ease slightly reduced.
 * - GOOD → grow by current ease.
 * - EASY → at least 4 days, longer interval, ease raised.
 *
 * Spec rule: "保存 UTC 时间戳；以用户当前设备时区划分每日目标；切换时区不得重复生成事件."
 * This module only emits UTC timestamps. Daily-bucket partitioning happens in M07 when writing
 * the daily summary, using the user's device timezone at the moment of the answer.
 */
object Scheduler {

    // ---- Constants (SI: ms) ----
    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 60 * MINUTE_MS
    const val DAY_MS = 24 * HOUR_MS

    const val NEW_EASE = 2.5
    const val MIN_EASE = 1.3
    const val MAX_EASE = 3.2

    const val LEARNING_STEP_0_INTERVAL_MS = 10 * MINUTE_MS
    const val LEARNING_STEP_1_INTERVAL_MS = 1 * DAY_MS
    const val FIRST_REVIEW_INTERVAL_DAYS = 3.0
    const val HARD_MIN_INTERVAL_MS = 1 * DAY_MS
    const val HARD_GROWTH = 1.2
    const val EASY_MIN_INTERVAL_DAYS = 4.0
    const val EASY_INTERVAL_MULTIPLIER = 1.3

    const val EASE_DELTA_HARD = -0.15
    const val EASE_DELTA_GOOD = 0.0
    const val EASE_DELTA_EASY = 0.15
    const val EASE_DELTA_AGAIN = -0.20

    /**
     * Create the initial card state for a brand-new book entry.
     * Stage = NEW, due immediately, ease = NEW_EASE, step = 0.
     */
    fun newCard(bookEntryId: Long, now: Long): ReviewCard = ReviewCard(
        id = 0L,
        bookEntryId = bookEntryId,
        stage = Stage.NEW,
        dueAt = now,
        intervalDays = 0.0,
        ease = NEW_EASE,
        step = 0,
        updatedAt = now
    )

    /**
     * Compute the next card state after a grade. Pure: same inputs → same output.
     *
     * @param card current card state
     * @param grade the answer grade
     * @param now  epoch millis UTC at the moment of answer
     * @param isRelearnRequest true when the user explicitly asked to relearn this card
     *        (e.g. from the word detail screen). Resets to LEARNING step 0 regardless of grade.
     */
    fun next(
        card: ReviewCard,
        grade: Grade,
        now: Long,
        isRelearnRequest: Boolean = false
    ): ReviewCard {
        if (isRelearnRequest) {
            return card.copy(
                stage = Stage.LEARNING,
                dueAt = now + LEARNING_STEP_0_INTERVAL_MS,
                intervalDays = LEARNING_STEP_0_INTERVAL_MS.toDouble() / DAY_MS,
                ease = max(MIN_EASE, min(MAX_EASE, card.ease)),
                step = 0,
                updatedAt = now
            )
        }

        return when (card.stage) {
            Stage.NEW -> scheduleNew(card, grade, now)
            Stage.LEARNING -> scheduleLearning(card, grade, now)
            Stage.REVIEW -> scheduleReview(card, grade, now)
            Stage.SUSPENDED -> card.copy(updatedAt = now) // never auto-advances; stays suspended
        }
    }

    private fun scheduleNew(card: ReviewCard, grade: Grade, now: Long): ReviewCard {
        // First interaction with a new card. AGAIN → LEARNING step 0; anything else → step 1.
        val newEase = clampEase(card.ease + easeDelta(grade))
        return when (grade) {
            Grade.AGAIN -> card.copy(
                stage = Stage.LEARNING,
                dueAt = now + LEARNING_STEP_0_INTERVAL_MS,
                intervalDays = LEARNING_STEP_0_INTERVAL_MS.toDouble() / DAY_MS,
                ease = newEase,
                step = 0,
                updatedAt = now
            )
            Grade.HARD, Grade.GOOD, Grade.EASY -> card.copy(
                stage = Stage.LEARNING,
                dueAt = now + LEARNING_STEP_1_INTERVAL_MS,
                intervalDays = LEARNING_STEP_1_INTERVAL_MS.toDouble() / DAY_MS,
                ease = newEase,
                step = 1,
                updatedAt = now
            )
        }
    }

    private fun scheduleLearning(card: ReviewCard, grade: Grade, now: Long): ReviewCard {
        if (grade == Grade.AGAIN) {
            return card.copy(
                stage = Stage.LEARNING,
                dueAt = now + LEARNING_STEP_0_INTERVAL_MS,
                intervalDays = LEARNING_STEP_0_INTERVAL_MS.toDouble() / DAY_MS,
                ease = clampEase(card.ease + EASE_DELTA_AGAIN),
                step = 0,
                updatedAt = now
            )
        }
        // Non-AGAIN. Advance step.
        return when (card.step) {
            0 -> card.copy(
                stage = Stage.LEARNING,
                dueAt = now + LEARNING_STEP_1_INTERVAL_MS,
                intervalDays = LEARNING_STEP_1_INTERVAL_MS.toDouble() / DAY_MS,
                ease = clampEase(card.ease + easeDelta(grade)),
                step = 1,
                updatedAt = now
            )
            else -> {
                // Step 1 complete → graduate to REVIEW with first interval = 3 days.
                val newEase = clampEase(card.ease + easeDelta(grade))
                val intervalDays = if (grade == Grade.EASY) {
                    max(FIRST_REVIEW_INTERVAL_DAYS * EASY_INTERVAL_MULTIPLIER, EASY_MIN_INTERVAL_DAYS)
                } else {
                    FIRST_REVIEW_INTERVAL_DAYS
                }
                card.copy(
                    stage = Stage.REVIEW,
                    dueAt = now + (intervalDays * DAY_MS).toLong(),
                    intervalDays = intervalDays,
                    ease = newEase,
                    step = 0,
                    updatedAt = now
                )
            }
        }
    }

    private fun scheduleReview(card: ReviewCard, grade: Grade, now: Long): ReviewCard {
        if (grade == Grade.AGAIN) {
            return card.copy(
                stage = Stage.LEARNING,
                dueAt = now + LEARNING_STEP_0_INTERVAL_MS,
                intervalDays = LEARNING_STEP_0_INTERVAL_MS.toDouble() / DAY_MS,
                ease = clampEase(card.ease + EASE_DELTA_AGAIN),
                step = 0,
                updatedAt = now
            )
        }
        val newEase = clampEase(card.ease + easeDelta(grade))
        val currentIntervalMs = (card.intervalDays * DAY_MS).coerceAtLeast(HARD_MIN_INTERVAL_MS.toDouble())
        val nextIntervalMs: Long = when (grade) {
            Grade.HARD -> {
                val candidate = (currentIntervalMs * HARD_GROWTH).toLong()
                maxOf(HARD_MIN_INTERVAL_MS, candidate)
            }
            Grade.GOOD -> {
                val candidate = (currentIntervalMs * newEase).toLong()
                maxOf(HARD_MIN_INTERVAL_MS, candidate)
            }
            Grade.EASY -> {
                val candidate = (currentIntervalMs * newEase * EASY_INTERVAL_MULTIPLIER).toLong()
                val minEasyMs = (EASY_MIN_INTERVAL_DAYS * DAY_MS).toLong()
                maxOf(minEasyMs, candidate)
            }
            Grade.AGAIN -> error("unreachable") // handled above
        }
        return card.copy(
            stage = Stage.REVIEW,
            dueAt = now + nextIntervalMs,
            intervalDays = nextIntervalMs.toDouble() / DAY_MS,
            ease = newEase,
            step = 0,
            updatedAt = now
        )
    }

    /**
     * A card is "due" if its dueAt <= now and it is not SUSPENDED and not NEW with step > 0
     * that hasn't reached its first learning step interval.
     *
     * Spec rule: "AGAIN 未到短间隔时不可立刻重复." → enforce via dueAt comparison.
     */
    fun isDue(card: ReviewCard, now: Long): Boolean {
        if (card.stage == Stage.SUSPENDED) return false
        if (card.stage == Stage.NEW) return true // NEW is always available to be picked up
        return card.dueAt <= now
    }

    /**
     * Whether the card should be considered a NEW pickup (counts against the daily new-word limit).
     */
    fun isNewPickup(card: ReviewCard): Boolean = card.stage == Stage.NEW

    private fun easeDelta(grade: Grade): Double = when (grade) {
        Grade.AGAIN -> EASE_DELTA_AGAIN
        Grade.HARD -> EASE_DELTA_HARD
        Grade.GOOD -> EASE_DELTA_GOOD
        Grade.EASY -> EASE_DELTA_EASY
    }

    private fun clampEase(value: Double): Double =
        max(MIN_EASE, min(MAX_EASE, value))
}
