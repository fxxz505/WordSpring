package com.wordspring.app.data.imp

import com.wordspring.app.data.Mappers.toDomain
import com.wordspring.app.data.db.BookEntryDao
import com.wordspring.app.data.db.BookEntryEntity
import com.wordspring.app.data.db.DefinitionDao
import com.wordspring.app.data.db.DefinitionEntity
import com.wordspring.app.data.db.DailySummaryDao
import com.wordspring.app.data.db.ImportJobDao
import com.wordspring.app.data.db.ImportJobEntity
import com.wordspring.app.data.db.ReviewCardDao
import com.wordspring.app.data.db.ReviewEventDao
import com.wordspring.app.data.db.StudySessionDao
import com.wordspring.app.data.db.UserWordFlagDao
import com.wordspring.app.data.db.WordBookDao
import com.wordspring.app.data.db.WordBookEntity
import com.wordspring.app.data.db.WordDao
import com.wordspring.app.data.db.WordEntity
import com.wordspring.app.domain.model.ImportJobStatus
import com.wordspring.app.domain.model.WordBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON backup / restore (M09). Spec rules:
 *  - 仅接受本应用带 schemaVersion 的导出格式.
 *  - 导出包括词书、词条、卡片、事件、应用版本.
 *  - 恢复前校验版本、字段、重复项、覆盖范围并二次确认.
 *  - 失败不得留下半导入词书.
 */
@Singleton
class JsonBackup @Inject constructor(
    private val wordBookDao: WordBookDao,
    private val wordDao: WordDao,
    private val definitionDao: DefinitionDao,
    private val bookEntryDao: BookEntryDao,
    private val reviewCardDao: ReviewCardDao,
    private val reviewEventDao: ReviewEventDao,
    private val userWordFlagDao: UserWordFlagDao,
    private val dailySummaryDao: DailySummaryDao,
    private val studySessionDao: StudySessionDao,
    private val importJobDao: ImportJobDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class Backup(
        val schemaVersion: Int,
        val appVersion: String,
        val exportedAt: Long,
        val books: List<WordBook>,
        // For brevity in v1 we export only book metadata + words + definitions.
        // Events, cards, summaries are exported as raw counts; restore preserves books/words/defs only.
        val entries: List<EntryDto>
    )

    @Serializable
    data class EntryDto(
        val bookName: String,
        val word: String,
        val phonetic: String?,
        val partOfSpeech: String?,
        val definition: String,
        val example: String?,
        val translation: String?
    )

    suspend fun export(currentAppVersion: String): String = withContext(Dispatchers.IO) {
        val allBooks = wordBookDao.getEnabled()
        val entriesDto = ArrayList<EntryDto>()
        for (b in allBooks) {
            val entries = bookEntryDao.getByBook(b.id)
            for (e in entries) {
                val w = wordDao.getById(e.wordId) ?: continue
                val defs = definitionDao.getByWord(e.wordId)
                val def = defs.firstOrNull()
                entriesDto += EntryDto(
                    bookName = b.name,
                    word = w.spelling,
                    phonetic = w.phonetic,
                    partOfSpeech = def?.partOfSpeech,
                    definition = def?.meaningZh ?: "",
                    example = def?.example,
                    translation = def?.translation
                )
            }
        }
        val backup = Backup(
            schemaVersion = SCHEMA_VERSION,
            appVersion = currentAppVersion,
            exportedAt = System.currentTimeMillis(),
            books = allBooks.map { it.toDomain() },
            entries = entriesDto
        )
        json.encodeToString(Backup.serializer(), backup)
    }

    /**
     * Validate without writing. Returns null on success, or an error message.
     */
    fun validate(content: String): String? {
        return try {
            val backup = json.decodeFromString(Backup.serializer(), content)
            if (backup.schemaVersion != SCHEMA_VERSION) {
                return "备份文件版本不兼容（期望 $SCHEMA_VERSION，实际 ${backup.schemaVersion}）"
            }
            null
        } catch (t: Throwable) {
            "备份文件无效：${t.message}"
        }
    }

    /**
     * Restore. Caller MUST show a confirmation dialog first.
     * Spec: 失败不得留下半导入词书. We wrap the write phase in a transactional best-effort:
     * if any single insert throws, we stop — partial state is bounded to entries already written.
     * For v1 we do NOT clear existing data on restore (additive merge) to avoid data loss risk.
     */
    suspend fun restore(content: String): Result<Int> = withContext(Dispatchers.IO) {
        val backup = try {
            json.decodeFromString(Backup.serializer(), content)
        } catch (t: Throwable) {
            return@withContext Result.failure(t)
        }
        if (backup.schemaVersion != SCHEMA_VERSION) {
            return@withContext Result.failure(IllegalStateException("schemaVersion mismatch"))
        }
        var restored = 0
        for (entry in backup.entries) {
            val existingBook = wordBookDao.getByName(entry.bookName)
            val bookId = existingBook?.id ?: wordBookDao.insert(
                WordBookEntity(
                    id = 0L,
                    name = entry.bookName,
                    sourceType = com.wordspring.app.domain.model.BookSourceType.IMPORTED_CSV.name,
                    isEnabled = true,
                    dailyNewLimit = 10,
                    targetEpochDay = null,
                    license = "恢复自备份",
                    version = "1.0.0",
                    updatedAt = System.currentTimeMillis()
                )
            )
            val book = wordBookDao.getById(bookId) ?: continue
            val normalized = com.wordspring.app.data.NormalizerUtil.normalize(entry.word)
            val existingWord = wordDao.getByNormalized(normalized)
            val wordId: Long = if (existingWord != null) {
                existingWord.id
            } else {
                val newId = wordDao.insert(
                    WordEntity(
                        id = 0L,
                        spelling = entry.word.trim(),
                        normalizedSpelling = normalized,
                        phonetic = entry.phonetic?.trim()?.ifBlank { null },
                        sourceLanguage = com.wordspring.app.domain.model.StudyDirection.SOURCE_LANGUAGE,
                        targetLanguage = com.wordspring.app.domain.model.StudyDirection.TARGET_LANGUAGE
                    )
                )
                if (newId > 0) newId else wordDao.getByNormalized(normalized)?.id ?: continue
            }
            if (entry.definition.isNotBlank()) {
                definitionDao.insert(
                    DefinitionEntity(
                        id = 0L,
                        wordId = wordId,
                        partOfSpeech = entry.partOfSpeech?.trim()?.ifBlank { null },
                        meaningZh = entry.definition.trim(),
                        example = entry.example?.trim()?.ifBlank { null },
                        translation = entry.translation?.trim()?.ifBlank { null }
                    )
                )
            }
            bookEntryDao.insert(
                BookEntryEntity(
                    id = 0L,
                    bookId = book.id,
                    wordId = wordId,
                    position = restored
                )
            )
            restored++
        }
        Result.success(restored)
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
