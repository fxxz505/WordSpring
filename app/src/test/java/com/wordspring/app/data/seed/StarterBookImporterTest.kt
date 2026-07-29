package com.wordspring.app.data.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.wordspring.app.data.db.WordSpringDatabase
import com.wordspring.app.domain.model.BookSourceType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Integration tests for M08 鈥?built-in starter book import.
 *
 * Verifies:
 *  - First import creates the book with correct metadata.
 *  - Second import is idempotent (does not duplicate).
 *  - Words within the book are deduplicated by normalized spelling.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class StarterBookImporterTest {

    private lateinit var db: WordSpringDatabase
    private lateinit var importer: StarterBookImporter

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WordSpringDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // We can't easily mock assets; we'll test idempotency logic directly by inserting
        // a book with the starter name and ensuring ensureStarterBook returns without re-importing.
        importer = StarterBookImporter(
            context = ctx,
            database = db,
            wordBookDao = db.wordBookDao(),
            wordDao = db.wordDao(),
            definitionDao = db.definitionDao(),
            bookEntryDao = db.bookEntryDao(),
            reviewCardDao = db.reviewCardDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ensureStarterBook_whenBookAlreadyExists_isIdempotent() = runTest {
        // Pre-insert a book with the starter name.
        val existingId = db.wordBookDao().insert(
            com.wordspring.app.data.db.WordBookEntity(
                id = 0L,
                name = StarterBookImporter.STARTER_BOOK_NAME,
                sourceType = BookSourceType.BUILT_IN.name,
                isEnabled = true,
                dailyNewLimit = 10,
                targetEpochDay = null,
                license = "MIT",
                version = "1.0.0",
                updatedAt = 0L
            )
        )
        val result = importer.ensureStarterBook()
        // Should return the existing book id, not -1, and not create duplicates.
        assertThat(result).isEqualTo(existingId)
        val books = db.wordBookDao().getAll()
        assertThat(books).hasSize(4)
        assertThat(books.map { it.name }).contains(StarterBookImporter.STARTER_BOOK_NAME)
    }

    @Test
    fun ensureStarterBook_firstImport_createsBookAndEntries() = runTest {
        // Robolectric includes the app's merged assets, so the starter book payload
        // is available. The first import should create the book and insert entries.
        val result = importer.ensureStarterBook()
        // Should return a positive book id (not -1)
        assertThat(result).isGreaterThan(0L)
        val books = db.wordBookDao().getAll()
        assertThat(books).hasSize(4)
        assertThat(books.map { it.name }).contains(StarterBookImporter.STARTER_BOOK_NAME)
        // A second call must be idempotent 鈥?same id, no duplicate book.
        val secondResult = importer.ensureStarterBook()
        assertThat(secondResult).isEqualTo(result)
        assertThat(db.wordBookDao().getAll()).hasSize(4)
    }

    @Test
    fun ensureStarterBook_firstImport_hasMoreThanFourThousandEntriesPerBook() = runTest {
        importer.ensureStarterBook()
        db.wordBookDao().getAll().forEach { book ->
            assertThat(db.bookEntryDao().countInBook(book.id)).isAtLeast(4500)
        }
    }}


