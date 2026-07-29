package com.wordspring.app.domain.usecase

import com.wordspring.app.data.repository.AnswerCommand
import com.wordspring.app.data.repository.AnswerResult
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.QuestionType
import java.util.UUID
import javax.inject.Inject

/**
 * M05 → M07 boundary. The card UI calls only this. It never touches DAOs.
 */
class SubmitAnswerUseCase @Inject constructor(
    private val reviewRepository: ReviewRepository
) {
    suspend operator fun invoke(
        cardId: Long,
        bookEntryId: Long,
        grade: Grade,
        questionType: QuestionType,
        durationMs: Long,
        sessionId: String,
        now: Long,
        isRelearnRequest: Boolean = false
    ): AnswerResult {
        val command = AnswerCommand(
            answerId = UUID.randomUUID().toString(),
            cardId = cardId,
            bookEntryId = bookEntryId,
            grade = grade,
            questionType = questionType,
            now = now,
            durationMs = durationMs,
            sessionId = sessionId,
            isRelearnRequest = isRelearnRequest
        )
        return reviewRepository.submitAnswer(command)
    }
}
