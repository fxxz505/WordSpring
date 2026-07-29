package com.wordspring.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookLearningProgressTest {

    @Test
    fun `progress separates mastered learning and unstarted words`() {
        val progress = BookLearningProgress(
            bookId = 1L,
            totalCount = 4_500,
            masteredCount = 900,
            learningCount = 600,
            newCount = 3_000
        )

        assertThat(progress.completedCount).isEqualTo(1_500)
        assertThat(progress.completionFraction).isWithin(0.0001f).of(1f / 3f)
    }

    @Test
    fun `progress fraction remains safe for an empty book`() {
        val progress = BookLearningProgress(1L, 0, 0, 0, 0)

        assertThat(progress.completionFraction).isEqualTo(0f)
    }
}
