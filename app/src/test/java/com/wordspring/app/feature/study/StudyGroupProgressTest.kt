package com.wordspring.app.feature.study

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.domain.model.SessionCardRef
import org.junit.Test

class StudyGroupProgressTest {

    @Test
    fun `a repeated wrong card does not increase the ten word group total`() {
        val originalGroup = (1L..10L).map { card(it) }
        val queueWithRetries = originalGroup + card(2) + card(5) + card(5)

        assertThat(studyGroupTotal(queueWithRetries)).isEqualTo(10)
    }

    @Test
    fun `progress stays within the fixed group total when retries are appended`() {
        val originalGroup = (1L..10L).map { card(it) }
        val queueWithRetries = originalGroup + card(2) + card(5) + card(5)

        assertThat(studyGroupProgress(completedCount = 8, queue = queueWithRetries))
            .isEqualTo(StudyGroupProgress(current = 9, total = 10))
    }

    private fun card(id: Long) = SessionCardRef(
        cardId = id,
        bookEntryId = id,
        wordId = id,
        questionType = QuestionType.RECALL
    )
}
