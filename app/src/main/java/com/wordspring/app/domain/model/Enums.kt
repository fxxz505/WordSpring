package com.wordspring.app.domain.model

/**
 * Scheduling stage of a review card.
 * - NEW: never studied
 * - LEARNING: in initial 10min/1day steps
 * - REVIEW: in normal interval growth
 * - SUSPENDED: paused, excluded from queue
 */
enum class Stage { NEW, LEARNING, REVIEW, SUSPENDED }

/**
 * Answer grade.
 * AGAIN: forgot / wrong / typo that changed the word
 * HARD: fuzzy
 * GOOD: correct
 * EASY: very fluent (advanced grading only)
 */
enum class Grade { AGAIN, HARD, GOOD, EASY }

enum class QuestionType { RECALL, SPELLING, CHOICE }

enum class BookSourceType { BUILT_IN, IMPORTED_CSV, CUSTOM }

/** Direction the user studies. Spec fixes this to en -> zh-CN. */
object StudyDirection {
    const val SOURCE_LANGUAGE = "en"
    const val TARGET_LANGUAGE = "zh-CN"
}
