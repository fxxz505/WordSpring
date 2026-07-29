package com.wordspring.app.feature.study

internal data class CardOffset(val x: Float, val y: Float)

/** Every new prompt is rendered from a neutral origin, never an old swipe position. */
internal fun nextCardOffset(previousX: Float, previousY: Float): CardOffset = CardOffset(0f, 0f)

/** A requeued word is a new queue occurrence, so it must not reuse old gesture state. */
internal fun cardInstanceKey(cardId: Long, queueIndex: Int): String = "$cardId:$queueIndex"

internal fun shouldAutoAdvance(answerSaved: Boolean): Boolean = answerSaved

/** Cancels the visual mirror introduced while the front face rotates 180 degrees. */
internal fun cardFaceScaleX(rotationY: Float): Float = if (rotationY > 90f) -1f else 1f

/** Both faces use the same fixed fraction of the study area to avoid resize on flip. */
internal fun cardFaceHeightFraction(): Float = 0.92f

/** Visual order of the recall gesture legend. */
internal fun swipeHintDirections(): List<String> = listOf("left", "up", "right")
