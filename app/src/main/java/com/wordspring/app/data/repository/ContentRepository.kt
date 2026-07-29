package com.wordspring.app.data.repository

import com.wordspring.app.domain.model.BookEntry
import com.wordspring.app.domain.model.BookLearningProgress
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.Definition
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.Stage
import com.wordspring.app.domain.model.Word
import com.wordspring.app.domain.model.WordBook
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the content library (M08). ViewModels depend on this interface, not on DAOs.
 */
interface ContentRepository {
    fun observeBooks(): Flow<List<WordBook>>
    fun observeEnabledBooks(): Flow<List<WordBook>>
    fun observeBook(bookId: Long): Flow<WordBook?>
    suspend fun getBook(bookId: Long): WordBook?
    suspend fun getEnabledBooks(): List<WordBook>
    suspend fun setBookEnabled(bookId: Long, enabled: Boolean)
    suspend fun setBookDailyLimit(bookId: Long, limit: Int)
    suspend fun setBookTargetEpochDay(bookId: Long, targetEpochDay: Int?)
    suspend fun hideBook(bookId: Long)
    suspend fun deleteBook(bookId: Long): DeleteBookImpact
    suspend fun createCustomBook(name: String, dailyNewLimit: Int): Long

    fun observeBookEntries(bookId: Long): Flow<List<BookEntry>>
    suspend fun getBookEntries(bookId: Long): List<BookEntry>
    fun observeBookProgress(bookId: Long): Flow<Int>
    suspend fun getBookLearningProgress(): List<BookLearningProgress>

    suspend fun getWord(wordId: Long): Word?
    fun observeWord(wordId: Long): Flow<Word?>
    suspend fun getDefinitions(wordId: Long): List<Definition>
    fun observeDefinitions(wordId: Long): Flow<List<Definition>>

    suspend fun getOrCreateReviewCard(bookEntryId: Long, now: Long): ReviewCard
    suspend fun getReviewCard(bookEntryId: Long): ReviewCard?
    fun observeReviewCard(bookEntryId: Long): Flow<ReviewCard?>

    suspend fun searchWords(query: String, limit: Int): List<Word>
    suspend fun searchBySpellingPrefix(prefix: String, limit: Int): List<Word>
    suspend fun searchByDefinitionKeyword(keyword: String, limit: Int): List<Word>

    /**
     * Clear all user-generated data: review events, review cards, daily summaries,
     * study sessions, import jobs, user word flags, custom books (non-starter) and
     * their entries. The starter book is preserved. Per spec (M13).
     */
    suspend fun clearAllUserData()
}

data class DeleteBookImpact(
    val wordCount: Int,
    val reviewEventCount: Int
)
