package com.wordspring.app.feature.study

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.Grade
import org.junit.Test

class SwipeFlingTest {
    private val threshold = 300f
    private val fling = 1000f

    @Test
    fun `full swipe resolves without velocity`() {
        assertThat(resolveSwipeGradeWithFling(-320f, 0f, 0f, 0f, threshold, fling)).isEqualTo(Grade.GOOD)
    }

    @Test
    fun `quick flick after short travel counts as a swipe`() {
        assertThat(resolveSwipeGradeWithFling(120f, 10f, 2000f, 0f, threshold, fling)).isEqualTo(Grade.AGAIN)
        assertThat(resolveSwipeGradeWithFling(-120f, 0f, -2000f, 100f, threshold, fling)).isEqualTo(Grade.GOOD)
        assertThat(resolveSwipeGradeWithFling(0f, -110f, 0f, -1800f, threshold, fling)).isEqualTo(Grade.HARD)
    }

    @Test
    fun `slow release or tiny travel snaps back`() {
        assertThat(resolveSwipeGradeWithFling(120f, 0f, 400f, 0f, threshold, fling)).isNull()
        assertThat(resolveSwipeGradeWithFling(30f, 0f, 3000f, 0f, threshold, fling)).isNull()
        // Flick direction opposite to travel is ignored.
        assertThat(resolveSwipeGradeWithFling(-150f, 0f, 3000f, 0f, threshold, fling)).isNull()
    }
}
