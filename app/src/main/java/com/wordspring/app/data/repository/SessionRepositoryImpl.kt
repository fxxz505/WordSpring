package com.wordspring.app.data.repository

import com.wordspring.app.data.Mappers.toDomain
import com.wordspring.app.data.Mappers.toEntity
import com.wordspring.app.data.db.StudySessionDao
import com.wordspring.app.domain.model.SessionCardRef
import com.wordspring.app.domain.model.SessionStatus
import com.wordspring.app.domain.model.StudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: StudySessionDao
) : SessionRepository {

    override suspend fun createSession(queue: List<SessionCardRef>, now: Long): StudySession {
        val session = StudySession(
            id = UUID.randomUUID().toString(),
            queueSnapshot = queue,
            currentIndex = 0,
            completedCount = 0,
            startedAt = now,
            endedAt = null,
            status = SessionStatus.ACTIVE
        )
        sessionDao.upsert(session.toEntity())
        return session
    }

    override suspend fun getSession(id: String): StudySession? = sessionDao.getById(id)?.toDomain()
    override fun observeSession(id: String): Flow<StudySession?> =
        sessionDao.observeById(id).map { it?.toDomain() }
    override suspend fun getActiveSession(): StudySession? = sessionDao.getActive()?.toDomain()

    override suspend fun advanceCursor(sessionId: String, now: Long) {
        val current = sessionDao.getById(sessionId) ?: return
        sessionDao.updateProgress(
            id = sessionId,
            index = current.currentIndex + 1,
            completed = current.completedCount + 1,
            status = "ACTIVE",
            endedAt = null
        )
    }

    override suspend fun completeSession(sessionId: String, now: Long) {
        sessionDao.updateProgress(
            id = sessionId,
            index = sessionDao.getById(sessionId)?.currentIndex ?: 0,
            completed = sessionDao.getById(sessionId)?.completedCount ?: 0,
            status = SessionStatus.COMPLETED.name,
            endedAt = now
        )
    }

    override suspend fun abandonSession(sessionId: String, now: Long) {
        sessionDao.updateProgress(
            id = sessionId,
            index = sessionDao.getById(sessionId)?.currentIndex ?: 0,
            completed = sessionDao.getById(sessionId)?.completedCount ?: 0,
            status = SessionStatus.ABANDONED.name,
            endedAt = now
        )
    }

    override suspend fun requeueCard(sessionId: String, ref: SessionCardRef) {
        val current = sessionDao.getById(sessionId)?.toDomain() ?: return
        if (current.status != SessionStatus.ACTIVE) return
        val newQueue = current.queueSnapshot + ref
        val updated = current.copy(queueSnapshot = newQueue)
        sessionDao.upsert(updated.toEntity())
    }
}
