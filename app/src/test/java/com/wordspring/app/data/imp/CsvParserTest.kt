package com.wordspring.app.data.imp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure unit tests for the M09 CSV parser.
 * Covers: normal rows, missing required fields, error rows, duplicates, RFC 4180 quoting,
 * serialization round-trip, header with extra columns.
 */
class CsvParserTest {

    @Test
    fun parse_empty_returnsError() {
        val result = CsvParser.parse("")
        assertThat(result.validCount).isEqualTo(0)
        assertThat(result.errorCount).isAtLeast(1)
    }

    @Test
    fun parse_blank_returnsError() {
        val result = CsvParser.parse("   \n   ")
        assertThat(result.validCount).isEqualTo(0)
    }

    @Test
    fun parse_missingWordHeader_returnsError() {
        val csv = "phonetic,definition\n/foo/,bar"
        val result = CsvParser.parse(csv)
        assertThat(result.validCount).isEqualTo(0)
        assertThat(result.errorCount).isEqualTo(1)
        assertThat(result.rows.first().error).contains("word")
    }

    @Test
    fun parse_normalRows_countsValid() {
        val csv = """
            word,phonetic,partOfSpeech,definition,example,translation
            apple,/ˈæp.əl/,n.,苹果,An apple a day.,一天一苹果。
            banana,/bəˈnɑː.nə/,n.,香蕉,Monkeys like bananas.,猴子喜欢香蕉。
        """.trimIndent()
        val result = CsvParser.parse(csv)
        assertThat(result.errorCount).isEqualTo(0)
        assertThat(result.validCount).isEqualTo(2)
        assertThat(result.rows).hasSize(2)
        assertThat(result.validRows.map { it.word }).containsExactly("apple", "banana")
    }

    @Test
    fun parse_rowMissingWord_reportsErrorWithLineNumber() {
        val csv = """
            word,phonetic,partOfSpeech,definition,example,translation
            apple,/ˈæp.əl/,n.,苹果,An apple a day.,一天一苹果。
            ,/foo/,n.,should-fail,,
            banana,/bəˈnɑː.nə/,n.,香蕉,Monkeys like bananas.,猴子喜欢香蕉。
        """.trimIndent()
        val result = CsvParser.parse(csv)
        assertThat(result.errorCount).isEqualTo(1)
        assertThat(result.validCount).isEqualTo(2)
        val errorRow = result.rows.firstOrNull { it.error != null }
        assertThat(errorRow).isNotNull()
        assertThat(errorRow!!.lineNumber).isEqualTo(3) // header + 2 rows = line 3 is the empty-word row
        assertThat(errorRow.error).contains("word")
    }

    @Test
    fun parse_duplicateWords_countsDuplicates() {
        val csv = """
            word,definition
            apple,苹果
            apple,苹果2
        """.trimIndent()
        val result = CsvParser.parse(csv)
        assertThat(result.validCount).isEqualTo(2)
        assertThat(result.duplicateCount).isEqualTo(1)
    }

    @Test
    fun parse_rfc4180Quoting_handlesCommasInFields() {
        val csv = """
            word,definition,example
            test,"a, b, c","He said, ""hi""."
        """.trimIndent()
        val result = CsvParser.parse(csv)
        assertThat(result.errorCount).isEqualTo(0)
        assertThat(result.validCount).isEqualTo(1)
        val row = result.validRows.first()
        assertThat(row.word).isEqualTo("test")
        assertThat(row.definition).isEqualTo("a, b, c")
        assertThat(row.example).isEqualTo("He said, \"hi\".")
    }

    @Test
    fun parse_extraColumnsInHeader_areIgnored() {
        val csv = """
            word,definition,extra1,extra2
            apple,苹果,foo,bar
        """.trimIndent()
        val result = CsvParser.parse(csv)
        assertThat(result.errorCount).isEqualTo(0)
        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.validRows.first().word).isEqualTo("apple")
        assertThat(result.validRows.first().definition).isEqualTo("苹果")
    }

    @Test
    fun parse_blankLines_areSkipped() {
        val csv = "word,definition\n\napple,苹果\n\nbanana,香蕉\n"
        val result = CsvParser.parse(csv)
        assertThat(result.validCount).isEqualTo(2)
    }

    @Test
    fun parse_missingOptionalFields_keepsValid() {
        val csv = """
            word,phonetic,partOfSpeech,definition,example,translation
            apple,,,苹果,,
        """.trimIndent()
        val result = CsvParser.parse(csv)
        assertThat(result.validCount).isEqualTo(1)
        val row = result.validRows.first()
        assertThat(row.phonetic).isNull()
        assertThat(row.partOfSpeech).isNull()
        assertThat(row.example).isNull()
        assertThat(row.translation).isNull()
        assertThat(row.definition).isEqualTo("苹果")
    }

    @Test
    fun parse_definitionDefaultsToEmpty_whenMissing() {
        val csv = "word\napple\n"
        val result = CsvParser.parse(csv)
        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.validRows.first().definition).isEmpty()
    }

    @Test
    fun serialize_roundTripsBasicFields() {
        val entries = listOf(
            CsvParser.StarterEntryDto(
                word = "apple",
                phonetic = "/ˈæp.əl/",
                partOfSpeech = "n.",
                definition = "苹果",
                example = "An apple a day.",
                translation = "一天一苹果。"
            )
        )
        val csv = CsvParser.serialize(entries)
        val parsed = CsvParser.parse(csv)
        assertThat(parsed.validCount).isEqualTo(1)
        val row = parsed.validRows.first()
        assertThat(row.word).isEqualTo("apple")
        assertThat(row.definition).isEqualTo("苹果")
        assertThat(row.example).isEqualTo("An apple a day.")
    }

    @Test
    fun serialize_quotesFieldsWithCommas() {
        val entries = listOf(
            CsvParser.StarterEntryDto(
                word = "test",
                phonetic = null,
                partOfSpeech = null,
                definition = "a, b, c",
                example = null,
                translation = null
            )
        )
        val csv = CsvParser.serialize(entries)
        // The definition field should be quoted because it contains a comma
        assertThat(csv).contains("\"a, b, c\"")
    }
}
