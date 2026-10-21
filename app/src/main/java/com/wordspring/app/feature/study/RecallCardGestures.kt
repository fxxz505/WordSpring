package com.wordspring.app.feature.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.wordspring.app.domain.model.Grade
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Visual offset of the recall card, shared by the gesture handler and the grade buttons. */
@Stable
internal class CardSwipeState {
    val x = Animatable(0f)
    val y = Animatable(0f)

    suspend fun snapTo(newX: Float, newY: Float) {
        x.snapTo(newX)
        y.snapTo(newY)
    }

    suspend fun reset() = coroutineScope {
        launch { x.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
        launch { y.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
    }

    /** Throws the card off screen in the direction that matches [grade]. */
    suspend fun flyOut(grade: Grade, distance: Float) {
        val spec = tween<Float>(durationMillis = 220)
        when (grade) {
            Grade.AGAIN -> x.animateTo(distance, spec)
            Grade.HARD -> y.animateTo(-distance, spec)
            Grade.GOOD -> x.animateTo(-distance, spec)
            Grade.EASY -> y.animateTo(distance, spec)
        }
    }
}

/**
 * Recall-card gestures. On the front a tap reveals the answer; once revealed, dragging moves
 * the card and a release past the threshold (or a quick flick) grades it:
 * left = GOOD, up = HARD, right = AGAIN. Other states ignore input.
 *
 * [cardState] is a pointerInput key on purpose: pointerInput restarts its block only when a
 * key changes, never when the lambda changes. Keying on the card alone left the tap detector
 * running after the flip, so swipes were never recognised.
 */
internal fun Modifier.recallCardGestures(
    instanceKey: Any,
    cardState: CardState,
    swipe: CardSwipeState,
    thresholdPx: Float,
    flingVelocityPx: Float,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val currentOnReveal by rememberUpdatedState(onReveal)
    val currentOnGrade by rememberUpdatedState(onGrade)
    pointerInput(instanceKey, cardState) {
        when (cardState) {
            CardState.QUESTION -> detectTapGestures(onTap = { currentOnReveal() })
            CardState.ANSWER_REVEALED -> {
                val tracker = VelocityTracker()
                // Tracked here rather than read back from the Animatable so the release decision
                // never sees a stale offset while snapTo calls are still queued.
                var dragX = 0f
                var dragY = 0f
                detectDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        dragX = swipe.x.value
                        dragY = swipe.y.value
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity()
                        val grade = resolveSwipeGradeWithFling(
                            dragX, dragY, velocity.x, velocity.y, thresholdPx, flingVelocityPx
                        )
                        if (grade != null) currentOnGrade(grade) else scope.launch { swipe.reset() }
                    },
                    onDragCancel = { scope.launch { swipe.reset() } },
                    onDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dragX += amount.x
                        dragY += amount.y
                        val x = dragX
                        val y = dragY
                        scope.launch { swipe.snapTo(x, y) }
                    }
                )
            }
            else -> Unit
        }
    }
}
