package com.wordspring.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WordBook(
    val id: Long,
    val name: String,
    val sourceType: BookSourceType,
    val isEnabled: Boolean,
    val dailyNewLimit: Int,
    val targetEpochDay: Int?,
    val license: String,
    val version: String,
    val updatedAt: Long
)

@Serializable
data class Word(
    val id: Long,
    val spelling: String,
    val normalizedSpelling: String,
    val phonetic: String?,
    val sourceLanguage: String,
    val targetLanguage: String,
    val forms: String? = null
)

@Serializable
data class Definition(
    val id: Long,
    val wordId: Long,
    val partOfSpeech: String?,
    val meaningZh: String,
    val example: String?,
    val translation: String?
)

@Serializable
data class BookEntry(
    val id: Long,
    val bookId: Long,
    val wordId: Long,
    val position: Int
)

/** Learning-state breakdown for a book, used by the home and library dashboards. */
data class BookLearningProgress(
    val bookId: Long,
    val totalCount: Int,
    val masteredCount: Int,
    val learningCount: Int,
    val newCount: Int
) {
    val completedCount: Int get() = masteredCount + learningCount
    val completionFraction: Float
        get() = if (totalCount <= 0) 0f else completedCount.toFloat() / totalCount
}

/**
 * Scheduling state for one book entry. Immutable; updates go through the scheduler.
 */
@Serializable
data class ReviewCard(
    val id: Long,
    val bookEntryId: Long,
    val stage: Stage,
    val dueAt: Long,
    val intervalDays: Double,
    val ease: Double,
    val step: Int,
    val updatedAt: Long
)

/**
 * Immutable record of one answer. Write-once via M07.
 */
@Serializable
data class ReviewEvent(
    val id: Long,
    val answerId: String,
    val cardId: Long,
    val grade: Grade,
    val questionType: QuestionType,
    val answeredAt: Long,
    val durationMs: Long
)

@Serializable
data class UserWordFlag(
    val wordId: Long,
    val isFavorite: Boolean,
    val isDifficult: Boolean,
    val updatedAt: Long
)

@Serializable
data class DailySummary(
    val localDate: String, // yyyy-MM-dd in the user's device timezone
    val reviewedCount: Int,
    val newCount: Int,
    val correctCount: Int,
    val studySeconds: Long
)

enum class ImportJobStatus { PENDING, PARSING, PREVIEWING, COMMITTING, COMPLETED, FAILED, CANCELLED }

@Serializable
data class ImportJob(
    val id: Long,
    val uri: String,
    val status: ImportJobStatus,
    val totalRows: Int,
    val validRows: Int,
    val errorMessage: String?,
    val createdAt: Long
)

enum class SessionStatus { ACTIVE, COMPLETED, ABANDONED }

@Serializable
data class StudySession(
    val id: String,
    val queueSnapshot: List<SessionCardRef>,
    val currentIndex: Int,
    val completedCount: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val status: SessionStatus
)

@Serializable
data class SessionCardRef(
    val cardId: Long,
    val bookEntryId: Long,
    val wordId: Long,
    val questionType: QuestionType
)
