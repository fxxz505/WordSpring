package com.wordspring.app.feature.books.bookdetail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompletionPlanPreviewTest {

    @Test
    fun `preview rounds up the daily new-word amount for the selected completion date`() {
        val preview = completionPlanPreview(
            remainingNewWords = 4_500,
            todayEpochDay = 100,
            targetEpochDay = 130
        )

        assertThat(preview.daysRemaining).isEqualTo(30)
        assertThat(preview.dailyNewWords).isEqualTo(150)
    }

    @Test
    fun `preview keeps an overdue completion date actionable`() {
        val preview = completionPlanPreview(remainingNewWords = 12, todayEpochDay = 100, targetEpochDay = 99)

        assertThat(preview.daysRemaining).isEqualTo(1)
        assertThat(preview.dailyNewWords).isEqualTo(12)
    }
}
