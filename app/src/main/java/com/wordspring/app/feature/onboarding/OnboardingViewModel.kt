package com.wordspring.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.prefs.SettingsRepository
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.seed.StarterBookImporter
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.WordBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class OnboardingGoal { EXAM, DAILY, CUSTOM }
enum class OnboardingStep { GOAL, BOOK, DAILY }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.GOAL,
    val selectedGoal: OnboardingGoal = OnboardingGoal.DAILY,
    val books: List<WordBook> = emptyList(),
    /** Word count per book id, shown next to each option. */
    val wordCounts: Map<Long, Int> = emptyMap(),
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
                // Offer every built-in book, not only the one enabled by default.
                val books = contentRepository.observeBooks().first()
                    .filter { it.sourceType == BookSourceType.BUILT_IN }
                val counts = contentRepository.getBookLearningProgress()
                    .associate { it.bookId to it.totalCount }
                val preselected = books.firstOrNull { it.isEnabled }?.id ?: books.firstOrNull()?.id
                _state.value = _state.value.copy(
                    isLoading = false,
                    books = books,
                    wordCounts = counts,
                    selectedBookId = _state.value.selectedBookId ?: preselected
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
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
            current.selectedBookId?.let { chosen ->
                // Study exactly the chosen book: enable it and switch off the other built-in books.
                current.books.forEach { book ->
                    val shouldEnable = book.id == chosen
                    if (book.isEnabled != shouldEnable) contentRepository.setBookEnabled(book.id, shouldEnable)
                }
                contentRepository.setBookEnabled(chosen, true)
                contentRepository.setBookDailyLimit(chosen, limit)
            }
            // Mark onboarded last so Home never loads before the book choice is saved.
            settingsRepository.setOnboarded(true)
        }
    }
}
