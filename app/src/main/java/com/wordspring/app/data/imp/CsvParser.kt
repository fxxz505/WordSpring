package com.wordspring.app.data.imp

import com.wordspring.app.data.NormalizerUtil
import com.wordspring.app.domain.model.StudyDirection

/**
 * Pure CSV parsing (M09). No Android, no DB.
 *
 * Spec: UTF-8, header `word,phonetic,partOfSpeech,definition,example,translation`.
 * Quoted fields use RFC 4180 style with "" escaping.
 */
object CsvParser {

    data class Row(
        val lineNumber: Int,
        val dto: StarterEntryDto?,
        val error: String?
    )

    data class StarterEntryDto(
        val word: String,
        val phonetic: String?,
        val partOfSpeech: String?,
        val definition: String,
        val example: String?,
        val translation: String?
    )

    data class ParseResult(
        val rows: List<Row>,
        val validCount: Int,
        val errorCount: Int,
        val duplicateCount: Int
    ) {
        val validRows: List<StarterEntryDto> get() = rows.mapNotNull { it.dto }
    }

    private val REQUIRED_HEADER = "word"
    private val HEADER_FIELDS = listOf("word", "phonetic", "partOfSpeech", "definition", "example", "translation")

    fun parse(content: String): ParseResult {
        if (content.isBlank()) return ParseResult(emptyList(), 0, 1, 0)

        val lines = splitLines(content)
        if (lines.isEmpty()) return ParseResult(emptyList(), 0, 1, 0)

        val header = parseLine(lines.first())
        val wordIdx = header.indexOfFirst { it.trim().equals(REQUIRED_HEADER, ignoreCase = true) }
        if (wordIdx < 0) {
            return ParseResult(
                rows = listOf(Row(lineNumber = 1, dto = null, error = "首行缺少 word 字段")),
                validCount = 0, errorCount = 1, duplicateCount = 0
            )
        }
        val phoneticIdx = header.indexOfFirst { it.equals("phonetic", true) }
        val posIdx = header.indexOfFirst { it.equals("partOfSpeech", true) }
        val defIdx = header.indexOfFirst { it.equals("definition", true) }
        val exampleIdx = header.indexOfFirst { it.equals("example", true) }
        val translationIdx = header.indexOfFirst { it.equals("translation", true) }

        val seen = HashSet<String>()
        val outRows = ArrayList<Row>()
        var valid = 0
        var errors = 0
        var dupes = 0

        for (i in 1 until lines.size) {
            val lineNum = i + 1
            val raw = lines[i]
            if (raw.isBlank()) continue
            val fields = parseLine(raw)
            val word = fields.getOrNull(wordIdx)?.trim().orEmpty()
            if (word.isEmpty()) {
                outRows += Row(lineNum, null, "缺少 word 字段")
                errors++
                continue
            }
            val normalized = NormalizerUtil.normalize(word)
            if (!seen.add(normalized)) {
                dupes++
                // We still count it as valid (will be merged at insert time)
                outRows += Row(
                    lineNum,
                    StarterEntryDto(
                        word = word,
                        phonetic = fieldOrNull(fields, phoneticIdx),
                        partOfSpeech = fieldOrNull(fields, posIdx),
                        definition = fieldOrNull(fields, defIdx) ?: "",
                        example = fieldOrNull(fields, exampleIdx),
                        translation = fieldOrNull(fields, translationIdx)
                    ),
                    error = null
                )
                valid++
                continue
            }
            outRows += Row(
                lineNum,
                StarterEntryDto(
                    word = word,
                    phonetic = fieldOrNull(fields, phoneticIdx),
                    partOfSpeech = fieldOrNull(fields, posIdx),
                    definition = fieldOrNull(fields, defIdx) ?: "",
                    example = fieldOrNull(fields, exampleIdx),
                    translation = fieldOrNull(fields, translationIdx)
                ),
                error = null
            )
            valid++
        }

        return ParseResult(outRows, valid, errors, dupes)
    }

    private fun fieldOrNull(fields: List<String>, idx: Int): String? =
        if (idx in fields.indices) fields[idx].trim().ifBlank { null } else null

    private fun splitLines(content: String): List<String> {
        // Handle \r\n and \n
        return content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
            .filter { it.isNotBlank() }
    }

    private fun parseLine(line: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i += 2; continue
                }
                c == '"' -> { inQuotes = !inQuotes; i++; continue }
                c == ',' && !inQuotes -> { result.add(current.toString()); current.setLength(0); i++; continue }
                else -> { current.append(c); i++ }
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * Serialise a list of entries back to CSV (used by export of a custom book).
     */
    fun serialize(entries: List<StarterEntryDto>): String {
        val sb = StringBuilder()
        sb.append(HEADER_FIELDS.joinToString(",") { it })
        sb.append("\n")
        for (e in entries) {
            val row = listOf(
                csv(e.word),
                csv(e.phonetic ?: ""),
                csv(e.partOfSpeech ?: ""),
                csv(e.definition),
                csv(e.example ?: ""),
                csv(e.translation ?: "")
            ).joinToString(",")
            sb.append(row)
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun csv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
