package com.wordspring.app.feature.word

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.Definition
import org.junit.Test

class WordDetailDefinitionsTest {

    @Test
    fun `identical legacy definitions are shown only once`() {
        val definitions = List(4) { index ->
            Definition(
                id = index.toLong(),
                wordId = 1L,
                partOfSpeech = null,
                meaningZh = "vt. 涂, 应用\\nvi. 申请, 适用",
                example = null,
                translation = "vt. 涂, 应用\\nvi. 申请, 适用"
            )
        }

        assertThat(distinctDefinitionsForDetail(definitions)).hasSize(1)
    }
}
