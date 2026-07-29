package com.wordspring.app.domain.session

import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.domain.model.SessionCardRef
import com.wordspring.app.domain.model.Stage
import kotlin.random.Random

/**
 * Pure session planner (M04). No Android, no DB, no clock. Inputs come from repositories.
 *
 * Rules per spec:
 *  - 到期复习优先
 *  - 取不超过单次上限（固定 10 张）的到期卡
 *  - 队列未满时按每部已启用词书剩余每日新词额度加入 NEW 卡
 *  - 混排题型但不连续超过两张相同题型
 *  - 同一张卡单次会话最多出现两次
 *  - AGAIN 未到短间隔时不可立刻重复
 */
object SessionPlanner {

    /** One study group always starts with at most ten distinct words. */
    const val DEFAULT_SESSION_LIMIT = 10

    data class CardCandidate(
        val cardId: Long,
        val bookEntryId: Long,
        val bookId: Long,
        val wordId: Long,
        val stage: Stage,
        val dueAt: Long,
        val dailyNewRemainingForBook: Int
    )

    /**
     * @param dueCards due REVIEW/LEARNING cards (already filtered to stage != SUSPENDED, dueAt <= now)
     * @param newCards NEW cards available, in book priority order. Each carries the remaining daily-new
     *                 quota for its own book so we don't over-pull.
     * @param sessionLimit max distinct cards in this study group (default 10)
     * @param random deterministic seedable random for shuffling choice questions and interleaving
     */
    fun plan(
        dueCards: List<CardCandidate>,
        newCards: List<CardCandidate>,
        sessionLimit: Int = DEFAULT_SESSION_LIMIT,
        random: Random = Random.Default
    ): List<SessionCardRef> {
        if (sessionLimit <= 0) return emptyList()

        // 1. Due first.
        val due = dueCards.take(sessionLimit).map { it.toRef(random) }
        val remaining = sessionLimit - due.size
        if (remaining <= 0) return interleave(due)

        // 2. NEW cards, gated per-book by dailyNewRemainingForBook.
        val perBookQuota = mutableMapOf<Long, Int>()
        val newPicked = ArrayList<SessionCardRef>(remaining)
        for (c in newCards) {
            if (newPicked.size >= remaining) break
            val remainingForBook = perBookQuota[c.bookId] ?: c.dailyNewRemainingForBook
            if (remainingForBook <= 0) continue
            perBookQuota[c.bookId] = remainingForBook - 1
            newPicked += c.toRef(random)
        }

        val combined = due + newPicked
        return interleave(combined)
    }

    /**
     * Reorder so the same QuestionType doesn't appear more than twice in a row.
     */
    fun interleave(refs: List<SessionCardRef>): List<SessionCardRef> {
        if (refs.size <= 2) return refs
        // Greedy: walk through, picking the next card whose type differs from the last when possible.
        val result = ArrayList<SessionCardRef>(refs.size)
        val pool = ArrayDeque(refs)
        var lastType: QuestionType? = null
        var sameStreak = 0
        while (pool.isNotEmpty()) {
            val next = pool.firstOrNull { it.questionType != lastType || sameStreak < 2 }
                ?: pool.first()
            pool.remove(next)
            if (next.questionType == lastType) sameStreak++ else sameStreak = 1
            lastType = next.questionType
            result += next
        }
        return result
    }

    private fun CardCandidate.toRef(random: Random): SessionCardRef {
        // Choose question type. For NEW cards we favour RECALL first, then SPELLING, then CHOICE.
        // For REVIEW cards we rotate so the same card isn't always the same type.
        val type = when (stage) {
            Stage.NEW -> QuestionType.RECALL
            Stage.LEARNING -> QuestionType.RECALL
            Stage.REVIEW -> chooseReviewType(random)
            Stage.SUSPENDED -> QuestionType.RECALL // shouldn't reach here
        }
        return SessionCardRef(
            cardId = cardId,
            bookEntryId = bookEntryId,
            wordId = wordId,
            questionType = type
        )
    }

    private fun chooseReviewType(random: Random): QuestionType {
        // Distribute roughly: 50% RECALL, 25% SPELLING, 25% CHOICE.
        return when (random.nextInt(100)) {
            in 0..49 -> QuestionType.RECALL
            in 50..74 -> QuestionType.SPELLING
            else -> QuestionType.CHOICE
        }
    }
}
