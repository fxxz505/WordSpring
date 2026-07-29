package com.wordspring.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke instrumented test that runs on the emulator.
 *
 * Verifies that:
 *  - The app package is installed and its Context is accessible.
 *  - The Hilt application graph can be built on-device (HiltAndroidRule).
 *  - The app's package name matches the expected applicationId.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun appContext_isWordSpringPackage() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertThat(ctx.packageName).isEqualTo("com.wordspring.app")
    }

    @Test
    fun hiltApplicationGraph_buildsSuccessfully() {
        // HiltAndroidRule launches the generated test application component.
        // Reaching this assertion means @Inject fields could be resolved on-device.
        hiltRule.inject()
        assertThat(hiltRule).isNotNull()
    }
}
