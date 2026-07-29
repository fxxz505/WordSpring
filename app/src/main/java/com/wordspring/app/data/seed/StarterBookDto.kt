package com.wordspring.app.data.seed

import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.StudyDirection
import kotlinx.serialization.Serializable

/**
 * On-disk metadata for the built-in `WordSpring Starter 1200` book.
 * Loaded from assets/books/starter_1200/metadata.json.
 */
@Serializable
data class StarterBookMetadata(
    val id: String,                  // stable asset id, e.g. "starter_1200"
    val name: String,                // "WordSpring Starter 1200"
    val sourceType: String = BookSourceType.BUILT_IN.name,
    val dailyNewLimit: Int = 10,
    val license: String,
    val version: String,
    val updatedAt: String,           // ISO date
    val source: String,
    val wordCount: Int,
    val sourceLanguage: String = StudyDirection.SOURCE_LANGUAGE,
    val targetLanguage: String = StudyDirection.TARGET_LANGUAGE
)

@Serializable
data class StarterEntryDto(
    val word: String,
    val phonetic: String? = null,
    val partOfSpeech: String? = null,
    val definition: String,         // Chinese meaning
    val example: String? = null,
    val translation: String? = null
)

@Serializable
data class StarterBookPayload(
    val metadata: StarterBookMetadata,
    val entries: List<StarterEntryDto>
)
