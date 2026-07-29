package com.wordspring.app.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.wordspring.app.data.db.WordSpringDatabase
import com.wordspring.app.data.NormalizerUtil
import com.wordspring.app.data.db.BookEntryEntity
import com.wordspring.app.data.db.DefinitionEntity
import com.wordspring.app.data.db.ReviewCardDao
import com.wordspring.app.data.db.ReviewCardEntity
import com.wordspring.app.data.db.WordBookDao
import com.wordspring.app.data.db.WordBookEntity
import com.wordspring.app.data.db.WordDao
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.StudyDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Idempotent first-launch importer for the built-in book catalog.
 *
 * Idempotency:
 *  - Built-in books are matched by current name or their original catalog order, so existing sample books upgrade in place.
 *  - Within the book, de-dupe by normalizedSpelling (NFKC + lowercase).
 */
@Singleton
class StarterBookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: WordSpringDatabase,
    private val wordBookDao: WordBookDao,
    private val wordDao: WordDao,
    private val definitionDao: com.wordspring.app.data.db.DefinitionDao,
    private val bookEntryDao: com.wordspring.app.data.db.BookEntryDao,
    private val reviewCardDao: ReviewCardDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun ensureStarterBook(): Long = withContext(Dispatchers.IO) {
        var starterBookId = -1L
        val previousBuiltIns = wordBookDao.getAll().filter { it.sourceType == BookSourceType.BUILT_IN.name }
        BuiltInBookCatalog.books.forEachIndexed { index, spec ->
            val existing = wordBookDao.getByName(spec.name) ?: previousBuiltIns.getOrNull(index)
            val bookId = when {
                existing == null -> importBook(spec)
                bookEntryDao.countInBook(existing.id) < spec.minimumWordCount -> upgradeBook(existing, spec)
                else -> existing.id
            }
            if (spec.name == STARTER_BOOK_NAME) starterBookId = bookId
        }
        starterBookId
    }

    private suspend fun importBook(spec: BuiltInBookCatalog.Book): Long {
        val payload = loadPayload(spec.assetFolder) ?: return -1L
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val bookId = wordBookDao.insert(
                WordBookEntity(
                    id = 0L,
                    name = payload.metadata.name,
                    sourceType = BookSourceType.BUILT_IN.name,
                    isEnabled = spec.enabledByDefault,
                    dailyNewLimit = payload.metadata.dailyNewLimit.coerceIn(1, 100),
                    targetEpochDay = null,
                    license = payload.metadata.license,
                    version = payload.metadata.version,
                    updatedAt = now,
                )
            )
            insertEntries(bookId, payload.entries, now)
            bookId
        }
    }

    private suspend fun upgradeBook(existing: WordBookEntity, spec: BuiltInBookCatalog.Book): Long {
        val payload = loadPayload(spec.assetFolder) ?: return existing.id
        database.withTransaction {
            reviewCardDao.deleteByBook(existing.id)
            bookEntryDao.deleteByBook(existing.id)
            wordBookDao.update(
                existing.copy(
                    name = payload.metadata.name,
                    dailyNewLimit = payload.metadata.dailyNewLimit.coerceIn(1, 100),
                    license = payload.metadata.license,
                    version = payload.metadata.version,
                    updatedAt = System.currentTimeMillis()
                )
            )
            insertEntries(existing.id, payload.entries, System.currentTimeMillis())
        }
        return existing.id
    }

    private suspend fun insertEntries(bookId: Long, entries: List<StarterEntryDto>, now: Long) {
        val uniqueEntries = entries.mapNotNull { entry ->
            val normalized = NormalizerUtil.normalize(entry.word)
            if (normalized.isBlank()) null else normalized to entry
        }.distinctBy { it.first }
        if (uniqueEntries.isEmpty()) return

        // SQLite limits IN bindings, so resolve shared words in bounded batches.
        val normalizedSpellings = uniqueEntries.map { it.first }
        val existingByNormalized = normalizedSpellings.chunked(900)
            .flatMap { wordDao.getByNormalizedSpellings(it) }
            .associateBy { it.normalizedSpelling }
        val missingWords = uniqueEntries.filter { (normalized, _) -> normalized !in existingByNormalized }
            .map { (normalized, entry) ->
                com.wordspring.app.data.db.WordEntity(
                    id = 0L,
                    spelling = entry.word.trim(),
                    normalizedSpelling = normalized,
                    phonetic = entry.phonetic?.trim()?.ifBlank { null },
                    sourceLanguage = StudyDirection.SOURCE_LANGUAGE,
                    targetLanguage = StudyDirection.TARGET_LANGUAGE
                )
            }
        if (missingWords.isNotEmpty()) wordDao.insertAll(missingWords)

        val wordIdByNormalized = normalizedSpellings.chunked(900)
            .flatMap { wordDao.getByNormalizedSpellings(it) }
            .associate { it.normalizedSpelling to it.id }

        val definitions = uniqueEntries.mapNotNull { (normalized, entry) ->
            val wordId = wordIdByNormalized[normalized] ?: return@mapNotNull null
            if (entry.definition.isBlank()) return@mapNotNull null
            DefinitionEntity(
                id = 0L,
                wordId = wordId,
                partOfSpeech = entry.partOfSpeech?.trim()?.ifBlank { null },
                meaningZh = entry.definition.trim(),
                example = entry.example?.trim()?.ifBlank { null },
                translation = entry.translation?.trim()?.ifBlank { null }
            )
        }
        if (definitions.isNotEmpty()) definitionDao.insertAll(definitions)

        val bookEntries = uniqueEntries.mapIndexedNotNull { position, (normalized, _) ->
            wordIdByNormalized[normalized]?.let { wordId -> BookEntryEntity(0L, bookId, wordId, position) }
        }
        if (bookEntries.isEmpty()) return
        bookEntryDao.insertAll(bookEntries)

        val cards = bookEntryDao.getByBook(bookId).map { entry ->
            ReviewCardEntity(0L, entry.id, com.wordspring.app.domain.model.Stage.NEW.name,
                0L, 0.0, 2.5, 0, now)
        }
        if (cards.isNotEmpty()) reviewCardDao.insertAll(cards)
    }

    private fun loadPayload(assetFolder: String): StarterBookPayload? {
        return try {
            val meta = context.assets.open("books/$assetFolder/metadata.json").bufferedReader()
                .use { it.readText() }
            val entries = context.assets.open("books/$assetFolder/entries.json").bufferedReader()
                .use { it.readText() }
            val metadata = json.decodeFromString(StarterBookMetadata.serializer(), meta)
            val entryList = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(StarterEntryDto.serializer()),
                entries
            )
            StarterBookPayload(metadata = metadata, entries = entryList)
        } catch (t: Throwable) {
            null
        }
    }

    companion object {
        const val STARTER_BOOK_NAME = "WordSpring Complete 4500"
    }
}
