package com.wordspring.app.feature.study

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.Definition
import org.junit.Test
import kotlin.random.Random

class CardContentTest {

    private fun def(id: Long, pos: String?, zh: String, wordId: Long = 1L) =
        Definition(id, wordId, pos, zh, null, null)

    @Test
    fun `sense lines group by part of speech and drop duplicates`() {
        val lines = senseLines(
            listOf(
                def(1, "vt.", "放弃；抛弃"),
                def(2, "n.", "放任"),
                def(3, "vt.", "抛弃；遗弃")
            )
        )
        assertThat(lines.map { it.pos }).containsExactly("vt.", "n.").inOrder()
        assertThat(lines.first().items).containsExactly("放弃", "抛弃", "遗弃").inOrder()
    }

    @Test
    fun `word forms are labelled in a fixed order and unknown codes ignored`() {
        val forms = parseWordForms("i:running/p:ran/x:zzz/d:run/3:runs")
        assertThat(forms.map { it.label }).containsExactly("过去式", "过去分词", "现在分词", "第三人称单数").inOrder()
        assertThat(forms.first().value).isEqualTo("ran")
        assertThat(parseWordForms(null)).isEmpty()
        assertThat(parseWordForms("  ")).isEmpty()
    }

    @Test
    fun `choice options have exactly one correct answer and no placeholder padding`() {
        val correct = listOf(def(1, "vt.", "放弃；抛弃"))
        val candidates = listOf(
            def(10, "vt.", "抛弃；扔掉", wordId = 2), // shares a meaning -> skipped
            def(11, "n.", "能力", wordId = 3),
            def(12, "n.", "能力", wordId = 4),         // same text -> skipped
            def(13, "adj.", "美丽的", wordId = 5)
        )
        val options = buildChoiceOptions(correct, candidates, Random(7))
        assertThat(options).hasSize(3)
        assertThat(options.count { it.isCorrect }).isEqualTo(1)
        assertThat(options.map { it.text }).containsNoDuplicates()
        assertThat(options.map { it.text }).doesNotContain("vt. 抛弃；扔掉")
        assertThat(options.first { it.isCorrect }.text).isEqualTo("vt. 放弃；抛弃")
    }

    @Test
    fun `choice text keeps at most three meanings`() {
        assertThat(choiceText(SenseLine("n.", "一；二；三；四"))).isEqualTo("n. 一；二；三")
        assertThat(choiceText(SenseLine(null, "甲"))).isEqualTo("甲")
    }

    @Test
    fun `no definitions means no choice options`() {
        assertThat(buildChoiceOptions(emptyList(), listOf(def(2, "n.", "猫")))).isEmpty()
    }
}
