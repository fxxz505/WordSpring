package com.wordspring.app.domain.usecase

import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.StatsRepository
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.data.repository.SessionRepository
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.domain.model.SessionCardRef
import com.wordspring.app.domain.model.StudySession
import com.wordspring.app.domain.scheduler.Scheduler
import com.wordspring.app.domain.session.SessionPlanner
import com.wordspring.app.domain.study.resolveDailyStudyPlan
import javax.inject.Inject

/**
 * M04 — assemble and persist a study session.
 *
 * Pulls due cards first, then NEW cards respecting per-book daily limits, interleaves
 * question types, and persists a recoverable snapshot.
 */
class StartSessionUseCase @Inject constructor(
    private val contentRepository: ContentRepository,
    private val reviewRepository: ReviewRepository,
    private val sessionRepository: SessionRepository,
    private val statsRepository: StatsRepository
) {

    suspend operator fun invoke(now: Long): StudySession? {
        val enabledBooks = contentRepository.getEnabledBooks()
        if (enabledBooks.isEmpty()) return null

        val bookIds = enabledBooks.map { it.id }

        val todayEpochDay = java.time.LocalDate.now().toEpochDay().toInt()
        val todayStartMillis = TimeUtil.startOfTodayMillis()

        // Due review cards follow the forgetting curve; do not impose a daily review cap.
        val dueCards = reviewRepository.getDueCards(
            bookIds,
            now,
            SessionPlanner.DEFAULT_SESSION_LIMIT
        )

        val allNewCards = reviewRepository.getNewCards(bookIds)

        // Resolve every entry once; due and new candidates share the same lookup.
        val entriesByBook = enabledBooks.associate { it.id to contentRepository.getBookEntries(it.id) }
        val entryByEntryId = entriesByBook.values.flatten().associateBy { it.id }
        val dueCandidates = dueCards.map { c ->
            val entry = entryByEntryId[c.bookEntryId]
            SessionPlanner.CardCandidate(
                cardId = c.id,
                bookEntryId = c.bookEntryId,
                bookId = entry?.bookId ?: 0L,
                wordId = entry?.wordId ?: 0L,
                stage = c.stage,
                dueAt = c.dueAt,
                dailyNewRemainingForBook = 0
            )
        }

        val newQuotaByBookId = enabledBooks.associate { book ->
            val remainingNewWords = allNewCards.count { card -> entryByEntryId[card.bookEntryId]?.bookId == book.id }
            val plan = resolveDailyStudyPlan(
                remainingNewWords = remainingNewWords,
                daysUntilTarget = book.targetEpochDay?.minus(todayEpochDay) ?: 1,
                customNewLimit = book.dailyNewLimit.takeIf { book.targetEpochDay == null }
            )
            val completedNewToday = reviewRepository.getCompletedNewCountForBookSince(book.id, todayStartMillis)
            book.id to plan.remainingNewAllowance(completedNewToday)
        }
        val newCandidates = allNewCards.map { c ->
            val entry = entryByEntryId[c.bookEntryId]
            SessionPlanner.CardCandidate(
                cardId = c.id,
                bookEntryId = c.bookEntryId,
                bookId = entry?.bookId ?: 0L,
                wordId = entry?.wordId ?: 0L,
                stage = c.stage,
                dueAt = c.dueAt,
                dailyNewRemainingForBook = newQuotaByBookId[entry?.bookId] ?: 0
            )
        }

        val queue: List<SessionCardRef> = SessionPlanner.plan(
            dueCards = dueCandidates,
            newCards = newCandidates,
            sessionLimit = SessionPlanner.DEFAULT_SESSION_LIMIT
        )

        if (queue.isEmpty()) return null
        return sessionRepository.createSession(queue, now)
    }
}
