package com.wordspring.app.data.repository

import com.wordspring.app.domain.model.SessionCardRef
import com.wordspring.app.domain.model.StudySession
import kotlinx.coroutines.flow.Flow

/**
 * M04 session orchestration. Planner runs pure; this persists the session.
 */
interface SessionRepository {
    suspend fun createSession(queue: List<SessionCardRef>, now: Long): StudySession
    suspend fun getSession(id: String): StudySession?
    fun observeSession(id: String): Flow<StudySession?>
    suspend fun getActiveSession(): StudySession?
    suspend fun advanceCursor(sessionId: String, now: Long)
    suspend fun completeSession(sessionId: String, now: Long)
    suspend fun abandonSession(sessionId: String, now: Long)

    /**
     * Append [ref] to the end of the session's queueSnapshot so the card will
     * reappear later in the same session. Used by the end-of-round requeue
     * when the user has answered AGAIN/HARD — wrong cards are appended as a
     * new round that must be answered GOOD/EASY to complete the session.
     */
    suspend fun requeueCard(sessionId: String, ref: SessionCardRef)
}
