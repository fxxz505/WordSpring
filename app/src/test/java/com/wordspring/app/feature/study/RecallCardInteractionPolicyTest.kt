package com.wordspring.app.feature.study

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecallCardInteractionPolicyTest {

    @Test
    fun `revealed recall card places pronunciation with the moving card content`() {
        assertThat(recallCardPronunciationPlacement(CardState.ANSWER_REVEALED, ttsAvailable = true))
            .isEqualTo(RecallCardPronunciationPlacement.WITH_WORD)
    }

    @Test
    fun `front card and unavailable TTS do not add a second pronunciation control`() {
        assertThat(recallCardPronunciationPlacement(CardState.QUESTION, ttsAvailable = true)).isNull()
        assertThat(recallCardPronunciationPlacement(CardState.ANSWER_REVEALED, ttsAvailable = false)).isNull()
    }

    @Test
    fun `front and revealed recall cards share a stable word header anchor`() {
        assertThat(recallCardWordHeaderAnchor(CardState.QUESTION))
            .isEqualTo(RecallCardWordHeaderAnchor.STABLE_CENTER)
        assertThat(recallCardWordHeaderAnchor(CardState.ANSWER_REVEALED))
            .isEqualTo(RecallCardWordHeaderAnchor.STABLE_CENTER)
    }
}
