package com.wordspring.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.wordspring.app.data.db.BookEntryEntity
import com.wordspring.app.data.db.WordBookEntity
import com.wordspring.app.data.db.WordEntity
import com.wordspring.app.data.db.WordSpringDatabase
import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.domain.model.Stage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Integration tests for M07 — atomic, idempotent answer submission.
 *
 * Uses Robolectric + in-memory Room. Verifies:
 *  - Single submit commits event + card + daily summary + session cursor in one transaction.
 *  - Same answerId twice → AlreadyCommitted (idempotent).
 *  - Card stage transitions are persisted.
 *  - Daily summary counters are incremented correctly.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ReviewRepositoryImplTest {

    private lateinit var db: WordSpringDatabase
    private lateinit var repo: ReviewRepositoryImpl

    private var bookId: Long = 0L
    private var entryId: Long = 0L
    private var cardId: Long = 0L

    @Before
    fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WordSpringDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ReviewRepositoryImpl(
            db = db,
            reviewCardDao = db.reviewCardDao(),
            reviewEventDao = db.reviewEventDao(),
            dailySummaryDao = db.dailySummaryDao(),
            studySessionDao = db.studySessionDao(),
            bookEntryDao = db.bookEntryDao(),
            userWordFlagDao = db.userWordFlagDao(),
            wordDao = db.wordDao(),
            definitionDao = db.definitionDao()
        )
        // Seed a book + word + entry + review card.
        bookId = db.wordBookDao().insert(
            WordBookEntity(
                id = 0L, name = "Test Book", sourceType = "BUILT_IN",
                isEnabled = true, dailyNewLimit = 10, targetEpochDay = null, license = "MIT", version = "1.0", updatedAt = 0L
            )
        )
        val wordId = db.wordDao().insert(
            WordEntity(
                id = 0L, spelling = "apple", normalizedSpelling = "apple",
                phonetic = null, sourceLanguage = "en", targetLanguage = "zh-CN"
            )
        )
        entryId = db.bookEntryDao().insert(
            BookEntryEntity(id = 0L, bookId = bookId, wordId = wordId, position = 0)
        )
        // Initial NEW review card
        cardId = db.reviewCardDao().insert(
            com.wordspring.app.data.db.ReviewCardEntity(
                id = 0L, bookEntryId = entryId, stage = Stage.NEW.name,
                dueAt = 0L, intervalDays = 0.0, ease = 2.5, step = 0, updatedAt = 0L
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun submitAnswer_newCardGood_commitsEventAndAdvancesCard() = runTest {
        val cmd = AnswerCommand(
            answerId = "ans-1",
            cardId = cardId,
            bookEntryId = entryId,
            grade = Grade.GOOD,
            questionType = QuestionType.RECALL,
            now = 1_000L,
            durationMs = 5_000L,
            sessionId = ""
        )
        val result = repo.submitAnswer(cmd)
        assertThat(result).isInstanceOf(AnswerResult.Committed::class.java)

        val card = db.reviewCardDao().getById(cardId)!!
        assertThat(card.stage).isEqualTo(Stage.LEARNING.name)
        assertThat(card.step).isEqualTo(1)

        val events = db.reviewEventDao().getForCard(cardId, 10)
        assertThat(events).hasSize(1)
        assertThat(events.first().answerId).isEqualTo("ans-1")
        assertThat(events.first().grade).isEqualTo(Grade.GOOD.name)

        val today = com.wordspring.app.data.TimeUtil.localDateString(1_000L)
        val summary = db.dailySummaryDao().getByDate(today)
        assertThat(summary).isNotNull()
        assertThat(summary!!.reviewedCount).isEqualTo(1)
        assertThat(summary.newCount).isEqualTo(1) // was NEW and grade != AGAIN
        assertThat(summary.correctCount).isEqualTo(1) // GOOD is correct
        assertThat(summary.studySeconds).isEqualTo(5L)
    }

    @Test
    fun submitAnswer_sameAnswerIdTwice_isIdempotent() = runTest {
        val cmd = AnswerCommand(
            answerId = "ans-dup",
            cardId = cardId,
            bookEntryId = entryId,
            grade = Grade.GOOD,
            questionType = QuestionType.RECALL,
            now = 1_000L,
            durationMs = 5_000L,
            sessionId = ""
        )
        val first = repo.submitAnswer(cmd)
        val second = repo.submitAnswer(cmd)

        assertThat(first).isInstanceOf(AnswerResult.Committed::class.java)
        assertThat(second).isInstanceOf(AnswerResult.AlreadyCommitted::class.java)

        // Only one event recorded
        val events = db.reviewEventDao().getForCard(cardId, 10)
        assertThat(events).hasSize(1)
        // Summary not double-counted
        val today = com.wordspring.app.data.TimeUtil.localDateString(1_000L)
        val summary = db.dailySummaryDao().getByDate(today)!!
        assertThat(summary.reviewedCount).isEqualTo(1)
    }

    @Test
    fun submitAnswer_again_doesNotCountAsNewOrCorrect() = runTest {
        val cmd = AnswerCommand(
            answerId = "ans-again",
            cardId = cardId,
            bookEntryId = entryId,
            grade = Grade.AGAIN,
            questionType = QuestionType.SPELLING,
            now = 2_000L,
            durationMs = 3_000L,
            sessionId = ""
        )
        val result = repo.submitAnswer(cmd)
        assertThat(result).isInstanceOf(AnswerResult.Committed::class.java)

        val today = com.wordspring.app.data.TimeUtil.localDateString(2_000L)
        val summary = db.dailySummaryDao().getByDate(today)!!
        assertThat(summary.reviewedCount).isEqualTo(1)
        assertThat(summary.newCount).isEqualTo(0) // AGAIN doesn't count as new learned
        assertThat(summary.correctCount).isEqualTo(0) // AGAIN is not correct
    }

    @Test
    fun submitAnswer_multipleAnswers_accumulateInSummary() = runTest {
        // First answer: GOOD on NEW card
        repo.submitAnswer(
            AnswerCommand(
                answerId = "a1", cardId = cardId, bookEntryId = entryId,
                grade = Grade.GOOD, questionType = QuestionType.RECALL,
                now = 1_000L, durationMs = 5_000L, sessionId = ""
            )
        )
        // Add a second card to test accumulation
        val word2 = db.wordDao().insert(
            WordEntity(
                id = 0L, spelling = "banana", normalizedSpelling = "banana",
                phonetic = null, sourceLanguage = "en", targetLanguage = "zh-CN"
            )
        )
        val entry2 = db.bookEntryDao().insert(
            BookEntryEntity(id = 0L, bookId = bookId, wordId = word2, position = 1)
        )
        val card2 = db.reviewCardDao().insert(
            com.wordspring.app.data.db.ReviewCardEntity(
                id = 0L, bookEntryId = entry2, stage = Stage.NEW.name,
                dueAt = 0L, intervalDays = 0.0, ease = 2.5, step = 0, updatedAt = 0L
            )
        )
        // Second answer: AGAIN on NEW card (same day)
        repo.submitAnswer(
            AnswerCommand(
                answerId = "a2", cardId = card2, bookEntryId = entry2,
                grade = Grade.AGAIN, questionType = QuestionType.RECALL,
                now = 2_000L, durationMs = 4_000L, sessionId = ""
            )
        )

        val today = com.wordspring.app.data.TimeUtil.localDateString(1_000L)
        val summary = db.dailySummaryDao().getByDate(today)!!
        assertThat(summary.reviewedCount).isEqualTo(2)
        assertThat(summary.newCount).isEqualTo(1) // only the GOOD one
        assertThat(summary.correctCount).isEqualTo(1) // only the GOOD one
        assertThat(summary.studySeconds).isEqualTo(9L) // 5 + 4
    }

    @Test
    fun todayReviewMetrics_excludeCardsFirstLearnedToday_andDeduplicateRetries() = runTest {
        val dayStart = com.wordspring.app.data.TimeUtil.startOfTodayMillis()
        val now = dayStart + 2_000L

        // This card existed before today, so answering it today is a scheduled review.
        db.reviewEventDao().insert(
            com.wordspring.app.data.db.ReviewEventEntity(
                answerId = "yesterday", cardId = cardId, grade = Grade.GOOD.name,
                questionType = QuestionType.RECALL.name, answeredAt = dayStart - 1L, durationMs = 0L
            )
        )
        db.reviewCardDao().update(
            com.wordspring.app.data.db.ReviewCardEntity(
                id = cardId, bookEntryId = entryId, stage = Stage.LEARNING.name,
                dueAt = dayStart, intervalDays = 1.0, ease = 2.5, step = 1, updatedAt = dayStart
            )
        )
        db.reviewEventDao().insert(
            com.wordspring.app.data.db.ReviewEventEntity(
                answerId = "today-old-1", cardId = cardId, grade = Grade.GOOD.name,
                questionType = QuestionType.RECALL.name, answeredAt = now, durationMs = 0L
            )
        )
        db.reviewEventDao().insert(
            com.wordspring.app.data.db.ReviewEventEntity(
                answerId = "today-old-2", cardId = cardId, grade = Grade.GOOD.name,
                questionType = QuestionType.RECALL.name, answeredAt = now + 1L, durationMs = 0L
            )
        )

        // A card introduced today may be due again, but it is not today's planned review.
        val word2 = db.wordDao().insert(WordEntity(0L, "banana", "banana", null, "en", "zh-CN"))
        val entry2 = db.bookEntryDao().insert(BookEntryEntity(0L, bookId, word2, 1))
        val todayNewCard = db.reviewCardDao().insert(
            com.wordspring.app.data.db.ReviewCardEntity(0L, entry2, Stage.LEARNING.name, dayStart, 1.0, 2.5, 1, dayStart)
        )
        db.reviewEventDao().insert(
            com.wordspring.app.data.db.ReviewEventEntity(
                answerId = "today-new", cardId = todayNewCard, grade = Grade.GOOD.name,
                questionType = QuestionType.RECALL.name, answeredAt = now, durationMs = 0L
            )
        )

        assertThat(repo.getScheduledReviewCardCount(listOf(bookId), now, dayStart)).isEqualTo(1)
        assertThat(repo.getCompletedScheduledReviewCount(listOf(bookId), dayStart)).isEqualTo(1)
    }

    @Test
    fun submitAnswer_withSession_advancesCursor() = runTest {
        // Create an active session
        val sessionId = "session-1"
        db.studySessionDao().upsert(
            com.wordspring.app.data.db.StudySessionEntity(
                id = sessionId,
                queueSnapshot = "[]",
                currentIndex = 0,
                completedCount = 0,
                startedAt = 0L,
                endedAt = null,
                status = "ACTIVE"
            )
        )
        repo.submitAnswer(
            AnswerCommand(
                answerId = "ans-sess",
                cardId = cardId,
                bookEntryId = entryId,
                grade = Grade.GOOD,
                questionType = QuestionType.RECALL,
                now = 1_000L,
                durationMs = 5_000L,
                sessionId = sessionId
            )
        )
        val session = db.studySessionDao().getById(sessionId)!!
        assertThat(session.currentIndex).isEqualTo(1)
        assertThat(session.completedCount).isEqualTo(1)
        assertThat(session.status).isEqualTo("ACTIVE")
    }

    @Test
    fun submitAgainOnReviewCard_dropsToLearningStep0() = runTest {
        // Move card to REVIEW stage first
        db.reviewCardDao().update(
            com.wordspring.app.data.db.ReviewCardEntity(
                id = cardId, bookEntryId = entryId, stage = Stage.REVIEW.name,
                dueAt = 0L, intervalDays = 10.0, ease = 2.5, step = 0, updatedAt = 0L
            )
        )
        repo.submitAnswer(
            AnswerCommand(
                answerId = "ans-review-again",
                cardId = cardId,
                bookEntryId = entryId,
                grade = Grade.AGAIN,
                questionType = QuestionType.RECALL,
                now = 5_000L,
                durationMs = 1_000L,
                sessionId = ""
            )
        )
        val card = db.reviewCardDao().getById(cardId)!!
        assertThat(card.stage).isEqualTo(Stage.LEARNING.name)
        assertThat(card.step).isEqualTo(0)
        // dueAt should be now + 10 min
        assertThat(card.dueAt - 5_000L).isEqualTo(10 * 60_000L)
    }

    @Test
    fun relearnCard_resetsToLearningStep0() = runTest {
        // Move card to REVIEW with a long interval
        db.reviewCardDao().update(
            com.wordspring.app.data.db.ReviewCardEntity(
                id = cardId, bookEntryId = entryId, stage = Stage.REVIEW.name,
                dueAt = 0L, intervalDays = 30.0, ease = 2.5, step = 0, updatedAt = 0L
            )
        )
        repo.relearnCard(cardId, now = 9_000L)
        val card = db.reviewCardDao().getById(cardId)!!
        assertThat(card.stage).isEqualTo(Stage.LEARNING.name)
        assertThat(card.step).isEqualTo(0)
    }

    @Test
    fun submitAnswer_nonExistentCard_returnsFailed() = runTest {
        val result = repo.submitAnswer(
            AnswerCommand(
                answerId = "ans-missing",
                cardId = 999_999L,
                bookEntryId = 999_999L,
                grade = Grade.GOOD,
                questionType = QuestionType.RECALL,
                now = 1_000L,
                durationMs = 1_000L,
                sessionId = ""
            )
        )
        assertThat(result).isInstanceOf(AnswerResult.Failed::class.java)
    }
}
