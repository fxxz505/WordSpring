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

/**
 * Like [resolveSwipeGrade] but also accepts a quick flick: a release faster than
 * [flingVelocityPx] (px/s) along the dominant axis, after moving at least a third of the
 * threshold in that direction, counts as a full swipe.
 */
internal fun resolveSwipeGradeWithFling(
    offsetX: Float,
    offsetY: Float,
    velocityX: Float,
    velocityY: Float,
    thresholdPx: Float,
    flingVelocityPx: Float
): Grade? {
    resolveSwipeGrade(offsetX, offsetY, thresholdPx)?.let { return it }
    val minTravel = thresholdPx / 3f
    return when {
        abs(velocityY) > abs(velocityX) && velocityY <= -flingVelocityPx && offsetY <= -minTravel -> Grade.HARD
        abs(velocityX) >= abs(velocityY) && velocityX >= flingVelocityPx && offsetX >= minTravel -> Grade.AGAIN
        abs(velocityX) >= abs(velocityY) && velocityX <= -flingVelocityPx && offsetX <= -minTravel -> Grade.GOOD
        else -> null
    }
}
