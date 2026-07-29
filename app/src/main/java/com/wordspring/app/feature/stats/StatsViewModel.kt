package com.wordspring.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.repository.StatsRepository
import com.wordspring.app.data.repository.StatsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val snapshot: StatsSnapshot? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val today = statsRepository.getToday()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val start7 = fmt.format(cal.time)
            val endToday = TimeUtil.todayLocalDate()
            val last7 = statsRepository.getRange(start7, endToday)
            val cal30 = Calendar.getInstance()
            cal30.add(Calendar.DAY_OF_YEAR, -30)
            val start30 = fmt.format(cal30.time)
            val last30 = statsRepository.getRange(start30, endToday)
            val streak = statsRepository.getStreak(endToday)
            val reviewed7 = last7.sumOf { it.reviewedCount }
            val correct7 = last7.sumOf { it.correctCount }
            val new7 = last7.sumOf { it.newCount }
            val reviewed30 = last30.sumOf { it.reviewedCount }
            val correct30 = last30.sumOf { it.correctCount }
            val new30 = last30.sumOf { it.newCount }
            _state.value = StatsUiState(
                isLoading = false,
                snapshot = StatsSnapshot(
                    today = today,
                    last7 = last7,
                    last30 = last30,
                    streakDays = streak,
                    reviewed7 = reviewed7,
                    correct7 = correct7,
                    new7 = new7,
                    reviewed30 = reviewed30,
                    correct30 = correct30,
                    new30 = new30
                )
            )
        }
    }
}
