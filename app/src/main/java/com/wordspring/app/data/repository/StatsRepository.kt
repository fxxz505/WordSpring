package com.wordspring.app.data.repository

import com.wordspring.app.domain.model.DailySummary
import kotlinx.coroutines.flow.Flow

/**
 * M12 statistics — read-only over events and summaries. Must not change learning state.
 */
interface StatsRepository {
    suspend fun getToday(): DailySummary?
    fun observeRange(start: String, end: String): Flow<List<DailySummary>>
    suspend fun getRange(start: String, end: String): List<DailySummary>
    suspend fun countCorrectSince(since: Long): Int
    suspend fun countReviewedSince(since: Long): Int
    suspend fun getActiveDates(): List<String>
    suspend fun getStreak(today: String): Int
}

data class StatsSnapshot(
    val today: DailySummary?,
    val last7: List<DailySummary>,
    val last30: List<DailySummary>,
    val streakDays: Int,
    val reviewed7: Int,
    val correct7: Int,
    val new7: Int,
    val reviewed30: Int,
    val correct30: Int,
    val new30: Int
)
