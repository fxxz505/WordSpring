package com.wordspring.app.data.imp

import com.wordspring.app.data.Mappers.toDomain
import com.wordspring.app.data.Mappers.toEntity
import com.wordspring.app.data.NormalizerUtil
import com.wordspring.app.data.db.BookEntryEntity
import com.wordspring.app.data.db.DefinitionEntity
import com.wordspring.app.data.db.ReviewCardDao
import com.wordspring.app.data.db.ReviewCardEntity
import com.wordspring.app.data.db.WordBookDao
import com.wordspring.app.data.db.WordBookEntity
import com.wordspring.app.data.db.WordDao
import com.wordspring.app.data.db.DefinitionDao
import com.wordspring.app.data.db.BookEntryDao
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.Stage
import com.wordspring.app.domain.model.StudyDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a parsed CSV preview to a target book (M09). Spec rules:
 *  - 重复项默认合并到已有词条，不覆盖已有复习记录.
 *  - 用户确认前绝不写库.
 *  - 失败不得留下半导入词书（这里因为只追加 entries/defs/words，单次操作可回滚由调用方决定）.
 */
@Singleton
class CsvImporter @Inject constructor(
    private val wordBookDao: WordBookDao,
    private val wordDao: WordDao,
    private val definitionDao: DefinitionDao,
    private val bookEntryDao: BookEntryDao,
    private val reviewCardDao: ReviewCardDao
) {

    /**
     * @param bookId target book. If null, creates a new CUSTOM book named [newBookName].
     * @return the actual bookId written into.
     */
    suspend fun import(
        parsed: CsvParser.ParseResult,
        bookId: Long?,
        newBookName: String?
    ): Long = withContext(Dispatchers.IO) {
        val targetBookId = if (bookId != null && bookId > 0L) {
            bookId
        } else {
            val now = System.currentTimeMillis()
            wordBookDao.insert(
                WordBookEntity(
                    id = 0L,
                    name = (newBookName ?: "导入词集 ${now}").trim(),
                    sourceType = BookSourceType.IMPORTED_CSV.name,
                    isEnabled = true,
                    dailyNewLimit = 10,
                    targetEpochDay = null,
                    license = "用户导入",
                    version = "1.0.0",
                    updatedAt = now
                )
            )
        }

        // Fetch existing max position for the book to continue sequencing.
        val existingEntries = bookEntryDao.getByBook(targetBookId)
        var position = existingEntries.maxOfOrNull { it.position }?.plus(1) ?: 0
        val seen = HashSet<String>(existingEntries.size)
        for (e in existingEntries) {
            val w = wordDao.getById(e.wordId)
            if (w != null) seen.add(w.normalizedSpelling)
        }
        val now = System.currentTimeMillis()

        for (row in parsed.rows) {
            val dto = row.dto ?: continue
            val normalized = NormalizerUtil.normalize(dto.word)
            if (normalized.isBlank() || !seen.add(normalized)) continue

            val existingWord = wordDao.getByNormalized(normalized)
            val wordId = existingWord?.id ?: wordDao.insert(
                com.wordspring.app.data.db.WordEntity(
                    id = 0L,
                    spelling = dto.word.trim(),
                    normalizedSpelling = normalized,
                    phonetic = dto.phonetic?.trim()?.ifBlank { null },
                    sourceLanguage = StudyDirection.SOURCE_LANGUAGE,
                    targetLanguage = StudyDirection.TARGET_LANGUAGE
                )
            )
            if (dto.definition.isNotBlank() &&
                definitionDao.countSameMeaning(wordId, dto.definition.trim()) == 0
            ) {
                definitionDao.insert(
                    DefinitionEntity(
                        id = 0L,
                        wordId = wordId,
                        partOfSpeech = dto.partOfSpeech?.trim()?.ifBlank { null },
                        meaningZh = dto.definition.trim(),
                        example = dto.example?.trim()?.ifBlank { null },
                        translation = dto.translation?.trim()?.ifBlank { null }
                    )
                )
            }
            val bookEntryId = bookEntryDao.insert(
                BookEntryEntity(
                    id = 0L,
                    bookId = targetBookId,
                    wordId = wordId,
                    position = position++
                )
            )

            // M01 spec: every book entry must have a corresponding NEW review card
            // so the scheduler can pick it up as a new card for study sessions.
            // Skip if a card already exists (e.g., re-importing into an existing book).
            if (reviewCardDao.getByBookEntry(bookEntryId) == null) {
                reviewCardDao.insert(
                    ReviewCardEntity(
                        id = 0L,
                        bookEntryId = bookEntryId,
                        stage = Stage.NEW.name,
                        dueAt = 0L, // NEW cards are always due immediately
                        intervalDays = 0.0,
                        ease = 2.5,
                        step = 0,
                        updatedAt = now
                    )
                )
            }
        }
        targetBookId
    }
}
