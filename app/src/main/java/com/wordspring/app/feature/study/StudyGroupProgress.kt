package com.wordspring.app.feature.study

import com.wordspring.app.domain.model.SessionCardRef
import com.wordspring.app.domain.session.SessionPlanner

/** Progress is based on the group's distinct words, not retry appearances. */
data class StudyGroupProgress(val current: Int, val total: Int)

internal fun studyGroupTotal(queue: List<SessionCardRef>): Int =
    queue.asSequence()
        .map { it.wordId }
        .distinct()
        .take(SessionPlanner.DEFAULT_SESSION_LIMIT)
        .count()

internal fun studyGroupProgress(completedCount: Int, queue: List<SessionCardRef>): StudyGroupProgress {
    val total = studyGroupTotal(queue)
    val current = if (total == 0) 0 else (completedCount + 1).coerceIn(1, total)
    return StudyGroupProgress(current, total)
}
