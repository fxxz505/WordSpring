package com.wordspring.app.domain.study

data class DailyStudyPlan(
    val recommendedNewLimit: Int,
    val newLimit: Int
) {
    fun remainingNewAllowance(completedNewToday: Int): Int =
        (newLimit - completedNewToday).coerceAtLeast(0)

    /** Reviews follow their scheduled due time and are never restricted by a daily quota. */
    val reviewLimit: Int get() = Int.MAX_VALUE
}

fun resolveDailyStudyPlan(
    remainingNewWords: Int,
    daysUntilTarget: Int,
    customNewLimit: Int?
): DailyStudyPlan {
    val recommended = if (remainingNewWords <= 0) 0 else {
        val safeDays = daysUntilTarget.coerceAtLeast(1)
        // Round up so the remaining words can be completed by the selected date.
        (remainingNewWords + safeDays - 1) / safeDays
    }
    return DailyStudyPlan(
        recommendedNewLimit = recommended,
        newLimit = (customNewLimit ?: recommended).coerceAtLeast(0)
    )
}
