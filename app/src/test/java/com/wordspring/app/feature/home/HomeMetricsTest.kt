package com.wordspring.app.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeMetricsTest {

    @Test
    fun `completed new words use today's summary instead of the session limit`() {
        val metrics = resolveHomeMetrics(
            dueCount = 0,
            availableNewCount = 10,
            completedNewToday = 20
        )

        assertThat(metrics.completedNewToday).isEqualTo(20)
    }

    @Test
    fun `session availability and estimate remain based on pending cards`() {
        val metrics = resolveHomeMetrics(
            dueCount = 3,
            availableNewCount = 7,
            completedNewToday = 20
        )

        assertThat(metrics.pendingCount).isEqualTo(10)
        assertThat(metrics.etaMinutes).isEqualTo(2)
        assertThat(metrics.startEnabled).isTrue()
    }

    @Test
    fun `empty pending queue is complete even when today's new total is nonzero`() {
        val metrics = resolveHomeMetrics(
            dueCount = 0,
            availableNewCount = 0,
            completedNewToday = 20
        )

        assertThat(metrics.allDone).isTrue()
        assertThat(metrics.startEnabled).isFalse()
    }

    @Test
    fun `new and review targets describe today's full required workload`() {
        val targets = resolveTodayPlanTargets(
            plannedNewCount = 50,
            newCompletedToday = 17,
            dueReviewCount = 2,
            reviewsCompletedToday = 13
        )

        assertThat(targets.newTarget).isEqualTo(50)
        assertThat(targets.reviewTarget).isEqualTo(15)
    }
}
