package com.wordspring.app.worker

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wordspring.app.R
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.prefs.SettingsRepository
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.ui.MainActivity
import com.wordspring.app.WordSpringApp
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * M13 — daily reminder worker.
 *
 * Spec rules:
 *  - 默认关闭；Android 13+ 仅当用户主动开启时申请 POST_NOTIFICATIONS
 *  - 仅在有待复习任务时通知
 *  - 每自然日最多一次
 *  - 连续 7 天未打开则停止，直到用户再次学习或主动开启
 *
 * Posts at most one low-importance notification per local day, only when there are due
 * or new cards waiting across enabled books. The 7-day inactivity rule is enforced by
 * ReminderScheduler (it cancels recurring work after 7 days without app opens).
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val reviewRepository: ReviewRepository,
    private val contentRepository: ContentRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Skip if user has reminders turned off (could happen if they toggled after scheduling).
        if (!settingsRepository.reminderEnabled.first()) return Result.success()

        val now = TimeUtil.nowMillis()
        val today = TimeUtil.localDateString(now)
        val lastNotifiedDay = settingsRepository.lastReviewDay.first()
        if (lastNotifiedDay == today) return Result.success() // already notified today

        // Count due cards across enabled books.
        val enabledBooks = contentRepository.getEnabledBooks()
        if (enabledBooks.isEmpty()) return Result.success()
        val bookIds = enabledBooks.map { it.id }
        val dueCount = reviewRepository.getDueCardCount(bookIds, now) +
            reviewRepository.getNewCardCount(bookIds)
        if (dueCount <= 0) return Result.success()

        // Check permission on Android 13+.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val etaMinutes = ((dueCount * 30L + 59L) / 60L).coerceAtLeast(1L).toInt()
        val text = applicationContext.getString(
            R.string.notification_text_format, dueCount, etaMinutes
        )

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(
            applicationContext, WordSpringApp.REMINDER_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            settingsRepository.recordReviewDay(today)
        } catch (_: SecurityException) {
            // Permission revoked between check and notify; swallow.
        }
        return Result.success()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val UNIQUE_WORK_NAME = "wordspring_daily_reminder"
    }
}
