package com.wordspring.app.feature.study

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.Definition
import org.junit.Test

class DefinitionDisplayFormatterTest {

    @Test
    fun `escaped newline separates parts of speech and retains the source primary meaning first`() {
        val display = DefinitionDisplayFormatter.format(
            definition = definition(
                meaning = "vt. 放弃, 抛弃, 遗弃, 使屈从, 沉溺, 放纵\\nn. 放任, 无拘束, 狂热"
            )
        )

        assertThat(display.lines).containsExactly(
            "vt. 放弃、抛弃、遗弃、使屈从、沉溺、放纵",
            "n. 放任、无拘束、狂热"
        ).inOrder()
        assertThat(display.primaryMeaning).isEqualTo("放弃")
    }

    @Test
    fun `actual newlines and literal slash n never render as stray characters`() {
        val display = DefinitionDisplayFormatter.format(
            definition = definition(meaning = "adj. 清晰\nadv. 清楚地\\n")
        )

        assertThat(display.lines).containsExactly("adj. 清晰", "adv. 清楚地").inOrder()
        assertThat(display.lines.joinToString()).doesNotContain("\\n")
    }

    @Test
    fun `external part of speech prefixes a meaning that has no inline part of speech`() {
        val display = DefinitionDisplayFormatter.format(
            definition = definition(partOfSpeech = "v.", meaning = "记录, 记载")
        )

        assertThat(display.lines).containsExactly("v. 记录、记载")
        assertThat(display.primaryMeaning).isEqualTo("记录")
    }

    @Test
    fun `duplicate legacy translation is omitted after meaning normalization`() {
        val display = DefinitionDisplayFormatter.format(
            definition = definition(
                meaning = "vt. 涂, 应用\\nvi. 申请, 适用",
                translation = "vt. 涂, 应用\\nvi. 申请, 适用"
            )
        )

        assertThat(display.lines).containsExactly("vt. 涂、应用", "vi. 申请、适用").inOrder()
        assertThat(display.translationLines).isEmpty()
    }

    private fun definition(partOfSpeech: String? = null, meaning: String, translation: String? = null): Definition = Definition(
        id = 1L,
        wordId = 1L,
        partOfSpeech = partOfSpeech,
        meaningZh = meaning,
        example = null,
        translation = translation
    )
}
