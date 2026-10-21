package com.wordspring.app.data.repository

import com.wordspring.app.data.Mappers.toDomain
import com.wordspring.app.data.Mappers.toEntity
import com.wordspring.app.data.NormalizerUtil
import com.wordspring.app.data.db.BookEntryDao
import com.wordspring.app.data.db.DailySummaryDao
import com.wordspring.app.data.db.DefinitionDao
import com.wordspring.app.data.db.ImportJobDao
import com.wordspring.app.data.db.ReviewCardDao
import com.wordspring.app.data.db.ReviewEventDao
import com.wordspring.app.data.db.StudySessionDao
import com.wordspring.app.data.db.UserWordFlagDao
import com.wordspring.app.data.db.WordBookDao
import com.wordspring.app.data.db.WordDao
import com.wordspring.app.data.db.WordSpringDatabase
import com.wordspring.app.domain.model.BookEntry
import com.wordspring.app.domain.model.BookLearningProgress
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.Definition
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.Stage
import com.wordspring.app.domain.model.Word
import com.wordspring.app.domain.model.WordBook
import com.wordspring.app.domain.scheduler.Scheduler
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val database: WordSpringDatabase,
    private val wordBookDao: WordBookDao,
    private val wordDao: WordDao,
    private val definitionDao: DefinitionDao,
    private val bookEntryDao: BookEntryDao,
    private val reviewCardDao: ReviewCardDao,
    private val reviewEventDao: ReviewEventDao,
    private val userWordFlagDao: UserWordFlagDao,
    private val dailySummaryDao: DailySummaryDao,
    private val importJobDao: ImportJobDao,
    private val studySessionDao: StudySessionDao
) : ContentRepository {

    override fun observeBooks(): Flow<List<WordBook>> =
        wordBookDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEnabledBooks(): Flow<List<WordBook>> =
        wordBookDao.observeEnabled().map { list -> list.map { it.toDomain() } }

    override fun observeBook(bookId: Long): Flow<WordBook?> =
        wordBookDao.observeById(bookId).map { it?.toDomain() }

    override suspend fun getBook(bookId: Long): WordBook? = wordBookDao.getById(bookId)?.toDomain()

    override suspend fun getEnabledBooks(): List<WordBook> =
        wordBookDao.getEnabled().map { it.toDomain() }

    override suspend fun setBookEnabled(bookId: Long, enabled: Boolean) {
        wordBookDao.setEnabled(bookId, enabled, System.currentTimeMillis())
    }

    override suspend fun setBookDailyLimit(bookId: Long, limit: Int) {
        wordBookDao.setDailyNewLimit(bookId, limit.coerceIn(1, 100), System.currentTimeMillis())
    }

    override suspend fun setBookTargetEpochDay(bookId: Long, targetEpochDay: Int?) {
        wordBookDao.setTargetEpochDay(bookId, targetEpochDay, System.currentTimeMillis())
    }

    override suspend fun hideBook(bookId: Long) {
        wordBookDao.hide(bookId, System.currentTimeMillis())
    }

    override suspend fun deleteBook(bookId: Long): DeleteBookImpact {
        val entries = bookEntryDao.getByBook(bookId)
        val wordCount = entries.size
        wordBookDao.delete(bookId)
        return DeleteBookImpact(wordCount = wordCount, reviewEventCount = 0)
    }

    override suspend fun createCustomBook(name: String, dailyNewLimit: Int): Long {
        val now = System.currentTimeMillis()
        val entity = com.wordspring.app.data.db.WordBookEntity(
            id = 0L,
            name = name.trim(),
            sourceType = BookSourceType.CUSTOM.name,
            isEnabled = true,
            dailyNewLimit = dailyNewLimit.coerceIn(1, 100),
            targetEpochDay = null,
            license = "用户自创",
            version = "1.0.0",
            updatedAt = now
        )
        return wordBookDao.insert(entity)
    }

    override fun observeBookEntries(bookId: Long): Flow<List<BookEntry>> =
        bookEntryDao.observeByBook(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun getBookEntries(bookId: Long): List<BookEntry> =
        bookEntryDao.getByBook(bookId).map { it.toDomain() }

    override fun observeBookProgress(bookId: Long): Flow<Int> = bookEntryDao.observeCountInBook(bookId)

    override suspend fun getBookLearningProgress(): List<BookLearningProgress> =
        reviewCardDao.getBookLearningProgress().map { row ->
            BookLearningProgress(
                bookId = row.bookId,
                totalCount = row.totalCount,
                masteredCount = row.masteredCount,
                learningCount = row.learningCount,
                newCount = row.newCount
            )
        }

    override suspend fun getWord(wordId: Long): Word? = wordDao.getById(wordId)?.toDomain()
    override fun observeWord(wordId: Long): Flow<Word?> =
        wordDao.observeById(wordId).map { it?.toDomain() }

    override suspend fun getDefinitions(wordId: Long): List<Definition> =
        definitionDao.getByWord(wordId).map { it.toDomain() }
    override suspend fun getChoiceDistractors(
        bookEntryId: Long,
        excludeWordId: Long,
        partOfSpeech: String?,
        limit: Int
    ): List<Definition> =
        definitionDao.getRandomDistractors(bookEntryId, excludeWordId, partOfSpeech, limit).map { it.toDomain() }
    override fun observeDefinitions(wordId: Long): Flow<List<Definition>> =
        definitionDao.observeByWord(wordId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreateReviewCard(bookEntryId: Long, now: Long): ReviewCard {
        reviewCardDao.getByBookEntry(bookEntryId)?.let { return it.toDomain() }
        val fresh = Scheduler.newCard(bookEntryId, now)
        val id = reviewCardDao.insert(fresh.toEntity())
        return fresh.copy(id = id)
    }

    override suspend fun getReviewCard(bookEntryId: Long): ReviewCard? =
        reviewCardDao.getByBookEntry(bookEntryId)?.toDomain()

    override fun observeReviewCard(bookEntryId: Long): Flow<ReviewCard?> =
        reviewCardDao.observeByBookEntry(bookEntryId).map { it?.toDomain() }

    override suspend fun searchWords(query: String, limit: Int): List<Word> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val bySpelling = wordDao.searchBySpellingPrefix(trimmed, limit)
        if (bySpelling.size >= limit) return bySpelling.map { it.toDomain() }
        val byDef = wordDao.searchByDefinitionKeyword(trimmed, limit - bySpelling.size)
        val seen = bySpelling.mapTo(mutableSetOf()) { it.id }
        val merged = ArrayList<Word>(bySpelling.size + byDef.size)
        merged += bySpelling.map { it.toDomain() }
        for (w in byDef) if (seen.add(w.id)) merged += w.toDomain()
        return merged
    }

    override suspend fun searchBySpellingPrefix(prefix: String, limit: Int): List<Word> =
        wordDao.searchBySpellingPrefix(prefix, limit).map { it.toDomain() }

    override suspend fun searchByDefinitionKeyword(keyword: String, limit: Int): List<Word> =
        wordDao.searchByDefinitionKeyword(keyword, limit).map { it.toDomain() }

    /**
     * Per spec M13: clear all user-generated study data while preserving the starter book
     * (WordSpring Starter 1200) and its word entries so the user can start over.
     * Runs in a single Room transaction so we never leave a half-cleared database.
     */
    override suspend fun clearAllUserData() {
        database.withTransaction {
            reviewEventDao.deleteAll()
            reviewCardDao.deleteAll()
            dailySummaryDao.deleteAll()
            studySessionDao.deleteAll()
            importJobDao.deleteAll()
            userWordFlagDao.deleteAll()
            // Remove custom / imported books and their entries; keep the starter book.
            val customBooks = wordBookDao.getAll().filter {
                it.sourceType != BookSourceType.BUILT_IN.name
            }
            for (b in customBooks) {
                bookEntryDao.deleteByBook(b.id)
                wordBookDao.delete(b.id)
            }
        }
    }
}
