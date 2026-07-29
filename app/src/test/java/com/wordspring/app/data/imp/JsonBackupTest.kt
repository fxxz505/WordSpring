package com.wordspring.app.data.imp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.wordspring.app.data.db.WordSpringDatabase
import com.wordspring.app.data.db.WordBookEntity
import com.wordspring.app.data.db.WordEntity
import com.wordspring.app.data.db.BookEntryEntity
import com.wordspring.app.data.db.DefinitionEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Integration tests for M09 — JSON backup validation and restore.
 *
 * Verifies:
 *  - validate() returns null for a well-formed backup with matching schemaVersion.
 *  - validate() returns an error message for a non-matching schemaVersion.
 *  - validate() returns an error message for invalid JSON.
 *  - restore() inserts books, words, and definitions from the backup.
 *  - restore() is additive (existing books are reused, not duplicated).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class JsonBackupTest {

    private lateinit var db: WordSpringDatabase
    private lateinit var backup: JsonBackup

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WordSpringDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = JsonBackup(
            wordBookDao = db.wordBookDao(),
            wordDao = db.wordDao(),
            definitionDao = db.definitionDao(),
            bookEntryDao = db.bookEntryDao(),
            reviewCardDao = db.reviewCardDao(),
            reviewEventDao = db.reviewEventDao(),
            userWordFlagDao = db.userWordFlagDao(),
            dailySummaryDao = db.dailySummaryDao(),
            studySessionDao = db.studySessionDao(),
            importJobDao = db.importJobDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun validate_validBackup_returnsNull() {
        val content = makeBackupJson(schemaVersion = 1)
        assertThat(backup.validate(content)).isNull()
    }

    @Test
    fun validate_wrongSchemaVersion_returnsError() {
        val content = makeBackupJson(schemaVersion = 99)
        val result = backup.validate(content)
        assertThat(result).isNotNull()
        assertThat(result).contains("不兼容")
    }

    @Test
    fun validate_invalidJson_returnsError() {
        val result = backup.validate("not valid json {{{")
        assertThat(result).isNotNull()
        assertThat(result).contains("无效")
    }

    @Test
    fun restore_validBackup_insertsEntries() = runTest {
        val content = makeBackupJson(
            schemaVersion = 1,
            entries = listOf(
                JsonBackup.EntryDto(
                    bookName = "Restored Book",
                    word = "apple",
                    phonetic = "/ˈæp.əl/",
                    partOfSpeech = "n.",
                    definition = "苹果",
                    example = "An apple a day.",
                    translation = "一天一苹果。"
                )
            )
        )
        val result = backup.restore(content)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(1)

        val book = db.wordBookDao().getByName("Restored Book")
        assertThat(book).isNotNull()
        assertThat(book!!.isEnabled).isTrue()

        val entries = db.bookEntryDao().getByBook(book.id)
        assertThat(entries).hasSize(1)
        val word = db.wordDao().getById(entries.first().wordId)
        assertThat(word!!.spelling).isEqualTo("apple")
        val defs = db.definitionDao().getByWord(word.id)
        assertThat(defs).hasSize(1)
        assertThat(defs.first().meaningZh).isEqualTo("苹果")
    }

    @Test
    fun restore_existingBook_isAdditive() = runTest {
        // Pre-create the book
        val existingId = db.wordBookDao().insert(
            WordBookEntity(
                id = 0L, name = "Existing Book", sourceType = "BUILT_IN",
                isEnabled = true, dailyNewLimit = 5, targetEpochDay = null, license = "MIT", version = "1.0", updatedAt = 0L
            )
        )
        val content = makeBackupJson(
            schemaVersion = 1,
            entries = listOf(
                JsonBackup.EntryDto(
                    bookName = "Existing Book",
                    word = "test",
                    phonetic = null,
                    partOfSpeech = null,
                    definition = "测试",
                    example = null,
                    translation = null
                )
            )
        )
        val result = backup.restore(content)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(1)

        // Should not have created a duplicate book
        val allBooks = db.wordBookDao().getAll()
        assertThat(allBooks).hasSize(1)
        assertThat(allBooks.first().id).isEqualTo(existingId)
        // Entry was added
        val entries = db.bookEntryDao().getByBook(existingId)
        assertThat(entries).hasSize(1)
    }

    @Test
    fun restore_existingWord_reusesWordId() = runTest {
        // Pre-insert a word
        val existingWordId = db.wordDao().insert(
            WordEntity(
                id = 0L, spelling = "apple", normalizedSpelling = "apple",
                phonetic = null, sourceLanguage = "en", targetLanguage = "zh-CN"
            )
        )
        // Pre-create the book and an entry
        val bookId = db.wordBookDao().insert(
            WordBookEntity(
                id = 0L, name = "Book A", sourceType = "BUILT_IN",
                isEnabled = true, dailyNewLimit = 5, targetEpochDay = null, license = "MIT", version = "1.0", updatedAt = 0L
            )
        )
        db.bookEntryDao().insert(
            BookEntryEntity(id = 0L, bookId = bookId, wordId = existingWordId, position = 0)
        )

        // Restore to a different book using the same word
        val content = makeBackupJson(
            schemaVersion = 1,
            entries = listOf(
                JsonBackup.EntryDto(
                    bookName = "Book B",
                    word = "apple",
                    phonetic = null,
                    partOfSpeech = null,
                    definition = "苹果",
                    example = null,
                    translation = null
                )
            )
        )
        backup.restore(content)

        // Word count should still be 1 (reused)
        assertThat(db.wordDao().count()).isEqualTo(1)
    }

    @Test
    fun restore_wrongSchemaVersion_returnsFailure() = runTest {
        val content = makeBackupJson(schemaVersion = 99)
        val result = backup.restore(content)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun restore_malformedJson_returnsFailure() = runTest {
        val result = backup.restore("not json")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun export_thenValidate_roundTrips() = runTest {
        // Seed data
        val bookId = db.wordBookDao().insert(
            WordBookEntity(
                id = 0L, name = "Export Book", sourceType = "BUILT_IN",
                isEnabled = true, dailyNewLimit = 5, targetEpochDay = null, license = "MIT", version = "1.0", updatedAt = 0L
            )
        )
        val wordId = db.wordDao().insert(
            WordEntity(
                id = 0L, spelling = "test", normalizedSpelling = "test",
                phonetic = null, sourceLanguage = "en", targetLanguage = "zh-CN"
            )
        )
        db.bookEntryDao().insert(BookEntryEntity(id = 0L, bookId = bookId, wordId = wordId, position = 0))
        db.definitionDao().insert(
            DefinitionEntity(
                id = 0L, wordId = wordId, partOfSpeech = "n.",
                meaningZh = "测试", example = null, translation = null
            )
        )

        val exported = backup.export("1.0.0")
        // The exported string should validate cleanly.
        assertThat(backup.validate(exported)).isNull()
    }

    private fun makeBackupJson(
        schemaVersion: Int = 1,
        entries: List<JsonBackup.EntryDto> = emptyList()
    ): String {
        val backup = JsonBackup.Backup(
            schemaVersion = schemaVersion,
            appVersion = "1.0.0",
            exportedAt = 0L,
            books = emptyList(),
            entries = entries
        )
        return json.encodeToString(JsonBackup.Backup.serializer(), backup)
    }
}
