package com.wordspring.app.feature.study

import com.wordspring.app.domain.model.Definition
import kotlin.random.Random

/** One part-of-speech group on a card, e.g. pos = "vt.", text = "放弃；抛弃". */
data class SenseLine(val pos: String?, val text: String) {
    /** Individual meanings inside [text]. */
    val items: List<String>
        get() = text.split('；', '、', ';').map { it.trim() }.filter { it.isNotEmpty() }
}

/** One inflected form, e.g. label = "过去式", value = "ran". */
data class WordFormItem(val label: String, val value: String)

private val leadingPartOfSpeech = Regex(
    "^(abbr|prep|pron|conj|adj|adv|num|art|aux|interj|int|vt|vi|pl|pref|suf|v|n|a)\\.\\s*",
    RegexOption.IGNORE_CASE
)
private val posAliases = mapOf("a." to "adj.", "int." to "interj.")

/**
 * Turns stored definitions (new per-POS rows or legacy multi-line strings) into
 * de-duplicated card lines grouped by part of speech, keeping source order.
 */
fun senseLines(definitions: List<Definition>): List<SenseLine> {
    val order = mutableListOf<String?>()
    val grouped = LinkedHashMap<String?, MutableList<String>>()
    for (definition in definitions) {
        for (line in DefinitionDisplayFormatter.format(definition).lines) {
            val match = leadingPartOfSpeech.find(line)
            val pos = match?.value?.trim()?.lowercase()?.let { posAliases[it] ?: it }
            val body = (if (match != null) line.substring(match.range.last + 1) else line).trim()
            if (body.isEmpty()) continue
            val bucket = grouped.getOrPut(pos) { order += pos; mutableListOf() }
            body.split('；', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                if (it !in bucket) bucket += it
            }
        }
    }
    return order.map { SenseLine(it, grouped.getValue(it).joinToString("；")) }
}

private val formLabels = linkedMapOf(
    "p" to "过去式",
    "d" to "过去分词",
    "i" to "现在分词",
    "3" to "第三人称单数",
    "s" to "复数",
    "r" to "比较级",
    "t" to "最高级"
)

/** Parses the ECDICT-style exchange string "p:ran/d:run/i:running" into labelled forms. */
fun parseWordForms(forms: String?): List<WordFormItem> {
    if (forms.isNullOrBlank()) return emptyList()
    val values = forms.split('/').mapNotNull { item ->
        val code = item.substringBefore(':', "").trim()
        val value = item.substringAfter(':', "").trim()
        if (code in formLabels && value.isNotEmpty()) code to value else null
    }.toMap()
    return formLabels.mapNotNull { (code, label) -> values[code]?.let { WordFormItem(label, it) } }
}

/** Short option text: part of speech plus at most [maxItems] meanings. */
internal fun choiceText(line: SenseLine, maxItems: Int = 3): String {
    val meanings = line.items.take(maxItems).joinToString("；")
    return listOfNotNull(line.pos, meanings).joinToString(" ")
}

/**
 * Builds a 4-way multiple choice: the word's first meaning plus up to three distractors taken
 * from [candidates] (meanings of other words in the same book). Distractors that share any
 * meaning with the correct word are skipped so exactly one option is right. Never pads with
 * placeholder options; if the book is tiny there are simply fewer options.
 */
internal fun buildChoiceOptions(
    correctDefinitions: List<Definition>,
    candidates: List<Definition>,
    random: Random = Random.Default,
    optionCount: Int = 4
): List<ChoiceOption> {
    val correctLines = senseLines(correctDefinitions)
    val first = correctLines.firstOrNull() ?: return emptyList()
    val correctMeanings = correctLines.flatMap { it.items }.toSet()
    val correct = ChoiceOption(choiceText(first), isCorrect = true, definition = correctDefinitions.first())

    val seenTexts = mutableSetOf(correct.text)
    val distractors = mutableListOf<ChoiceOption>()
    for (candidate in candidates) {
        if (distractors.size >= optionCount - 1) break
        val line = senseLines(listOf(candidate)).firstOrNull() ?: continue
        if (line.items.any { it in correctMeanings }) continue
        val text = choiceText(line)
        if (!seenTexts.add(text)) continue
        distractors += ChoiceOption(text, isCorrect = false, definition = candidate)
    }
    return (distractors + correct).shuffled(random)
}
