package com.wordspring.app.data.repository

import androidx.room.withTransaction
import com.wordspring.app.data.Mappers.toDomain
import com.wordspring.app.data.Mappers.toEntity
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.db.BookEntryDao
import com.wordspring.app.data.db.DailySummaryDao
import com.wordspring.app.data.db.DefinitionDao
import com.wordspring.app.data.db.ReviewCardDao
import com.wordspring.app.data.db.ReviewEventDao
import com.wordspring.app.data.db.StudySessionDao
import com.wordspring.app.data.db.WordDao
import com.wordspring.app.data.db.WordSpringDatabase
import com.wordspring.app.data.db.UserWordFlagDao
import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.ReviewEvent
import com.wordspring.app.domain.model.Stage
import com.wordspring.app.domain.scheduler.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val db: WordSpringDatabase,
    private val reviewCardDao: ReviewCardDao,
    private val reviewEventDao: ReviewEventDao,
    private val dailySummaryDao: DailySummaryDao,
    private val studySessionDao: StudySessionDao,
    private val bookEntryDao: BookEntryDao,
    private val userWordFlagDao: UserWordFlagDao,
    private val wordDao: WordDao,
    private val definitionDao: DefinitionDao
) : ReviewRepository {

    override suspend fun getDueCardCount(bookIds: List<Long>, now: Long): Int =
        if (bookIds.isEmpty()) 0 else reviewCardDao.countDue(bookIds, now)

    override suspend fun getNewCardCount(bookIds: List<Long>): Int =
        if (bookIds.isEmpty()) 0 else reviewCardDao.countNew(bookIds)

    override suspend fun getScheduledReviewCardCount(bookIds: List<Long>, now: Long, dayStart: Long): Int =
        if (bookIds.isEmpty()) 0 else reviewCardDao.countScheduledReviewDue(bookIds, now, dayStart)

    override suspend fun getCompletedScheduledReviewCount(bookIds: List<Long>, dayStart: Long): Int =
        if (bookIds.isEmpty()) 0 else reviewEventDao.countCompletedScheduledReviews(bookIds, dayStart)

    override suspend fun getCompletedNewCountForBookSince(bookId: Long, since: Long): Int =
        reviewEventDao.countFirstSuccessfulAnswersForBookSince(bookId, since)

    override suspend fun getDueCards(bookIds: List<Long>, now: Long, limit: Int): List<ReviewCard> =
        if (bookIds.isEmpty()) emptyList() else reviewCardDao.getDueCards(bookIds, now, limit).map { it.toDomain() }

    override suspend fun getNewCards(bookIds: List<Long>): List<ReviewCard> =
        if (bookIds.isEmpty()) emptyList() else reviewCardDao.getNewCards(bookIds).map { it.toDomain() }

    /**
     * M07 — atomic, idempotent answer submission.
     *
     * Single Room transaction:
     *   1. Idempotency check by answerId. If present → return AlreadyCommitted.
     *   2. Insert review_events row (immutable).
     *   3. Update review_cards row using Scheduler.next (pure).
     *   4. Upsert daily_summaries row (increment counters).
     *   5. Advance study_sessions cursor (if sessionId present).
     *
     * Any failure rolls back the entire transaction. Cannot end up half-written.
     */
    override suspend fun submitAnswer(command: AnswerCommand): AnswerResult {
        return try {
            db.withTransaction {
                // 1. Idempotency
                val existing = reviewEventDao.countByAnswerId(command.answerId)
                if (existing > 0) {
                    val eventEntity = reviewEventDao.getForCard(command.cardId, Int.MAX_VALUE)
                        .first { it.answerId == command.answerId }
                    val cardEntity = reviewCardDao.getById(command.cardId)!!
                    return@withTransaction AnswerResult.AlreadyCommitted(
                        existingEvent = eventEntity.toDomain(),
                        existingCard = cardEntity.toDomain()
                    )
                }

                val currentCardEntity = reviewCardDao.getById(command.cardId)
                    ?: return@withTransaction AnswerResult.Failed("找不到卡片")
                val currentCard = currentCardEntity.toDomain()

                val nextCard: ReviewCard = Scheduler.next(
                    card = currentCard,
                    grade = command.grade,
                    now = command.now,
                    isRelearnRequest = command.isRelearnRequest
                )

                // 2. Insert immutable event
                val event = ReviewEvent(
                    id = 0L,
                    answerId = command.answerId,
                    cardId = command.cardId,
                    grade = command.grade,
                    questionType = command.questionType,
                    answeredAt = command.now,
                    durationMs = command.durationMs
                )
                val newEventId = reviewEventDao.insert(event.toEntity())
                val savedEvent = event.copy(id = newEventId)

                // 3. Update card
                val updatedCard = nextCard.copy(id = currentCard.id, updatedAt = command.now)
                reviewCardDao.update(updatedCard.toEntity())

                // 4. Update daily summary (idempotent at day level because answerId dedups events)
                val today = TimeUtil.localDateString(command.now)
                val currentSummary = dailySummaryDao.getByDate(today)
                val wasNew = currentCard.stage == Stage.NEW && command.grade != Grade.AGAIN
                val isCorrect = command.grade != Grade.AGAIN
                val newSummary = if (currentSummary == null) {
                    com.wordspring.app.data.db.DailySummaryEntity(
                        localDate = today,
                        reviewedCount = 1,
                        newCount = if (wasNew) 1 else 0,
                        correctCount = if (isCorrect) 1 else 0,
                        studySeconds = command.durationMs / 1000
                    )
                } else {
                    currentSummary.copy(
                        reviewedCount = currentSummary.reviewedCount + 1,
                        newCount = currentSummary.newCount + (if (wasNew) 1 else 0),
                        correctCount = currentSummary.correctCount + (if (isCorrect) 1 else 0),
                        studySeconds = currentSummary.studySeconds + (command.durationMs / 1000)
                    )
                }
                dailySummaryDao.upsert(newSummary)

                // 5. Advance session cursor
                // For AGAIN/HARD: still advance the cursor (move to next card) but
                // do NOT increment completedCount (correct count). The card will be
                // re-queued for a later round in the same session. Matches the
                // "不背单词" style: 答错后切到下一张但正确数不变。
                if (command.sessionId.isNotBlank()) {
                    val session = studySessionDao.getById(command.sessionId)
                    if (session != null && session.status == "ACTIVE") {
                        val isAgainOrHard = command.grade == Grade.AGAIN || command.grade == Grade.HARD
                        val nextIndex = session.currentIndex + 1
                        val completed = if (isAgainOrHard) session.completedCount else session.completedCount + 1
                        studySessionDao.updateProgress(
                            id = command.sessionId,
                            index = nextIndex,
                            completed = completed,
                            status = "ACTIVE",
                            endedAt = null
                        )
                    }
                }

                AnswerResult.Committed(event = savedEvent, newCard = updatedCard)
            }
        } catch (t: Throwable) {
            AnswerResult.Failed(t.message ?: "保存失败")
        }
    }

    override suspend fun getRecentEventsForCard(cardId: Long, limit: Int): List<ReviewEvent> =
        reviewEventDao.getForCard(cardId, limit).map { it.toDomain() }

    override suspend fun getMistakeCardIds(since: Long): List<Long> =
        reviewEventDao.getMistakeCardIds(since)

    override fun observeMistakeCardIds(since: Long): Flow<List<Long>> =
        reviewEventDao.observeMistakeCardIds(since)

    override suspend fun getMistakeCards(since: Long, bookIds: List<Long>): List<MistakeCard> {
        val cardIds = reviewEventDao.getMistakeCardIds(since)
        if (cardIds.isEmpty()) return emptyList()
        val result = ArrayList<MistakeCard>(cardIds.size)
        val recentByCard = HashMap<Long, Long>()
        for (id in cardIds) {
            val events = reviewEventDao.getForCard(id, 1)
            if (events.isNotEmpty()) {
                recentByCard[id] = events.first().answeredAt
            }
        }
        for (cardId in cardIds) {
            val card = reviewCardDao.getById(cardId) ?: continue
            val entry = bookEntryDao.getById(card.bookEntryId) ?: continue
            if (bookIds.isNotEmpty() && entry.bookId !in bookIds) continue
            // Fetch word spelling and primary definition for display.
            val word = wordDao.getById(entry.wordId)
            val definitions = definitionDao.getByWord(entry.wordId)
            val primaryMeaning = definitions.firstOrNull()?.meaningZh
            result += MistakeCard(
                cardId = cardId,
                bookEntryId = card.bookEntryId,
                wordId = entry.wordId,
                bookId = entry.bookId,
                lastWrongAt = recentByCard[cardId] ?: since,
                spelling = word?.spelling ?: "",
                meaningZh = primaryMeaning
            )
        }
        return result
    }

    override suspend fun suspendCard(cardId: Long) {
        reviewCardDao.suspend(cardId, System.currentTimeMillis())
    }

    override suspend fun relearnCard(cardId: Long, now: Long): ReviewCard {
        val current = reviewCardDao.getById(cardId)?.toDomain()
            ?: return Scheduler.newCard(0L, now)
        val next = Scheduler.next(current, Grade.AGAIN, now, isRelearnRequest = true)
        // Persist via the same atomic path (with a synthetic answerId so it's idempotent)
        val synthetic = AnswerCommand(
            answerId = "relearn-${cardId}-$now",
            cardId = cardId,
            bookEntryId = current.bookEntryId,
            grade = Grade.AGAIN,
            questionType = QuestionType.RECALL,
            now = now,
            durationMs = 0L,
            sessionId = "",
            isRelearnRequest = true
        )
        val result = submitAnswer(synthetic)
        return when (result) {
            is AnswerResult.Committed -> result.newCard
            is AnswerResult.AlreadyCommitted -> result.existingCard
            is AnswerResult.Failed -> next
        }
    }
}
