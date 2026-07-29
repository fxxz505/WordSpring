package com.wordspring.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wordspring.app.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules / cancels the daily reminder work (M13).
 *
 * Spec: 提醒默认关闭；Android 13+ 仅当用户主动开启时申请 POST_NOTIFICATIONS；仅在有待复习任务时通知；
 * 每自然日最多一次；连续 7 天未打开则停止，直到用户再次学习或主动开启。
 *
 * The 7-day inactivity rule: if the user has not opened the app in 7+ days, we cancel the
 * recurring work. It will be re-scheduled when the user opens the app (see AppOpenObserver).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    fun schedule(hour: Int, minute: Int) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var next = now.withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val initialDelayMinutes = maxOf(1L, java.time.Duration.between(now, next).toMinutes())

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(ReminderWorker.UNIQUE_WORK_NAME)
    }

    /**
     * Called on app open. Records the open day and, if reminders are enabled but the recurring
     * work has been stopped (e.g. by the 7-day rule), re-schedules it.
     */
    suspend fun onAppOpen() {
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay().toInt()
        settingsRepository.recordOpenEpochDay(today)

        val enabled = settingsRepository.reminderEnabled.first()
        if (enabled) {
            // Re-arm the daily window so the 7-day rule doesn't strand the user.
            val h = settingsRepository.reminderHour.first()
            val m = settingsRepository.reminderMinute.first()
            schedule(h, m)
        }
    }
}
