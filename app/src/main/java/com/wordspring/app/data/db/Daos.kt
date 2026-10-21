package com.wordspring.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordBookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(book: WordBookEntity): Long
    @Update suspend fun update(book: WordBookEntity)
    @Query("SELECT * FROM word_books ORDER BY id ASC") fun observeAll(): Flow<List<WordBookEntity>>
    @Query("SELECT * FROM word_books WHERE isEnabled = 1 ORDER BY id ASC") fun observeEnabled(): Flow<List<WordBookEntity>>
    @Query("SELECT * FROM word_books WHERE isEnabled = 1 ORDER BY id ASC") suspend fun getEnabled(): List<WordBookEntity>
    @Query("SELECT * FROM word_books ORDER BY id ASC") suspend fun getAll(): List<WordBookEntity>
    @Query("SELECT * FROM word_books WHERE id = :id") suspend fun getById(id: Long): WordBookEntity?
    @Query("SELECT * FROM word_books WHERE id = :id") fun observeById(id: Long): Flow<WordBookEntity?>
    @Query("SELECT * FROM word_books WHERE name = :name LIMIT 1") suspend fun getByName(name: String): WordBookEntity?
    @Query("UPDATE word_books SET isEnabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)
    @Query("UPDATE word_books SET dailyNewLimit = :limit, updatedAt = :now WHERE id = :id")
    suspend fun setDailyNewLimit(id: Long, limit: Int, now: Long)
    @Query("UPDATE word_books SET targetEpochDay = :targetEpochDay, updatedAt = :now WHERE id = :id")
    suspend fun setTargetEpochDay(id: Long, targetEpochDay: Int?, now: Long)
    @Query("UPDATE word_books SET isEnabled = 0, updatedAt = :now WHERE id = :id")
    suspend fun hide(id: Long, now: Long)
    @Query("DELETE FROM word_books WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM word_books WHERE sourceType != :starterType") suspend fun deleteNonStarter(starterType: String)
    @Query("DELETE FROM word_books") suspend fun deleteAll()
}

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(word: WordEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(words: List<WordEntity>): List<Long>
    @Query("SELECT * FROM words WHERE id = :id") suspend fun getById(id: Long): WordEntity?
    @Query("SELECT * FROM words WHERE id = :id") fun observeById(id: Long): Flow<WordEntity?>
    @Query("SELECT * FROM words WHERE normalizedSpelling = :normalized LIMIT 1")
    suspend fun getByNormalized(normalized: String): WordEntity?
    @Query("SELECT * FROM words WHERE normalizedSpelling IN (:normalizedSpellings)")
    suspend fun getByNormalizedSpellings(normalizedSpellings: List<String>): List<WordEntity>
    @Query("SELECT * FROM words WHERE spelling LIKE :prefix || '%' COLLATE NOCASE ORDER BY spelling LIMIT :limit")
    suspend fun searchBySpellingPrefix(prefix: String, limit: Int): List<WordEntity>
    @Query("""
        SELECT w.* FROM words w
        INNER JOIN definitions d ON d.wordId = w.id
        WHERE d.meaningZh LIKE :keyword || '%' COLLATE NOCASE
        ORDER BY w.spelling
        LIMIT :limit
    """)
    suspend fun searchByDefinitionKeyword(keyword: String, limit: Int): List<WordEntity>
    @Query("SELECT COUNT(*) FROM words") suspend fun count(): Int
    @Query("UPDATE words SET phonetic = :phonetic, forms = :forms WHERE id = :id")
    suspend fun updatePhoneticAndForms(id: Long, phonetic: String?, forms: String?)
}

@Dao
interface DefinitionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(definition: DefinitionEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(defs: List<DefinitionEntity>): List<Long>
    @Query("SELECT * FROM definitions WHERE wordId = :wordId ORDER BY id ASC")
    suspend fun getByWord(wordId: Long): List<DefinitionEntity>
    @Query("SELECT * FROM definitions WHERE wordId = :wordId ORDER BY id ASC")
    fun observeByWord(wordId: Long): Flow<List<DefinitionEntity>>
    @Query("SELECT * FROM definitions WHERE wordId IN (:wordIds) ORDER BY id ASC")
    suspend fun getByWordIds(wordIds: List<Long>): List<DefinitionEntity>
    @Query("DELETE FROM definitions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
    @Query("SELECT COUNT(*) FROM definitions WHERE wordId = :wordId AND meaningZh = :meaningZh")
    suspend fun countSameMeaning(wordId: Long, meaningZh: String): Int

    /**
     * Random meanings of *other* words from the same book, used as multiple-choice distractors.
     * Pass [partOfSpeech] to prefer distractors of the same part of speech (harder, fairer options).
     */
    @Query("""
        SELECT d.* FROM definitions d
        INNER JOIN book_entries be ON be.wordId = d.wordId
        WHERE be.bookId = (SELECT bookId FROM book_entries WHERE id = :bookEntryId)
          AND d.wordId != :excludeWordId
          AND (:partOfSpeech IS NULL OR d.partOfSpeech = :partOfSpeech)
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomDistractors(
        bookEntryId: Long,
        excludeWordId: Long,
        partOfSpeech: String?,
        limit: Int
    ): List<DefinitionEntity>
}

@Dao
interface BookEntryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: BookEntryEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(entries: List<BookEntryEntity>): List<Long>
    @Query("SELECT * FROM book_entries WHERE bookId = :bookId ORDER BY position ASC")
    fun observeByBook(bookId: Long): Flow<List<BookEntryEntity>>
    @Query("SELECT * FROM book_entries WHERE bookId = :bookId ORDER BY position ASC")
    suspend fun getByBook(bookId: Long): List<BookEntryEntity>
    @Query("SELECT * FROM book_entries WHERE id = :id") suspend fun getById(id: Long): BookEntryEntity?
    @Query("SELECT COUNT(*) FROM book_entries WHERE bookId = :bookId") suspend fun countInBook(bookId: Long): Int
    @Query("SELECT COUNT(*) FROM book_entries WHERE bookId = :bookId") fun observeCountInBook(bookId: Long): Flow<Int>
    @Query("DELETE FROM book_entries WHERE bookId = :bookId") suspend fun deleteByBook(bookId: Long)
    @Query("DELETE FROM book_entries WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)
    @Query("UPDATE book_entries SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
    @Query("DELETE FROM book_entries") suspend fun deleteAll()
}

@Dao
interface ReviewCardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(card: ReviewCardEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(cards: List<ReviewCardEntity>): List<Long>
    @Update suspend fun update(card: ReviewCardEntity)
    @Query("SELECT * FROM review_cards WHERE id = :id") suspend fun getById(id: Long): ReviewCardEntity?
    @Query("SELECT * FROM review_cards WHERE bookEntryId = :bookEntryId LIMIT 1")
    suspend fun getByBookEntry(bookEntryId: Long): ReviewCardEntity?
    @Query("SELECT * FROM review_cards WHERE bookEntryId = :bookEntryId LIMIT 1")
    fun observeByBookEntry(bookEntryId: Long): Flow<ReviewCardEntity?>

    /**
     * Due cards across the given book ids: stage in (LEARNING, REVIEW) and dueAt <= now.
     * NEW is excluded here; the planner pulls NEW separately.
     */
    @Query("""
        SELECT rc.* FROM review_cards rc
        INNER JOIN book_entries be ON be.id = rc.bookEntryId
        WHERE be.bookId IN (:bookIds)
          AND rc.stage IN ('LEARNING','REVIEW')
          AND rc.dueAt <= :now
        ORDER BY rc.dueAt ASC
        LIMIT :limit
    """)
    suspend fun getDueCards(bookIds: List<Long>, now: Long, limit: Int): List<ReviewCardEntity>

    @Query("""
        SELECT rc.* FROM review_cards rc
        INNER JOIN book_entries be ON be.id = rc.bookEntryId
        WHERE be.bookId IN (:bookIds) AND rc.stage = 'NEW'
        ORDER BY be.position ASC
    """)
    suspend fun getNewCards(bookIds: List<Long>): List<ReviewCardEntity>

    @Query("""
        SELECT COUNT(*) FROM review_cards rc
        INNER JOIN book_entries be ON be.id = rc.bookEntryId
        WHERE be.bookId IN (:bookIds)
          AND rc.stage IN ('LEARNING','REVIEW')
          AND rc.dueAt <= :now
    """)
    suspend fun countDue(bookIds: List<Long>, now: Long): Int

    /** Cards due today that already existed in the learning system before today. */
    @Query("""
        SELECT COUNT(DISTINCT rc.id) FROM review_cards rc
        INNER JOIN book_entries be ON be.id = rc.bookEntryId
        WHERE be.bookId IN (:bookIds)
          AND rc.stage IN ('LEARNING','REVIEW')
          AND rc.dueAt <= :now
          AND EXISTS (
              SELECT 1 FROM review_events prior
              WHERE prior.cardId = rc.id AND prior.answeredAt < :dayStart
          )
    """)
    suspend fun countScheduledReviewDue(bookIds: List<Long>, now: Long, dayStart: Long): Int

    @Query("""
        SELECT COUNT(*) FROM review_cards rc
        INNER JOIN book_entries be ON be.id = rc.bookEntryId
        WHERE be.bookId IN (:bookIds) AND rc.stage = 'NEW'
    """)
    suspend fun countNew(bookIds: List<Long>): Int

    @Query("""
        SELECT COUNT(*) FROM review_cards rc
        INNER JOIN book_entries be ON be.id = rc.bookEntryId
        WHERE be.bookId = :bookId AND rc.stage IN ('LEARNING','REVIEW')
    """)
    suspend fun countStudiedInBook(bookId: Long): Int

    @Query("""
        SELECT be.bookId AS bookId,
               COUNT(be.id) AS totalCount,
               SUM(CASE WHEN rc.stage = 'REVIEW' THEN 1 ELSE 0 END) AS masteredCount,
               SUM(CASE WHEN rc.stage = 'LEARNING' THEN 1 ELSE 0 END) AS learningCount,
               SUM(CASE WHEN rc.stage = 'NEW' THEN 1 ELSE 0 END) AS newCount
        FROM book_entries be
        LEFT JOIN review_cards rc ON rc.bookEntryId = be.id
        GROUP BY be.bookId
    """)
    suspend fun getBookLearningProgress(): List<BookLearningProgressRow>

    @Query("UPDATE review_cards SET stage = 'SUSPENDED', updatedAt = :now WHERE id = :cardId")
    suspend fun suspend(cardId: Long, now: Long)
    @Query("DELETE FROM review_cards") suspend fun deleteAll()
    @Query("DELETE FROM review_cards WHERE bookEntryId IN (SELECT id FROM book_entries WHERE bookId = :bookId)")
    suspend fun deleteByBook(bookId: Long)
    @Query("DELETE FROM review_cards WHERE bookEntryId IN (:bookEntryIds)")
    suspend fun deleteByBookEntryIds(bookEntryIds: List<Long>)
    @Query("""
        SELECT rc.* FROM review_cards rc
        INNER JOIN book_entries be ON be.id = rc.bookEntryId
        WHERE be.bookId = :bookId
    """)
    suspend fun getByBook(bookId: Long): List<ReviewCardEntity>
}

data class BookLearningProgressRow(
    val bookId: Long,
    val totalCount: Int,
    val masteredCount: Int,
    val learningCount: Int,
    val newCount: Int
)

@Dao
interface ReviewEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(event: ReviewEventEntity): Long
    @Query("SELECT COUNT(*) FROM review_events WHERE answerId = :answerId")
    suspend fun countByAnswerId(answerId: String): Int
    @Query("SELECT * FROM review_events WHERE cardId = :cardId ORDER BY answeredAt DESC LIMIT :limit")
    suspend fun getForCard(cardId: Long, limit: Int): List<ReviewEventEntity>
    @Query("SELECT * FROM review_events WHERE answeredAt >= :since ORDER BY answeredAt DESC")
    suspend fun getSince(since: Long): List<ReviewEventEntity>
    @Query("""
        SELECT cardId FROM review_events
        WHERE grade = 'AGAIN' AND answeredAt >= :since
        GROUP BY cardId
    """)
    suspend fun getMistakeCardIds(since: Long): List<Long>
    @Query("""
        SELECT cardId FROM review_events
        WHERE grade = 'AGAIN' AND answeredAt >= :since
        GROUP BY cardId
    """)
    fun observeMistakeCardIds(since: Long): Flow<List<Long>>
    @Query("SELECT COUNT(*) FROM review_events") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM review_events WHERE answeredAt >= :since")
    suspend fun countSince(since: Long): Int

    /** Completed today for cards first seen before today; retries count once. */
    @Query("""
        SELECT COUNT(DISTINCT today.cardId)
        FROM review_events today
        INNER JOIN review_cards card ON card.id = today.cardId
        INNER JOIN book_entries entry ON entry.id = card.bookEntryId
        WHERE entry.bookId IN (:bookIds)
          AND today.answeredAt >= :dayStart
          AND EXISTS (
              SELECT 1 FROM review_events prior
              WHERE prior.cardId = today.cardId AND prior.answeredAt < :dayStart
          )
          AND NOT EXISTS (
              SELECT 1 FROM review_events sameDayPrior
              WHERE sameDayPrior.cardId = today.cardId
                AND sameDayPrior.answeredAt >= :dayStart
                AND sameDayPrior.answeredAt < today.answeredAt
          )
    """)
    suspend fun countCompletedScheduledReviews(bookIds: List<Long>, dayStart: Long): Int
    @Query("""
        SELECT COUNT(DISTINCT event.cardId)
        FROM review_events event
        INNER JOIN review_cards card ON card.id = event.cardId
        INNER JOIN book_entries entry ON entry.id = card.bookEntryId
        WHERE entry.bookId = :bookId
          AND event.answeredAt >= :since
          AND event.grade != 'AGAIN'
          AND NOT EXISTS (
              SELECT 1 FROM review_events earlier
              WHERE earlier.cardId = event.cardId
                AND earlier.answeredAt < event.answeredAt
          )
    """)
    suspend fun countFirstSuccessfulAnswersForBookSince(bookId: Long, since: Long): Int
    @Query("SELECT COUNT(*) FROM review_events WHERE answeredAt >= :since AND grade != 'AGAIN'")
    suspend fun countCorrectSince(since: Long): Int
    @Query("SELECT * FROM review_events ORDER BY answeredAt DESC")
    suspend fun getAll(): List<ReviewEventEntity>
    @Query("UPDATE review_events SET cardId = :newCardId WHERE cardId = :oldCardId")
    suspend fun reassignCard(oldCardId: Long, newCardId: Long)
    @Query("DELETE FROM review_events") suspend fun deleteAll()
}

@Dao
interface UserWordFlagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flag: UserWordFlagEntity)
    @Query("SELECT * FROM user_word_flags WHERE wordId = :wordId LIMIT 1")
    suspend fun getByWord(wordId: Long): UserWordFlagEntity?
    @Query("SELECT * FROM user_word_flags WHERE wordId = :wordId LIMIT 1")
    fun observeByWord(wordId: Long): Flow<UserWordFlagEntity?>
    @Query("SELECT * FROM user_word_flags WHERE isFavorite = 1")
    fun observeFavorites(): Flow<List<UserWordFlagEntity>>
    @Query("UPDATE user_word_flags SET isDifficult = :isDifficult, updatedAt = :now WHERE wordId = :wordId")
    suspend fun setDifficult(wordId: Long, isDifficult: Boolean, now: Long)
    @Query("DELETE FROM user_word_flags") suspend fun deleteAll()
}

@Dao
interface DailySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)
    @Query("SELECT * FROM daily_summaries WHERE localDate = :date LIMIT 1")
    suspend fun getByDate(date: String): DailySummaryEntity?
    @Query("SELECT * FROM daily_summaries WHERE localDate BETWEEN :start AND :end ORDER BY localDate ASC")
    suspend fun getBetween(start: String, end: String): List<DailySummaryEntity>
    @Query("SELECT * FROM daily_summaries WHERE localDate BETWEEN :start AND :end ORDER BY localDate ASC")
    fun observeBetween(start: String, end: String): Flow<List<DailySummaryEntity>>
    @Query("SELECT * FROM daily_summaries ORDER BY localDate DESC LIMIT 1")
    suspend fun getLatest(): DailySummaryEntity?
    @Query("SELECT COUNT(*) FROM daily_summaries WHERE reviewedCount > 0")
    suspend fun countActiveDays(): Int
    @Query("DELETE FROM daily_summaries") suspend fun deleteAll()
}

@Dao
interface ImportJobDao {
    @Insert suspend fun insert(job: ImportJobEntity): Long
    @Update suspend fun update(job: ImportJobEntity)
    @Query("SELECT * FROM import_jobs WHERE id = :id") suspend fun getById(id: Long): ImportJobEntity?
    @Query("SELECT * FROM import_jobs ORDER BY createdAt DESC") fun observeAll(): Flow<List<ImportJobEntity>>
    @Query("DELETE FROM import_jobs WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM import_jobs") suspend fun deleteAll()
}

@Dao
interface StudySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(session: StudySessionEntity)
    @Query("SELECT * FROM study_sessions WHERE id = :id") suspend fun getById(id: String): StudySessionEntity?
    @Query("SELECT * FROM study_sessions WHERE id = :id") fun observeById(id: String): Flow<StudySessionEntity?>
    @Query("UPDATE study_sessions SET currentIndex = :index, completedCount = :completed, status = :status, endedAt = :endedAt WHERE id = :id")
    suspend fun updateProgress(id: String, index: Int, completed: Int, status: String, endedAt: Long?)
    @Query("DELETE FROM study_sessions WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM study_sessions") suspend fun deleteAll()
    @Query("SELECT * FROM study_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): StudySessionEntity?
}
