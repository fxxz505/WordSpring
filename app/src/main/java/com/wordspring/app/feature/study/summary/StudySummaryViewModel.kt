package com.wordspring.app.feature.study.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.data.repository.SessionRepository
import com.wordspring.app.data.repository.StatsRepository
import com.wordspring.app.domain.model.StudySession
import com.wordspring.app.feature.study.studyGroupProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUiState(
    val isLoading: Boolean = true,
    val session: StudySession? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val correctRate: Int = 0,
    val durationSeconds: Long = 0,
    val remainingDue: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class StudySummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val statsRepository: StatsRepository,
    private val contentRepository: ContentRepository,
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _state = MutableStateFlow(SummaryUiState())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val session = sessionRepository.getSession(sessionId)
            if (session == null) {
                _state.value = SummaryUiState(isLoading = false, errorMessage = "找不到这次学习会话")
                return@launch
            }
            val today = statsRepository.getToday()
            val progress = studyGroupProgress(session.completedCount, session.queueSnapshot)
            val correctRate = if (today != null && today.reviewedCount > 0) {
                ((today.correctCount.toDouble() / today.reviewedCount) * 100).toInt()
            } else 0
            val duration = (session.endedAt ?: TimeUtil.nowMillis()) - session.startedAt
            val bookIds = contentRepository.getEnabledBooks().map { it.id }
            val remaining = reviewRepository.getDueCardCount(bookIds, TimeUtil.nowMillis())
            _state.value = SummaryUiState(
                isLoading = false,
                session = session,
                completedCount = progress.current,
                totalCount = progress.total,
                correctRate = correctRate,
                durationSeconds = (duration / 1000).coerceAtLeast(0),
                remainingDue = remaining
            )
        }
    }
}
