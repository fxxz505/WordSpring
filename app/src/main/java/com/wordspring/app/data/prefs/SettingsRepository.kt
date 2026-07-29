package com.wordspring.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wordspring.app.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wordspring_prefs")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val DAILY_NEW_LIMIT = intPreferencesKey("daily_new_limit")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTIC = booleanPreferencesKey("haptic")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val ADVANCED_GRADING = booleanPreferencesKey("advanced_grading")
        val SHOW_ANSWER_ON_WRONG = booleanPreferencesKey("show_answer_on_wrong")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val LAST_REVIEW_DAY = stringPreferencesKey("last_review_day")
        val LAST_OPENED_AT = intPreferencesKey("last_opened_at") // epoch days
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }
    // Zero means that the daily new-word quota is calculated from the target date.
    val dailyNewLimit: Flow<Int> = context.dataStore.data.map { it[Keys.DAILY_NEW_LIMIT] ?: 0 }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME_MODE] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)
    }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    val reduceMotion: Flow<Boolean> = context.dataStore.data.map { it[Keys.REDUCE_MOTION] ?: false }
    val haptic: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTIC] ?: true }
    val ttsSpeed: Flow<Float> = context.dataStore.data.map { it[Keys.TTS_SPEED] ?: 1.0f }
    val advancedGrading: Flow<Boolean> = context.dataStore.data.map { it[Keys.ADVANCED_GRADING] ?: false }
    val showAnswerOnWrong: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_ANSWER_ON_WRONG] ?: true }
    val reminderEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.REMINDER_ENABLED] ?: false }
    val reminderHour: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_HOUR] ?: 20 }
    val reminderMinute: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_MINUTE] ?: 0 }
    val lastReviewDay: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_REVIEW_DAY] }
    val lastOpenedAt: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_OPENED_AT] ?: 0 }

    suspend fun setOnboarded(value: Boolean) { context.dataStore.edit { it[Keys.ONBOARDED] = value } }
    suspend fun setDailyNewLimit(value: Int) {
        context.dataStore.edit { it[Keys.DAILY_NEW_LIMIT] = value.coerceIn(0, 200) }
    }
    suspend fun setThemeMode(mode: ThemeMode) { context.dataStore.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun setDynamicColor(value: Boolean) { context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = value } }
    suspend fun setReduceMotion(value: Boolean) { context.dataStore.edit { it[Keys.REDUCE_MOTION] = value } }
    suspend fun setHaptic(value: Boolean) { context.dataStore.edit { it[Keys.HAPTIC] = value } }
    suspend fun setTtsSpeed(value: Float) {
        context.dataStore.edit { it[Keys.TTS_SPEED] = value.coerceIn(0.5f, 2.0f) }
    }
    suspend fun setAdvancedGrading(value: Boolean) { context.dataStore.edit { it[Keys.ADVANCED_GRADING] = value } }
    suspend fun setShowAnswerOnWrong(value: Boolean) { context.dataStore.edit { it[Keys.SHOW_ANSWER_ON_WRONG] = value } }
    suspend fun setReminderEnabled(value: Boolean) { context.dataStore.edit { it[Keys.REMINDER_ENABLED] = value } }
    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun recordReviewDay(localDate: String) {
        context.dataStore.edit { it[Keys.LAST_REVIEW_DAY] = localDate }
    }
    suspend fun recordOpenEpochDay(epochDay: Int) {
        context.dataStore.edit { it[Keys.LAST_OPENED_AT] = epochDay }
    }
}
