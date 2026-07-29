package com.wordspring.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Time helpers. Spec rule: "保存 UTC 时间戳；以用户当前设备时区划分每日目标."
 */
object TimeUtil {

    /** epoch millis for "now". Caller passes this in for testability. */
    fun nowMillis(): Long = System.currentTimeMillis()

    /**
     * Returns the yyyy-MM-dd string for the given epoch millis in the device's default timezone.
     * Used as the daily_summaries PK.
     */
    fun localDateString(epochMillis: Long, timezone: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        sdf.timeZone = timezone
        return sdf.format(Date(epochMillis))
    }

    fun todayLocalDate(timezone: TimeZone = TimeZone.getDefault()): String =
        localDateString(nowMillis(), timezone)

    /** Start of the local day in epoch millis (UTC). */
    fun startOfTodayMillis(timezone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(timezone)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Days ago start-of-day in epoch millis. */
    fun startOfDaysAgoMillis(daysAgo: Int, timezone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(timezone)
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Compute the current streak (consecutive days with reviewedCount > 0) ending today or yesterday.
     * If today has no reviews yet but yesterday does, the streak still counts yesterday
     * (we'll still display the "ongoing" count and update it once they review today).
     */
    fun computeStreak(activeDatesDesc: List<String>, today: String): Int {
        if (activeDatesDesc.isEmpty()) return 0
        val set = activeDatesDesc.toSet()
        if (set.isEmpty()) return 0
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val cal = Calendar.getInstance()
        cal.time = (runCatching { fmt.parse(today) }.getOrNull()) ?: return 0
        if (!set.contains(today)) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var streak = 0
        while (set.contains(fmt.format(cal.time))) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }
}
