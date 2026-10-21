package com.wordspring.app.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.wordspring.app.data.NormalizerUtil
import com.wordspring.app.data.db.BookEntryDao
import com.wordspring.app.data.db.BookEntryEntity
import com.wordspring.app.data.db.DefinitionDao
import com.wordspring.app.data.db.DefinitionEntity
import com.wordspring.app.data.db.DefinitionSource
import com.wordspring.app.data.db.ReviewCardDao
import com.wordspring.app.data.db.ReviewCardEntity
import com.wordspring.app.data.db.ReviewEventDao
import com.wordspring.app.data.db.WordBookDao
import com.wordspring.app.data.db.WordBookEntity
import com.wordspring.app.data.db.WordDao
import com.wordspring.app.data.db.WordEntity
import com.wordspring.app.data.db.WordSpringDatabase
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.Stage
import com.wordspring.app.domain.model.StudyDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the built-in book catalog in the database in sync with the bundled assets.
 *
 *  - First launch: every catalog book is imported; only [BuiltInBookCatalog.defaultBook] is enabled.
 *  - Asset version changed: the book is diffed in place. Words that stay keep their review card
 *    (and therefore the learner's progress); removed words are dropped; new words become NEW cards.
 *  - Legacy "WordSpring Complete 4500" is merged into the CET-4 book, carrying progress over.
 *  - Built-in meanings are tagged [DefinitionSource.BUILT_IN] and replaced on upgrade, so a word
 *    shared by several books never accumulates duplicate meanings.
 *
 * The work runs at most once per process; later calls return immediately.
 */
@Singleton
class StarterBookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: WordSpringDatabase,
    private val wordBookDao: WordBookDao,
    private val wordDao: WordDao,
    private val definitionDao: DefinitionDao,
    private val bookEntryDao: BookEntryDao,
    private val reviewCardDao: ReviewCardDao,
    private val reviewEventDao: ReviewEventDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val mutex = Mutex()
    @Volatile private var syncedDefaultBookId: Long? = null

    /** Ensures all built-in books exist and are current. Returns the default book id (or -1). */
    suspend fun ensureStarterBook(): Long {
        syncedDefaultBookId?.let { return it }
        return mutex.withLock {
            syncedDefaultBookId?.let { return@withLock it }
            val id = withContext(Dispatchers.IO) { syncCatalog() }
            if (id > 0) syncedDefaultBookId = id
            id
        }
    }

    private suspend fun syncCatalog(): Long {
        val builtIns = wordBookDao.getAll().filter { it.sourceType == BookSourceType.BUILT_IN.name }
        val freshInstall = builtIns.isEmpty()
        val idsBySpec = LinkedHashMap<BuiltInBookCatalog.Book, Long>()
        for (spec in BuiltInBookCatalog.books) {
            val metadata = loadMetadata(spec.assetFolder) ?: continue
            val existing = builtIns.firstOrNull { it.name == spec.name || it.name in spec.legacyNames }
            val bookId = when {
                existing == null -> importBook(spec, metadata, enabled = freshInstall && spec.enabledByDefault)
                existing.version != metadata.version || existing.name != metadata.name ->
                    upgradeBook(existing, spec, metadata)
                else -> existing.id
            }
            if (bookId > 0) idsBySpec[spec] = bookId
        }
        val defaultId = idsBySpec[BuiltInBookCatalog.defaultBook] ?: return -1L
        builtIns.filter { it.name in BuiltInBookCatalog.legacyStarterNames }
            .forEach { legacy -> mergeLegacyBook(legacy, defaultId) }
        return defaultId
    }

    private suspend fun importBook(
        spec: BuiltInBookCatalog.Book,
        metadata: StarterBookMetadata,
        enabled: Boolean
    ): Long {
        val entries = loadEntries(spec.assetFolder) ?: return -1L
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val bookId = wordBookDao.insert(
                WordBookEntity(
                    id = 0L,
                    name = metadata.name,
                    sourceType = BookSourceType.BUILT_IN.name,
                    isEnabled = enabled,
                    dailyNewLimit = metadata.dailyNewLimit.coerceIn(1, 100),
                    targetEpochDay = null,
                    license = metadata.license,
                    version = metadata.version,
                    updatedAt = now,
                )
            )
            val wordIds = upsertWords(entries)
            val bookEntries = wordIds.mapIndexed { position, wordId -> BookEntryEntity(0L, bookId, wordId, position) }
            bookEntryDao.insertAll(bookEntries)
            createMissingCards(bookId, now)
            bookId
        }
    }

    private suspend fun upgradeBook(
        existing: WordBookEntity,
        spec: BuiltInBookCatalog.Book,
        metadata: StarterBookMetadata
    ): Long {
        val entries = loadEntries(spec.assetFolder) ?: return existing.id
        database.withTransaction {
            val now = System.currentTimeMillis()
            val wordIds = upsertWords(entries)
            val newPositionByWord = HashMap<Long, Int>(wordIds.size * 2)
            wordIds.forEachIndexed { position, wordId -> newPositionByWord.putIfAbsent(wordId, position) }

            val current = bookEntryDao.getByBook(existing.id)
            val removed = current.filter { it.wordId !in newPositionByWord }.map { it.id }
            removed.chunked(CHUNK).forEach {
                reviewCardDao.deleteByBookEntryIds(it)
                bookEntryDao.deleteByIds(it)
            }
            val kept = current.filter { it.wordId in newPositionByWord }.associateBy { it.wordId }
            for ((wordId, entry) in kept) {
                val position = newPositionByWord.getValue(wordId)
                if (entry.position != position) bookEntryDao.updatePosition(entry.id, position)
            }
            val added = newPositionByWord.filterKeys { it !in kept }
                .map { (wordId, position) -> BookEntryEntity(0L, existing.id, wordId, position) }
            if (added.isNotEmpty()) bookEntryDao.insertAll(added)
            createMissingCards(existing.id, now)

            // Keep the learner's own settings (enabled flag, daily limit, target date).
            wordBookDao.update(
                existing.copy(
                    name = metadata.name,
                    license = metadata.license,
                    version = metadata.version,
                    updatedAt = now
                )
            )
        }
        return existing.id
    }

    /**
     * Moves progress from an old aggregate book into [targetBookId] for words both books share,
     * then deletes the old book. Review history follows the card so stats and streaks survive.
     */
    private suspend fun mergeLegacyBook(legacy: WordBookEntity, targetBookId: Long) {
        database.withTransaction {
            val target = wordBookDao.getById(targetBookId) ?: return@withTransaction
            val targetEntryByWord = bookEntryDao.getByBook(targetBookId).associateBy { it.wordId }
            val targetCardByEntry = reviewCardDao.getByBook(targetBookId).associateBy { it.bookEntryId }
            val legacyEntries = bookEntryDao.getByBook(legacy.id).associateBy { it.id }
            for (card in reviewCardDao.getByBook(legacy.id)) {
                if (card.stage == Stage.NEW.name) continue
                val wordId = legacyEntries[card.bookEntryId]?.wordId ?: continue
                val targetEntry = targetEntryByWord[wordId] ?: continue
                val targetCard = targetCardByEntry[targetEntry.id] ?: continue
                if (targetCard.stage != Stage.NEW.name) continue
                reviewCardDao.update(
                    targetCard.copy(
                        stage = card.stage,
                        dueAt = card.dueAt,
                        intervalDays = card.intervalDays,
                        ease = card.ease,
                        step = card.step,
                        updatedAt = card.updatedAt
                    )
                )
                reviewEventDao.reassignCard(card.id, targetCard.id)
            }
            if (legacy.isEnabled && !target.isEnabled) {
                wordBookDao.update(
                    target.copy(
                        isEnabled = true,
                        dailyNewLimit = legacy.dailyNewLimit,
                        targetEpochDay = legacy.targetEpochDay,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            reviewCardDao.deleteByBook(legacy.id)
            bookEntryDao.deleteByBook(legacy.id)
            wordBookDao.delete(legacy.id)
        }
    }

    /**
     * Inserts missing words, refreshes phonetic/forms of existing ones and syncs their built-in
     * meanings. Returns word ids in asset order (duplicates removed).
     */
    private suspend fun upsertWords(entries: List<StarterEntryDto>): List<Long> {
        val unique = entries.mapNotNull { entry ->
            val normalized = NormalizerUtil.normalize(entry.word)
            if (normalized.isBlank() || entry.resolvedSenses().isEmpty()) null else normalized to entry
        }.distinctBy { it.first }
        if (unique.isEmpty()) return emptyList()

        val existing = unique.map { it.first }.chunked(CHUNK)
            .flatMap { wordDao.getByNormalizedSpellings(it) }
            .associateBy { it.normalizedSpelling }
        val missing = unique.filter { it.first !in existing }.map { (normalized, entry) ->
            WordEntity(
                id = 0L,
                spelling = entry.word.trim(),
                normalizedSpelling = normalized,
                phonetic = entry.phonetic?.trim()?.ifBlank { null },
                sourceLanguage = StudyDirection.SOURCE_LANGUAGE,
                targetLanguage = StudyDirection.TARGET_LANGUAGE,
                forms = entry.forms?.trim()?.ifBlank { null }
            )
        }
        if (missing.isNotEmpty()) wordDao.insertAll(missing)
        for ((normalized, entry) in unique) {
            val word = existing[normalized] ?: continue
            val phonetic = mergePhonetic(word.phonetic, entry.phonetic)
            val forms = word.forms ?: entry.forms?.trim()?.ifBlank { null }
            if (phonetic != word.phonetic || forms != word.forms) {
                wordDao.updatePhoneticAndForms(word.id, phonetic, forms)
            }
        }

        val wordIdByNormalized = unique.map { it.first }.chunked(CHUNK)
            .flatMap { wordDao.getByNormalizedSpellings(it) }
            .associate { it.normalizedSpelling to it.id }
        val orderedIds = unique.mapNotNull { (normalized, entry) ->
            wordIdByNormalized[normalized]?.let { it to entry }
        }
        syncBuiltInDefinitions(orderedIds)
        return orderedIds.map { it.first }
    }

    private suspend fun syncBuiltInDefinitions(words: List<Pair<Long, StarterEntryDto>>) {
        val defsByWord = words.map { it.first }.chunked(CHUNK)
            .flatMap { definitionDao.getByWordIds(it) }
            .groupBy { it.wordId }
        val toDelete = ArrayList<Long>()
        val toInsert = ArrayList<DefinitionEntity>()
        for ((wordId, entry) in words) {
            val current = defsByWord[wordId].orEmpty()
            val builtIn = current.filter { it.source == DefinitionSource.BUILT_IN }
            val userMeanings = current.filter { it.source != DefinitionSource.BUILT_IN }.map { it.meaningZh.trim() }.toSet()
            val desired = entry.resolvedSenses()
                .map { (it.pos?.trim()?.ifBlank { null }) to it.zh.trim() }
                .filter { it.second !in userMeanings }
            if (builtIn.map { it.partOfSpeech to it.meaningZh } == desired) continue
            toDelete += builtIn.map { it.id }
            desired.mapTo(toInsert) { (pos, zh) ->
                DefinitionEntity(
                    id = 0L,
                    wordId = wordId,
                    partOfSpeech = pos,
                    meaningZh = zh,
                    example = entry.example?.trim()?.ifBlank { null },
                    translation = null,
                    source = DefinitionSource.BUILT_IN
                )
            }
        }
        toDelete.chunked(CHUNK).forEach { definitionDao.deleteByIds(it) }
        if (toInsert.isNotEmpty()) definitionDao.insertAll(toInsert)
    }

    private suspend fun createMissingCards(bookId: Long, now: Long) {
        val withCards = reviewCardDao.getByBook(bookId).mapTo(HashSet()) { it.bookEntryId }
        val cards = bookEntryDao.getByBook(bookId)
            .filter { it.id !in withCards }
            .map { ReviewCardEntity(0L, it.id, Stage.NEW.name, 0L, 0.0, 2.5, 0, now) }
        if (cards.isNotEmpty()) reviewCardDao.insertAll(cards)
    }

    private fun loadMetadata(assetFolder: String): StarterBookMetadata? = runCatching {
        val text = context.assets.open("books/$assetFolder/metadata.json").bufferedReader().use { it.readText() }
        json.decodeFromString(StarterBookMetadata.serializer(), text)
    }.getOrNull()

    private fun loadEntries(assetFolder: String): List<StarterEntryDto>? = runCatching {
        context.assets.open("books/$assetFolder/entries.json").bufferedReader().use { reader ->
            json.decodeFromString(ListSerializer(StarterEntryDto.serializer()), reader.readText())
        }
    }.getOrNull()

    companion object {
        /** Name of the book enabled for new users. */
        val STARTER_BOOK_NAME: String = BuiltInBookCatalog.defaultBook.name
        private const val CHUNK = 900

        /**
         * Built-in phonetics are IPA wrapped in slashes. Replace a missing value or one in the old
         * raw ECDICT style (no slashes, "ә"), but never overwrite a user-provided "/.../" value.
         */
        internal fun mergePhonetic(current: String?, incoming: String?): String? {
            val clean = incoming?.trim()?.ifBlank { null } ?: return current
            if (current.isNullOrBlank()) return clean
            return if (current.trim().startsWith("/")) current else clean
        }
    }
}
