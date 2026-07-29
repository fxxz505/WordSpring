package com.wordspring.app.data.repository

import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.ReviewEvent
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to review state (M06/M07/M11/M12). All writes go through submitAnswer.
 */
interface ReviewRepository {
    suspend fun getDueCardCount(bookIds: List<Long>, now: Long): Int
    suspend fun getNewCardCount(bookIds: List<Long>): Int
    suspend fun getScheduledReviewCardCount(bookIds: List<Long>, now: Long, dayStart: Long): Int
    suspend fun getCompletedScheduledReviewCount(bookIds: List<Long>, dayStart: Long): Int
    suspend fun getCompletedNewCountForBookSince(bookId: Long, since: Long): Int
    suspend fun getDueCards(bookIds: List<Long>, now: Long, limit: Int): List<ReviewCard>
    suspend fun getNewCards(bookIds: List<Long>): List<ReviewCard>

    suspend fun submitAnswer(command: AnswerCommand): AnswerResult

    suspend fun getRecentEventsForCard(cardId: Long, limit: Int): List<ReviewEvent>
    suspend fun getMistakeCardIds(since: Long): List<Long>
    fun observeMistakeCardIds(since: Long): Flow<List<Long>>
    suspend fun getMistakeCards(since: Long, bookIds: List<Long>): List<MistakeCard>

    suspend fun suspendCard(cardId: Long)
    suspend fun relearnCard(cardId: Long, now: Long): ReviewCard
}

/**
 * Immutable command to record one answer. Carries the idempotency key.
 */
data class AnswerCommand(
    val answerId: String,           // UUID, idempotency key
    val cardId: Long,
    val bookEntryId: Long,
    val grade: Grade,
    val questionType: QuestionType,
    val now: Long,                  // epoch millis UTC at the moment of answer
    val durationMs: Long,
    val sessionId: String,
    val isRelearnRequest: Boolean = false
)

sealed interface AnswerResult {
    data class Committed(val event: ReviewEvent, val newCard: ReviewCard) : AnswerResult
    data class AlreadyCommitted(val existingEvent: ReviewEvent, val existingCard: ReviewCard) : AnswerResult
    data class Failed(val reason: String) : AnswerResult
}

data class MistakeCard(
    val cardId: Long,
    val bookEntryId: Long,
    val wordId: Long,
    val bookId: Long,
    val lastWrongAt: Long,
    val spelling: String = "",
    val meaningZh: String? = null
)
