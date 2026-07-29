package com.wordspring.app.data

import com.google.common.truth.Truth.assertThat
import java.util.TimeZone
import org.junit.Test

/**
 * Pure unit tests for TimeUtil helpers (cross-timezone date bucketing, streak).
 */
class TimeUtilTest {

    @Test
    fun localDateString_usesDeviceTimezone() {
        val tz = TimeZone.getTimeZone("UTC")
        // 2023-01-01 00:00:00 UTC = 1672531200000L
        val s = TimeUtil.localDateString(1672531200000L, tz)
        assertThat(s).isEqualTo("2023-01-01")
    }

    @Test
    fun localDateString_acrossTimezones_bucketsCorrectly() {
        // 2023-01-01 00:30:00 UTC
        val ts = 1672533000000L
        // In UTC this is still Jan 1
        assertThat(TimeUtil.localDateString(ts, TimeZone.getTimeZone("UTC"))).isEqualTo("2023-01-01")
        // In UTC+8 this is Jan 1 08:30
        assertThat(TimeUtil.localDateString(ts, TimeZone.getTimeZone("GMT+8"))).isEqualTo("2023-01-01")
        // In UTC-8 this is Dec 31 16:30
        assertThat(TimeUtil.localDateString(ts, TimeZone.getTimeZone("GMT-8"))).isEqualTo("2022-12-31")
    }

    @Test
    fun computeStreak_emptyReturnsZero() {
        assertThat(TimeUtil.computeStreak(emptyList(), "2024-01-01")).isEqualTo(0)
    }

    @Test
    fun computeStreak_consecutiveDays_returnsCount() {
        val dates = listOf("2024-01-03", "2024-01-02", "2024-01-01")
        assertThat(TimeUtil.computeStreak(dates, "2024-01-03")).isEqualTo(3)
    }

    @Test
    fun computeStreak_gapResetsCount() {
        val dates = listOf("2024-01-03", "2024-01-02", "2023-12-31")
        assertThat(TimeUtil.computeStreak(dates, "2024-01-03")).isEqualTo(2)
    }

    @Test
    fun computeStreak_todayMissingButYesterdayPresent_countsFromYesterday() {
        val dates = listOf("2024-01-03", "2024-01-02")
        // Today is Jan 4, no entry yet — streak should still count yesterday's run.
        assertThat(TimeUtil.computeStreak(dates, "2024-01-04")).isEqualTo(2)
    }

    @Test
    fun computeStreak_singleDayToday_returnsOne() {
        val dates = listOf("2024-01-01")
        assertThat(TimeUtil.computeStreak(dates, "2024-01-01")).isEqualTo(1)
    }
}
