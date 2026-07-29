package com.wordspring.app.data.repository

import com.wordspring.app.data.Mappers.toDomain
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.db.DailySummaryDao
import com.wordspring.app.data.db.ReviewEventDao
import com.wordspring.app.domain.model.DailySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val dailySummaryDao: DailySummaryDao,
    private val reviewEventDao: ReviewEventDao
) : StatsRepository {

    override suspend fun getToday(): DailySummary? =
        dailySummaryDao.getByDate(TimeUtil.todayLocalDate())?.toDomain()

    override fun observeRange(start: String, end: String): Flow<List<DailySummary>> =
        dailySummaryDao.observeBetween(start, end).map { list -> list.map { it.toDomain() } }

    override suspend fun getRange(start: String, end: String): List<DailySummary> =
        dailySummaryDao.getBetween(start, end).map { it.toDomain() }

    override suspend fun countCorrectSince(since: Long): Int = reviewEventDao.countCorrectSince(since)
    override suspend fun countReviewedSince(since: Long): Int = reviewEventDao.countSince(since)

    override suspend fun getActiveDates(): List<String> {
        // Active day = a day with reviewedCount > 0
        return dailySummaryDao.getBetween("1970-01-01", "2999-12-31")
            .filter { it.reviewedCount > 0 }
            .map { it.localDate }
    }

    override suspend fun getStreak(today: String): Int {
        val dates = getActiveDates()
        return TimeUtil.computeStreak(dates, today)
    }
}
