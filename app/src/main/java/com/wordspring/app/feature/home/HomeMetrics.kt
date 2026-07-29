package com.wordspring.app.feature.home

/**
 * Keeps completed learning history separate from cards that can still enter a session.
 */
internal data class HomeMetrics(
    val completedNewToday: Int,
    val pendingCount: Int,
    val etaMinutes: Int,
    val allDone: Boolean,
    val startEnabled: Boolean
)

internal data class TodayPlanTargets(
    val newTarget: Int,
    val reviewTarget: Int
)

/** Targets reflect today's full workload, never only what is already completed. */
internal fun resolveTodayPlanTargets(
    plannedNewCount: Int,
    newCompletedToday: Int,
    dueReviewCount: Int,
    reviewsCompletedToday: Int
): TodayPlanTargets = TodayPlanTargets(
    newTarget = plannedNewCount.coerceAtLeast(newCompletedToday.coerceAtLeast(0)),
    reviewTarget = dueReviewCount.coerceAtLeast(0) + reviewsCompletedToday.coerceAtLeast(0)
)

internal fun resolveHomeMetrics(
    dueCount: Int,
    availableNewCount: Int,
    completedNewToday: Int
): HomeMetrics {
    val pendingCount = (dueCount.coerceAtLeast(0) + availableNewCount.coerceAtLeast(0))
    return HomeMetrics(
        completedNewToday = completedNewToday.coerceAtLeast(0),
        pendingCount = pendingCount,
        etaMinutes = ((pendingCount * 10 + 59) / 60).coerceAtLeast(1),
        allDone = pendingCount == 0,
        startEnabled = pendingCount > 0
    )
}
