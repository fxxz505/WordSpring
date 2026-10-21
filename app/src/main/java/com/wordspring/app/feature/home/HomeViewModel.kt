package com.wordspring.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.data.repository.StatsRepository
import com.wordspring.app.data.seed.StarterBookImporter
import com.wordspring.app.domain.model.StudySession
import com.wordspring.app.domain.study.resolveDailyStudyPlan
import com.wordspring.app.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Ready(
        val dueCount: Int,
        val newCount: Int,
        val reviewedCount: Int,
        val newGoal: Int,
        val reviewGoal: Int,
        val recommendedNewGoal: Int,
        val pendingCount: Int,
        val etaMinutes: Int,
        val streakDays: Int,
        val totalWordCount: Int,
        val masteredWordCount: Int,
        val learningWordCount: Int,
        val unstartedWordCount: Int,
        val allDone: Boolean,
        val startEnabled: Boolean
    ) : HomeUiState
    data class Failed(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val reviewRepository: ReviewRepository,
    private val statsRepository: StatsRepository,
    private val starterBookImporter: StarterBookImporter,
    private val startSessionUseCase: StartSessionUseCase
) : ViewModel() {

    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    val state: StateFlow<HomeUiState> = _refreshTick
        .combine(flowOf(Unit)) { _, _ -> loadSnapshot() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.Loading)

    private suspend fun loadSnapshot(): HomeUiState {
        return try {
            starterBookImporter.ensureStarterBook()
            val books = contentRepository.getEnabledBooks()
            if (books.isEmpty()) return HomeUiState.Empty
            val bookIds = books.map { it.id }
            val progress = contentRepository.getBookLearningProgress()
                .filter { it.bookId in bookIds }
            val totalWordCount = progress.sumOf { it.totalCount }
            val masteredWordCount = progress.sumOf { it.masteredCount }
            val learningWordCount = progress.sumOf { it.learningCount }
            val unstartedWordCount = progress.sumOf { it.newCount }
            val now = TimeUtil.nowMillis()
            val newAvailable = reviewRepository.getNewCardCount(bookIds)
            val today = statsRepository.getToday()
            val completedNew = today?.newCount ?: 0
            val dayStart = TimeUtil.startOfTodayMillis()
            val completedReviews = reviewRepository.getCompletedScheduledReviewCount(bookIds, dayStart)
            val todayEpochDay = java.time.LocalDate.now().toEpochDay().toInt()
            val newPlanByBook = books.associate { book ->
                val remainingInBook = progress.firstOrNull { it.bookId == book.id }?.newCount ?: 0
                book.id to resolveDailyStudyPlan(
                    remainingNewWords = remainingInBook,
                    daysUntilTarget = book.targetEpochDay?.minus(todayEpochDay) ?: 1,
                    customNewLimit = book.dailyNewLimit.takeIf { book.targetEpochDay == null }
                )
            }
            val totalNewGoal = newPlanByBook.values.sumOf { it.newLimit }
            val newRemaining = newAvailable.coerceAtMost((totalNewGoal - completedNew).coerceAtLeast(0))
            val dueForToday = reviewRepository.getScheduledReviewCardCount(bookIds, now, dayStart)
            val targets = resolveTodayPlanTargets(
                plannedNewCount = totalNewGoal,
                newCompletedToday = completedNew,
                dueReviewCount = dueForToday,
                reviewsCompletedToday = completedReviews
            )
            val metrics = resolveHomeMetrics(
                dueCount = dueForToday,
                availableNewCount = newRemaining,
                completedNewToday = completedNew
            )
            val streak = statsRepository.getStreak(TimeUtil.todayLocalDate())
            HomeUiState.Ready(
                dueCount = dueForToday,
                newCount = metrics.completedNewToday,
                reviewedCount = completedReviews,
                newGoal = targets.newTarget,
                reviewGoal = targets.reviewTarget,
                recommendedNewGoal = totalNewGoal,
                pendingCount = metrics.pendingCount,
                etaMinutes = metrics.etaMinutes,
                streakDays = streak,
                totalWordCount = totalWordCount,
                masteredWordCount = masteredWordCount,
                learningWordCount = learningWordCount,
                unstartedWordCount = unstartedWordCount,
                allDone = metrics.allDone,
                startEnabled = metrics.startEnabled
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            HomeUiState.Failed("加载今日任务失败，请重试")
        }
    }

    fun refresh() { _refreshTick.value += 1 }

    fun startStudy(onStarted: (String) -> Unit, onNothing: () -> Unit) {
        viewModelScope.launch {
            val session: StudySession? = startSessionUseCase(TimeUtil.nowMillis())
            if (session == null) onNothing() else onStarted(session.id)
        }
    }
}
