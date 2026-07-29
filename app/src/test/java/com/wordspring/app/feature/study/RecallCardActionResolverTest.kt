package com.wordspring.app.feature.study

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.tts.EnglishLanguage
import com.wordspring.app.domain.tts.TtsResult
import com.wordspring.app.domain.tts.selectEnglishLanguage
import org.junit.Test

class RecallCardActionResolverTest {

    @Test
    fun `a tap is recognized when movement stays inside touch slop`() {
        assertThat(isCardTap(7f, -5f, 12f)).isTrue()
    }

    @Test
    fun `a drag is not treated as a tap`() {
        assertThat(isCardTap(13f, 0f, 12f)).isFalse()
        assertThat(isCardTap(0f, -13f, 12f)).isFalse()
    }

    @Test
    fun `English speech falls back from US to an installed UK voice`() {
        assertThat(selectEnglishLanguage(TtsResult.NOT_SUPPORTED, TtsResult.AVAILABLE, TtsResult.AVAILABLE))
            .isEqualTo(EnglishLanguage.UK)
    }

    @Test
    fun `speech is unavailable only when every English fallback is unavailable`() {
        assertThat(selectEnglishLanguage(TtsResult.MISSING_DATA, TtsResult.NOT_SUPPORTED, TtsResult.NOT_SUPPORTED))
            .isNull()
    }
}

