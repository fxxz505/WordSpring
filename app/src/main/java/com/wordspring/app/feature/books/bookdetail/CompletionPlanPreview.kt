package com.wordspring.app.feature.books.bookdetail

data class CompletionPlanPreview(
    val daysRemaining: Int,
    val dailyNewWords: Int
)

internal fun completionPlanPreview(
    remainingNewWords: Int,
    todayEpochDay: Int,
    targetEpochDay: Int
): CompletionPlanPreview {
    val days = (targetEpochDay - todayEpochDay).coerceAtLeast(1)
    return CompletionPlanPreview(
        daysRemaining = days,
        dailyNewWords = (remainingNewWords.coerceAtLeast(0) + days - 1) / days
    )
}
