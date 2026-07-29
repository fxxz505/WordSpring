package com.wordspring.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.prefs.SettingsRepository
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.seed.StarterBookImporter
import com.wordspring.app.domain.model.WordBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingGoal { EXAM, DAILY, CUSTOM }
enum class OnboardingStep { GOAL, BOOK, DAILY }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.GOAL,
    val selectedGoal: OnboardingGoal = OnboardingGoal.DAILY,
    val books: List<WordBook> = emptyList(),
    val selectedBookId: Long? = null,
    val dailyNewLimit: Int = 10,
    val customDailyInput: String = "10",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val contentRepository: ContentRepository,
    private val starterBookImporter: StarterBookImporter
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                starterBookImporter.ensureStarterBook()
                val books = contentRepository.getEnabledBooks()
                val firstBookId = books.firstOrNull()?.id
                _state.value = _state.value.copy(
                    isLoading = false,
                    books = books,
                    selectedBookId = firstBookId
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "初始化失败，请重试")
            }
        }
    }

    fun selectGoal(goal: OnboardingGoal) {
        _state.value = _state.value.copy(selectedGoal = goal)
    }

    fun selectBook(bookId: Long) {
        _state.value = _state.value.copy(selectedBookId = bookId)
    }

    fun selectDailyPreset(limit: Int) {
        _state.value = _state.value.copy(dailyNewLimit = limit, customDailyInput = limit.toString())
    }

    fun updateCustomDaily(input: String) {
        _state.value = _state.value.copy(customDailyInput = input.filter { it.isDigit() })
    }

    fun next() {
        val current = _state.value
        _state.value = current.copy(step = when (current.step) {
            OnboardingStep.GOAL -> OnboardingStep.BOOK
            OnboardingStep.BOOK -> OnboardingStep.DAILY
            OnboardingStep.DAILY -> {
                finish(current)
                return
            }
        })
    }

    fun back() {
        val current = _state.value
        _state.value = current.copy(step = when (current.step) {
            OnboardingStep.DAILY -> OnboardingStep.BOOK
            OnboardingStep.BOOK -> OnboardingStep.GOAL
            OnboardingStep.GOAL -> return
        })
    }

    fun skip() {
        finish(_state.value)
    }

    private fun finish(current: OnboardingUiState) {
        viewModelScope.launch {
            val limit = current.customDailyInput.toIntOrNull()?.coerceIn(1, 100) ?: 10
            settingsRepository.setDailyNewLimit(limit)
            settingsRepository.setOnboarded(true)
            // Mark the chosen book enabled (default starter already enabled by importer)
            current.selectedBookId?.let { contentRepository.setBookEnabled(it, true) }
        }
    }
}
