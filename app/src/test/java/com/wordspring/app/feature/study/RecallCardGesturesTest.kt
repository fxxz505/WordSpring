package com.wordspring.app.feature.study

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.Grade
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression: after tapping the front, the same gesture node must switch from tap to drag
 * detection. Previously both used pointerInput(instanceKey) so swipes were silently ignored.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RecallCardGesturesTest {

    @get:Rule
    val compose = createComposeRule()

    private val grades = mutableListOf<Grade>()
    private var reveals = 0

    private fun setCard(initial: CardState = CardState.QUESTION) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            val swipe = remember { CardSwipeState() }
            Box(
                Modifier
                    .size(360.dp, 480.dp)
                    .testTag("card")
                    .recallCardGestures(
                        instanceKey = 1L,
                        cardState = state,
                        swipe = swipe,
                        thresholdPx = 150f,
                        flingVelocityPx = 100_000f, // disable flings: only full swipes count
                        onReveal = {
                            reveals++
                            state = CardState.ANSWER_REVEALED
                        },
                        onGrade = {
                            grades += it
                            state = CardState.SUBMITTING
                        }
                    )
            )
        }
    }

    @Test
    fun `tap then swipe left grades GOOD`() {
        setCard()
        compose.onNodeWithTag("card").performClick()
        compose.waitForIdle()
        assertThat(reveals).isEqualTo(1)
        compose.onNodeWithTag("card").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(grades).containsExactly(Grade.GOOD)
    }

    @Test
    fun `swipe right grades AGAIN`() {
        setCard(CardState.ANSWER_REVEALED)
        compose.onNodeWithTag("card").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(grades).containsExactly(Grade.AGAIN)
    }

    @Test
    fun `swipe up grades HARD`() {
        setCard(CardState.ANSWER_REVEALED)
        compose.onNodeWithTag("card").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertThat(grades).containsExactly(Grade.HARD)
    }

    @Test
    fun `swipe on the front does not grade and swipe down is ignored`() {
        setCard()
        compose.onNodeWithTag("card").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(grades).isEmpty()

        compose.onNodeWithTag("card").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("card").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertThat(grades).isEmpty()
    }
}
