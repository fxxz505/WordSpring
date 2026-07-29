package com.wordspring.app.feature.study

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.ui.nav.isImmersiveRoute
import org.junit.Test

class CardTransitionTest {

    @Test
    fun `new card starts centered after an outgoing swipe`() {
        assertThat(nextCardOffset(480f, -480f)).isEqualTo(CardOffset(0f, 0f))
    }

    @Test
    fun `a requeued final card gets a new visual instance key`() {
        assertThat(cardInstanceKey(cardId = 42L, queueIndex = 10) != cardInstanceKey(cardId = 42L, queueIndex = 9))
            .isTrue()
    }

    @Test
    fun `auto advance is only for a successful answer submission`() {
        assertThat(shouldAutoAdvance(answerSaved = true)).isTrue()
        assertThat(shouldAutoAdvance(answerSaved = false)).isFalse()
    }

    @Test
    fun `front face compensates for its 180 degree rotation`() {
        assertThat(cardFaceScaleX(180f)).isEqualTo(-1f)
        assertThat(cardFaceScaleX(0f)).isEqualTo(1f)
    }

    @Test
    fun `the two card faces use one fixed card size`() {
        assertThat(cardFaceHeightFraction()).isEqualTo(0.92f)
    }

    @Test
    fun `swipe hints are arranged left up right`() {
        assertThat(swipeHintDirections()).isEqualTo(listOf("left", "up", "right"))
    }

    @Test
    fun `a concrete study route hides the app bottom bar`() {
        assertThat(isImmersiveRoute("study/session-123")).isTrue()
        assertThat(isImmersiveRoute("home")).isFalse()
    }
}
