package com.wordspring.app.domain.study

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DailyStudyPlanTest {

    @Test
    fun `target date recommendation rounds up the remaining new words`() {
        val plan = resolveDailyStudyPlan(
            remainingNewWords = 101,
            daysUntilTarget = 10,
            customNewLimit = null
        )

        assertThat(plan.recommendedNewLimit).isEqualTo(11)
        assertThat(plan.newLimit).isEqualTo(11)
    }

    @Test
    fun `custom new goal overrides the target date recommendation`() {
        val plan = resolveDailyStudyPlan(
            remainingNewWords = 101,
            daysUntilTarget = 10,
            customNewLimit = 20
        )

        assertThat(plan.newLimit).isEqualTo(20)
    }

    @Test
    fun `today limit subtracts completed new work`() {
        val plan = resolveDailyStudyPlan(
            remainingNewWords = 80,
            daysUntilTarget = 8,
            customNewLimit = 10
        )

        assertThat(plan.remainingNewAllowance(completedNewToday = 7)).isEqualTo(3)
    }

    @Test
    fun `past target uses one day and does not reduce the recommendation`() {
        val plan = resolveDailyStudyPlan(
            remainingNewWords = 201,
            daysUntilTarget = -3,
            customNewLimit = null
        )

        assertThat(plan.recommendedNewLimit).isEqualTo(201)
    }

    @Test
    fun `due reviews are always available and are not capped by a daily limit`() {
        val plan = resolveDailyStudyPlan(
            remainingNewWords = 100,
            daysUntilTarget = 10,
            customNewLimit = null
        )

        assertThat(plan.reviewLimit).isEqualTo(Int.MAX_VALUE)
    }
}
