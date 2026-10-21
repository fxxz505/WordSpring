package com.wordspring.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wordspring.app.domain.model.BookSourceType

@Entity(
    tableName = "word_books",
    indices = [Index(value = ["name"], unique = false)]
)
data class WordBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val sourceType: String, // BookSourceType.name()
    val isEnabled: Boolean,
    val dailyNewLimit: Int,
    val targetEpochDay: Int?,
    val license: String,
    val version: String,
    val updatedAt: Long
)

@Entity(
    tableName = "words",
    indices = [Index(value = ["normalizedSpelling"], name = "idx_words_normalized")]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val spelling: String,
    val normalizedSpelling: String,
    val phonetic: String?,
    val sourceLanguage: String,
    val targetLanguage: String,
    /** Inflected forms in ECDICT exchange style, e.g. "p:ran/d:run/i:running/3:runs". */
    val forms: String? = null
)

@Entity(
    tableName = "definitions",
    indices = [Index(value = ["wordId"])]
)
data class DefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val wordId: Long,
    val partOfSpeech: String?,
    val meaningZh: String,
    val example: String?,
    val translation: String?,
    /** [DefinitionSource.BUILT_IN] for rows owned by the built-in word books; null for user data. */
    val source: String? = null
)

object DefinitionSource {
    const val BUILT_IN = "BUILT_IN"
}

@Entity(
    tableName = "book_entries",
    indices = [
        Index(value = ["bookId", "wordId"], unique = true),
        Index(value = ["bookId", "position"])
    ]
)
data class BookEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val wordId: Long,
    val position: Int
)

@Entity(
    tableName = "review_cards",
    indices = [
        Index(value = ["bookEntryId"], unique = true),
        Index(value = ["stage", "dueAt"], name = "idx_review_cards_stage_due")
    ]
)
data class ReviewCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookEntryId: Long,
    val stage: String, // Stage.name()
    val dueAt: Long,
    val intervalDays: Double,
    val ease: Double,
    val step: Int,
    val updatedAt: Long,
    /** Monotonic version used by cloud sync for optimistic concurrency. */
    val version: Long = 0L
)

@Entity(
    tableName = "review_events",
    indices = [
        Index(value = ["answerId"], unique = true),
        Index(value = ["cardId", "answeredAt"], name = "idx_review_events_card_answered")
    ]
)
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val answerId: String,
    val cardId: Long,
    val grade: String, // Grade.name()
    val questionType: String, // QuestionType.name()
    val answeredAt: Long,
    val durationMs: Long,
    /** False until the event has been accepted by the sync backend. */
    val synced: Boolean = false
)

@Entity(
    tableName = "user_word_flags",
    indices = [Index(value = ["wordId"], unique = true)]
)
data class UserWordFlagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val wordId: Long,
    val isFavorite: Boolean,
    val isDifficult: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val localDate: String,
    val reviewedCount: Int,
    val newCount: Int,
    val correctCount: Int,
    val studySeconds: Long
)

@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    val status: String, // ImportJobStatus.name()
    val totalRows: Int,
    val validRows: Int,
    val errorMessage: String?,
    val createdAt: Long
)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val queueSnapshot: String, // JSON-serialized list of SessionCardRef
    val currentIndex: Int,
    val completedCount: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val status: String // SessionStatus.name()
)
