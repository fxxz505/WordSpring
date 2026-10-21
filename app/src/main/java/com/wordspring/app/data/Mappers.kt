package com.wordspring.app.data

import com.wordspring.app.data.db.BookEntryEntity
import com.wordspring.app.data.db.DailySummaryEntity
import com.wordspring.app.data.db.DefinitionEntity
import com.wordspring.app.data.db.ImportJobEntity
import com.wordspring.app.data.db.ReviewCardEntity
import com.wordspring.app.data.db.ReviewEventEntity
import com.wordspring.app.data.db.StudySessionEntity
import com.wordspring.app.data.db.UserWordFlagEntity
import com.wordspring.app.data.db.WordBookEntity
import com.wordspring.app.data.db.WordEntity
import com.wordspring.app.domain.model.BookEntry
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.DailySummary
import com.wordspring.app.domain.model.Definition
import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.ImportJob
import com.wordspring.app.domain.model.ImportJobStatus
import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.ReviewEvent
import com.wordspring.app.domain.model.SessionCardRef
import com.wordspring.app.domain.model.SessionStatus
import com.wordspring.app.domain.model.Stage
import com.wordspring.app.domain.model.StudySession
import com.wordspring.app.domain.model.UserWordFlag
import com.wordspring.app.domain.model.Word
import com.wordspring.app.domain.model.WordBook
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Entity ↔ domain mappers. Keep them pure & one-direction per file so DAOs stay isolated.
 */
object Mappers {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun WordBookEntity.toDomain(): WordBook = WordBook(
        id = id,
        name = name,
        sourceType = BookSourceType.valueOf(sourceType),
        isEnabled = isEnabled,
        dailyNewLimit = dailyNewLimit,
        targetEpochDay = targetEpochDay,
        license = license,
        version = version,
        updatedAt = updatedAt
    )

    fun WordEntity.toDomain(): Word = Word(
        id = id,
        spelling = spelling,
        normalizedSpelling = normalizedSpelling,
        phonetic = phonetic,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        forms = forms
    )

    fun DefinitionEntity.toDomain(): Definition = Definition(
        id = id,
        wordId = wordId,
        partOfSpeech = partOfSpeech,
        meaningZh = meaningZh,
        example = example,
        translation = translation
    )

    fun BookEntryEntity.toDomain(): BookEntry = BookEntry(id, bookId, wordId, position)

    fun ReviewCardEntity.toDomain(): ReviewCard = ReviewCard(
        id = id,
        bookEntryId = bookEntryId,
        stage = Stage.valueOf(stage),
        dueAt = dueAt,
        intervalDays = intervalDays,
        ease = ease,
        step = step,
        updatedAt = updatedAt
    )

    fun ReviewCard.toEntity(): ReviewCardEntity = ReviewCardEntity(
        id = id,
        bookEntryId = bookEntryId,
        stage = stage.name,
        dueAt = dueAt,
        intervalDays = intervalDays,
        ease = ease,
        step = step,
        updatedAt = updatedAt
    )

    fun ReviewEventEntity.toDomain(): ReviewEvent = ReviewEvent(
        id = id,
        answerId = answerId,
        cardId = cardId,
        grade = Grade.valueOf(grade),
        questionType = QuestionType.valueOf(questionType),
        answeredAt = answeredAt,
        durationMs = durationMs
    )

    fun ReviewEvent.toEntity(): ReviewEventEntity = ReviewEventEntity(
        id = id,
        answerId = answerId,
        cardId = cardId,
        grade = grade.name,
        questionType = questionType.name,
        answeredAt = answeredAt,
        durationMs = durationMs
    )

    fun UserWordFlagEntity.toDomain(): UserWordFlag = UserWordFlag(wordId, isFavorite, isDifficult, updatedAt)

    fun DailySummaryEntity.toDomain(): DailySummary = DailySummary(
        localDate, reviewedCount, newCount, correctCount, studySeconds
    )

    fun DailySummary.toEntity(): DailySummaryEntity = DailySummaryEntity(
        localDate, reviewedCount, newCount, correctCount, studySeconds
    )

    fun ImportJobEntity.toDomain(): ImportJob = ImportJob(
        id = id,
        uri = uri,
        status = ImportJobStatus.valueOf(status),
        totalRows = totalRows,
        validRows = validRows,
        errorMessage = errorMessage,
        createdAt = createdAt
    )

    fun StudySessionEntity.toDomain(): StudySession {
        val refs: List<SessionCardRef> = if (queueSnapshot.isBlank()) emptyList()
        else json.decodeFromString(ListSerializer(SessionCardRef.serializer()), queueSnapshot)
        return StudySession(
            id = id,
            queueSnapshot = refs,
            currentIndex = currentIndex,
            completedCount = completedCount,
            startedAt = startedAt,
            endedAt = endedAt,
            status = SessionStatus.valueOf(status)
        )
    }

    fun StudySession.toEntity(): StudySessionEntity = StudySessionEntity(
        id = id,
        queueSnapshot = json.encodeToString(ListSerializer(SessionCardRef.serializer()), queueSnapshot),
        currentIndex = currentIndex,
        completedCount = completedCount,
        startedAt = startedAt,
        endedAt = endedAt,
        status = status.name
    )
}
