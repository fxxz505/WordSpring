package com.wordspring.app.feature.study

import com.wordspring.app.domain.model.Grade
import kotlin.math.abs

internal enum class SwipeVisualTone { AGAIN_RED, HARD_ORANGE, GOOD_GREEN, NEUTRAL }

/** Maps only deliberate, supported card swipes to the three review grades. */
internal fun resolveSwipeGrade(
    offsetX: Float,
    offsetY: Float,
    thresholdPx: Float
): Grade? = when {
    offsetY <= -thresholdPx && abs(offsetY) > abs(offsetX) -> Grade.HARD
    offsetX >= thresholdPx && abs(offsetX) >= abs(offsetY) -> Grade.AGAIN
    offsetX <= -thresholdPx && abs(offsetX) >= abs(offsetY) -> Grade.GOOD
    else -> null
}

internal fun swipeVisualTone(offsetX: Float, offsetY: Float): SwipeVisualTone = when {
    offsetY < 0f && abs(offsetY) > abs(offsetX) -> SwipeVisualTone.HARD_ORANGE
    offsetX > 0f -> SwipeVisualTone.AGAIN_RED
    offsetX < 0f -> SwipeVisualTone.GOOD_GREEN
    else -> SwipeVisualTone.NEUTRAL
}
