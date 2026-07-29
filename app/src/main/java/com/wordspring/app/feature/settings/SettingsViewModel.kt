package com.wordspring.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.BuildConfig
import com.wordspring.app.data.imp.JsonBackup
import com.wordspring.app.data.prefs.SettingsRepository
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.ui.theme.ThemeMode
import com.wordspring.app.worker.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val onboarded: Boolean = false,
    val dailyNewLimit: Int = 0,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean = false,
    val haptic: Boolean = true,
    val ttsSpeed: Float = 1.0f,
    val advancedGrading: Boolean = false,
    val showAnswerOnWrong: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val jsonBackup: JsonBackup,
    private val contentRepository: ContentRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = SettingsUiState(
                onboarded = settingsRepository.onboarded.first(),
                dailyNewLimit = settingsRepository.dailyNewLimit.first(),
                themeMode = settingsRepository.themeMode.first(),
                dynamicColor = settingsRepository.dynamicColor.first(),
                reduceMotion = settingsRepository.reduceMotion.first(),
                haptic = settingsRepository.haptic.first(),
                ttsSpeed = settingsRepository.ttsSpeed.first(),
                advancedGrading = settingsRepository.advancedGrading.first(),
                showAnswerOnWrong = settingsRepository.showAnswerOnWrong.first(),
                reminderEnabled = settingsRepository.reminderEnabled.first(),
                reminderHour = settingsRepository.reminderHour.first(),
                reminderMinute = settingsRepository.reminderMinute.first()
            )
            _state.value = s
            // Subscribe to changes
            launch { settingsRepository.themeMode.collect { _state.value = _state.value.copy(themeMode = it) } }
            launch { settingsRepository.dynamicColor.collect { _state.value = _state.value.copy(dynamicColor = it) } }
            launch { settingsRepository.reduceMotion.collect { _state.value = _state.value.copy(reduceMotion = it) } }
            launch { settingsRepository.haptic.collect { _state.value = _state.value.copy(haptic = it) } }
            launch { settingsRepository.ttsSpeed.collect { _state.value = _state.value.copy(ttsSpeed = it) } }
            launch { settingsRepository.advancedGrading.collect { _state.value = _state.value.copy(advancedGrading = it) } }
            launch { settingsRepository.showAnswerOnWrong.collect { _state.value = _state.value.copy(showAnswerOnWrong = it) } }
            launch { settingsRepository.reminderEnabled.collect { _state.value = _state.value.copy(reminderEnabled = it) } }
            launch { settingsRepository.reminderHour.collect { _state.value = _state.value.copy(reminderHour = it) } }
            launch { settingsRepository.dailyNewLimit.collect { _state.value = _state.value.copy(dailyNewLimit = it) } }
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(v) }
    fun setReduceMotion(v: Boolean) = viewModelScope.launch { settingsRepository.setReduceMotion(v) }
    fun setHaptic(v: Boolean) = viewModelScope.launch { settingsRepository.setHaptic(v) }
    fun setTtsSpeed(v: Float) = viewModelScope.launch { settingsRepository.setTtsSpeed(v) }
    fun setAdvancedGrading(v: Boolean) = viewModelScope.launch { settingsRepository.setAdvancedGrading(v) }
    fun setShowAnswerOnWrong(v: Boolean) = viewModelScope.launch { settingsRepository.setShowAnswerOnWrong(v) }
    fun setReminderEnabled(v: Boolean) = viewModelScope.launch {
        settingsRepository.setReminderEnabled(v)
        if (v) {
            val h = settingsRepository.reminderHour.first()
            val m = settingsRepository.reminderMinute.first()
            reminderScheduler.schedule(h, m)
        } else {
            reminderScheduler.cancel()
        }
    }
    fun setReminderTime(h: Int, m: Int) = viewModelScope.launch {
        settingsRepository.setReminderTime(h, m)
        if (settingsRepository.reminderEnabled.first()) {
            reminderScheduler.schedule(h, m)
        }
    }
    fun setDailyNewLimit(v: Int) = viewModelScope.launch { settingsRepository.setDailyNewLimit(v) }

    /** Export the current data as a versioned JSON string. */
    suspend fun exportBackup(): String = jsonBackup.export(BuildConfig.VERSION_NAME)

    /** Validate a backup file's content. Returns null on success or an error message. */
    fun validateBackup(content: String): String? = jsonBackup.validate(content)

    /** Restore from validated backup content. Caller must confirm first. */
    suspend fun restoreBackup(content: String): Int = jsonBackup.restore(content).getOrThrow()

    /**
     * Clear all study data: review events, cards, daily summaries, sessions, import jobs,
     * custom books and words NOT belonging to the starter book.
     * The starter book is preserved (per spec) but its review cards are reset.
     */
    suspend fun clearAllData() {
        contentRepository.clearAllUserData()
    }
}
