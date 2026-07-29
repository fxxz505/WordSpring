package com.wordspring.app.feature.study

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.Grade
import org.junit.Test

class SwipeGradeResolverTest {

    @Test
    fun `each supported direction has a distinct high contrast visual tone`() {
        assertThat(swipeVisualTone(120f, 12f)).isEqualTo(SwipeVisualTone.AGAIN_RED)
        assertThat(swipeVisualTone(8f, -132f)).isEqualTo(SwipeVisualTone.HARD_ORANGE)
        assertThat(swipeVisualTone(-124f, 16f)).isEqualTo(SwipeVisualTone.GOOD_GREEN)
    }

    @Test
    fun `right swipe maps to again`() {
        assertThat(resolveSwipeGrade(120f, 12f, 96f)).isEqualTo(Grade.AGAIN)
    }

    @Test
    fun `up swipe maps to hard`() {
        assertThat(resolveSwipeGrade(8f, -132f, 96f)).isEqualTo(Grade.HARD)
    }

    @Test
    fun `left swipe maps to good`() {
        assertThat(resolveSwipeGrade(-124f, 16f, 96f)).isEqualTo(Grade.GOOD)
    }

    @Test
    fun `short or downward gestures are ignored`() {
        assertThat(resolveSwipeGrade(52f, 12f, 96f)).isNull()
        assertThat(resolveSwipeGrade(4f, 124f, 96f)).isNull()
    }

    @Test
    fun `gesture at the threshold is accepted`() {
        assertThat(resolveSwipeGrade(-96f, 0f, 96f)).isEqualTo(Grade.GOOD)
    }

    @Test
    fun `dominant axis determines the intended grade`() {
        assertThat(resolveSwipeGrade(130f, -95f, 96f)).isEqualTo(Grade.AGAIN)
        assertThat(resolveSwipeGrade(-80f, -140f, 96f)).isEqualTo(Grade.HARD)
    }
}
