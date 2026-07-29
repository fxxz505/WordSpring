package com.wordspring.app.feature.study

import com.wordspring.app.domain.model.Definition

/** Presentation-only normalization for legacy and imported dictionary meanings. */
data class DefinitionDisplay(
    val lines: List<String>,
    val primaryMeaning: String,
    val translationLines: List<String>
)

object DefinitionDisplayFormatter {
    private val inlinePartOfSpeech = Regex(
        pattern = "(?i)^(?:abbr|prep|pron|conj|adj|adv|num|art|aux|int|vt|vi|v|n)\\.?\\s*"
    )

    fun format(definition: Definition): DefinitionDisplay {
        val normalizedMeaning = definition.meaningZh
            .replace("\\r\\n", "\\n")
            .replace("\\n", "\n")
            .replace("/n", "\n")

        val lines = normalizedMeaning
            .lineSequence()
            .map(::normalizeLine)
            .filter(String::isNotBlank)
            .toList()
            .let { rawLines ->
                if (rawLines.isEmpty()) emptyList()
                else if (inlinePartOfSpeech.containsMatchIn(rawLines.first()) || definition.partOfSpeech.isNullOrBlank()) rawLines
                else listOf("${definition.partOfSpeech.trim()} ${rawLines.first()}") + rawLines.drop(1)
            }

        val primaryMeaning = lines.firstOrNull()
            ?.replaceFirst(inlinePartOfSpeech, "")
            ?.substringBefore('、')
            ?.trim()
            .orEmpty()

        val translationLines = definition.translation
            ?.let(::normalizeMeaningLines)
            .orEmpty()
            .filterNot { it in lines }

        return DefinitionDisplay(
            lines = lines,
            primaryMeaning = primaryMeaning,
            translationLines = translationLines
        )
    }

    private fun normalizeMeaningLines(value: String): List<String> = value
        .replace("\\r\\n", "\\n")
        .replace("\\n", "\n")
        .replace("/n", "\n")
        .lineSequence()
        .map(::normalizeLine)
        .filter(String::isNotBlank)
        .toList()

    private fun normalizeLine(line: String): String = line
        .trim()
        .replace(Regex("\\s*[,，]\\s*"), "、")
        .replace(Regex("\\s+"), " ")
}
