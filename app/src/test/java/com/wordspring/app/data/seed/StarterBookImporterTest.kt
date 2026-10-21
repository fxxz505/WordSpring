package com.wordspring.app.data.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.wordspring.app.data.db.BookEntryEntity
import com.wordspring.app.data.db.DefinitionSource
import com.wordspring.app.data.db.ReviewCardEntity
import com.wordspring.app.data.db.ReviewEventEntity
import com.wordspring.app.data.db.WordBookEntity
import com.wordspring.app.data.db.WordEntity
import com.wordspring.app.data.db.WordSpringDatabase
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.Stage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Integration tests for the built-in book import. Robolectric merges the app's assets,
 * so the real generated books are used.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class StarterBookImporterTest {

    private lateinit var ctx: Context
    private lateinit var db: WordSpringDatabase
    private lateinit var importer: StarterBookImporter

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, WordSpringDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = newImporter()
    }

    private fun newImporter() = StarterBookImporter(
        context = ctx,
        database = db,
        wordBookDao = db.wordBookDao(),
        wordDao = db.wordDao(),
        definitionDao = db.definitionDao(),
        bookEntryDao = db.bookEntryDao(),
        reviewCardDao = db.reviewCardDao(),
        reviewEventDao = db.reviewEventDao()
    )

    @After
    fun tearDown() {
        db.close()
    }

    private fun metadata(folder: String): StarterBookMetadata {
        val text = ctx.assets.open("books/$folder/metadata.json").bufferedReader().use { it.readText() }
        return Json { ignoreUnknownKeys = true }.decodeFromString(StarterBookMetadata.serializer(), text)
    }

    @Test
    fun firstImport_createsEveryCatalogBook_withOnlyDefaultEnabled() = runTest {
        val result = importer.ensureStarterBook()

        assertThat(result).isGreaterThan(0L)
        val books = db.wordBookDao().getAll()
        assertThat(books.map { it.name })
            .containsExactlyElementsIn(BuiltInBookCatalog.books.map { it.name })
        assertThat(books.filter { it.isEnabled }.map { it.name })
            .containsExactly(StarterBookImporter.STARTER_BOOK_NAME)
        // Second call is idempotent.
        assertThat(newImporter().ensureStarterBook()).isEqualTo(result)
        assertThat(db.wordBookDao().getAll()).hasSize(BuiltInBookCatalog.books.size)
    }

    @Test
    fun firstImport_eachBookHasExactlyItsListedWords() = runTest {
        importer.ensureStarterBook()
        val books = db.wordBookDao().getAll().associateBy { it.name }
        for (spec in BuiltInBookCatalog.books) {
            val meta = metadata(spec.assetFolder)
            assertThat(meta.name).isEqualTo(spec.name)
            val book = books.getValue(spec.name)
            assertThat(db.bookEntryDao().countInBook(book.id)).isEqualTo(meta.wordCount)
            assertThat(meta.wordCount).isAtLeast(1000)
        }
    }

    @Test
    fun sharedWord_hasNoDuplicateMeanings_andCleanPhonetic() = runTest {
        importer.ensureStarterBook()
        // "abandon" appears in CET-4, CET-6, 考研, IELTS, TOEFL, ...
        val word = db.wordDao().getByNormalized("abandon")!!
        assertThat(word.phonetic).startsWith("/")
        assertThat(word.phonetic).doesNotContain("ә")
        val defs = db.definitionDao().getByWord(word.id)
        assertThat(defs).isNotEmpty()
        assertThat(defs.map { it.partOfSpeech to it.meaningZh }.toSet()).hasSize(defs.size)
        assertThat(defs.all { it.source == DefinitionSource.BUILT_IN }).isTrue()
        assertThat(defs.none { it.meaningZh.contains("\\n") }).isTrue()
    }

    @Test
    fun outdatedBook_isUpgradedInPlace_keepingIdAndSettings() = runTest {
        val existingId = db.wordBookDao().insert(
            WordBookEntity(
                name = StarterBookImporter.STARTER_BOOK_NAME,
                sourceType = BookSourceType.BUILT_IN.name,
                isEnabled = true,
                dailyNewLimit = 42,
                targetEpochDay = null,
                license = "MIT",
                version = "1.0.0",
                updatedAt = 0L
            )
        )

        val result = importer.ensureStarterBook()

        assertThat(result).isEqualTo(existingId)
        val book = db.wordBookDao().getById(existingId)!!
        assertThat(book.dailyNewLimit).isEqualTo(42)
        assertThat(book.version).isEqualTo(metadata(BuiltInBookCatalog.defaultBook.assetFolder).version)
        assertThat(db.bookEntryDao().countInBook(existingId)).isGreaterThan(1000)
        assertThat(db.wordBookDao().getAll()).hasSize(BuiltInBookCatalog.books.size)
    }

    @Test
    fun legacyStarterBook_isMergedIntoDefault_keepingProgressAndHistory() = runTest {
        val legacyId = db.wordBookDao().insert(
            WordBookEntity(
                name = BuiltInBookCatalog.legacyStarterNames.first(),
                sourceType = BookSourceType.BUILT_IN.name,
                isEnabled = true,
                dailyNewLimit = 25,
                targetEpochDay = null,
                license = "MIT",
                version = "2.0.0",
                updatedAt = 0L
            )
        )
        val wordId = db.wordDao().insert(WordEntity(0L, "government", "government", "gʌvәnmәnt", "en", "zh-CN"))
        val entryId = db.bookEntryDao().insert(BookEntryEntity(0L, legacyId, wordId, 0))
        val cardId = db.reviewCardDao().insert(
            ReviewCardEntity(0L, entryId, Stage.REVIEW.name, 123L, 6.0, 2.6, 0, 1L)
        )
        db.reviewEventDao().insert(ReviewEventEntity(0L, "a1", cardId, "GOOD", "RECALL", 1L, 0L))

        val defaultId = importer.ensureStarterBook()

        assertThat(db.wordBookDao().getById(legacyId)).isNull()
        val defaultBook = db.wordBookDao().getById(defaultId)!!
        assertThat(defaultBook.isEnabled).isTrue()
        assertThat(defaultBook.dailyNewLimit).isEqualTo(25)
        val entry = db.bookEntryDao().getByBook(defaultId).first { it.wordId == wordId }
        val card = db.reviewCardDao().getByBookEntry(entry.id)!!
        assertThat(card.stage).isEqualTo(Stage.REVIEW.name)
        assertThat(card.intervalDays).isEqualTo(6.0)
        assertThat(db.reviewEventDao().getForCard(card.id, 10)).hasSize(1)
        // Old raw ECDICT phonetic was normalised.
        assertThat(db.wordDao().getById(wordId)!!.phonetic).startsWith("/")
    }

    @Test
    fun mergePhonetic_keepsUserValues_andReplacesRawOnes() {
        assertThat(StarterBookImporter.mergePhonetic(null, "/a/")).isEqualTo("/a/")
        assertThat(StarterBookImporter.mergePhonetic("gʌvәn", "/ˈgʌvən/")).isEqualTo("/ˈgʌvən/")
        assertThat(StarterBookImporter.mergePhonetic("/mine/", "/ˈgʌvən/")).isEqualTo("/mine/")
        assertThat(StarterBookImporter.mergePhonetic("/mine/", null)).isEqualTo("/mine/")
    }
}
