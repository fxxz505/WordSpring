package com.wordspring.app.feature.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.data.repository.SessionRepository
import com.wordspring.app.data.repository.MistakeCard
import com.wordspring.app.domain.model.StudySession
import com.wordspring.app.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MistakesUiState(
    val isLoading: Boolean = true,
    val mistakes: List<MistakeCard> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class MistakesViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val contentRepository: ContentRepository,
    private val startSessionUseCase: StartSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MistakesUiState())
    val state: StateFlow<MistakesUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val since = TimeUtil.startOfDaysAgoMillis(30)
            val books = contentRepository.getEnabledBooks()
            val bookIds = books.map { it.id }
            val mistakes = reviewRepository.getMistakeCards(since, bookIds)
            _state.value = MistakesUiState(isLoading = false, mistakes = mistakes)
        }
    }

    fun startRelearnSession(onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val session: StudySession? = startSessionUseCase(TimeUtil.nowMillis())
            if (session != null) onStarted(session.id)
        }
    }
}
