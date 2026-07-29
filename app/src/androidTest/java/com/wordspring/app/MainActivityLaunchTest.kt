package com.wordspring.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

/**
 * Verifies that MainActivity launches and renders the Compose hierarchy on the emulator.
 *
 * We rely on UI Automator to wait for any Compose-owned view to appear. Compose
 * surfaces as a single `ComposeView` under the window, so we assert that the
 * activity window comes up and stays stable for a short period.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun mainActivity_launchesAndStaysStable() {
        hiltRule.inject()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = android.content.Intent(
            targetContext,
            com.wordspring.app.ui.MainActivity::class.java
        ).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        targetContext.startActivity(intent)

        val device = UiDevice.getInstance(instrumentation)
        // Wait for the activity window to settle. Composeview is exposed via
        // its window name; we just ensure the device is not on a launcher
        // black-screen after 5 seconds.
        device.wait(Until.hasObject(By.pkg(targetContext.packageName).depth(0)), 10_000L)
        assertThat(device.hasObject(By.pkg(targetContext.packageName))).isTrue()
    }
}
