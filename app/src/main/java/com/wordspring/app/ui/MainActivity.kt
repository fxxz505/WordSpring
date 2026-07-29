package com.wordspring.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.wordspring.app.data.prefs.SettingsRepository
import com.wordspring.app.ui.nav.WordSpringNavHost
import com.wordspring.app.ui.theme.ThemeMode
import com.wordspring.app.ui.theme.WordSpringTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        // Cold-start settings: read once synchronously to avoid first-frame flash.
        val initial = runCatching {
            runBlocking {
                ThemeState(
                    mode = settingsRepository.themeMode.first(),
                    dynamicColor = settingsRepository.dynamicColor.first(),
                    reduceMotion = settingsRepository.reduceMotion.first()
                )
            }
        }.getOrDefault(ThemeState(ThemeMode.System, true, false))

        // Hot-reload settings: collect updates for recomposition.
        val settingsState = combine(
            settingsRepository.themeMode,
            settingsRepository.dynamicColor,
            settingsRepository.reduceMotion
        ) { mode, dynamic, reduce -> ThemeState(mode, dynamic, reduce) }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, initial)

        setContent {
            val theme by settingsState.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (theme.mode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            WordSpringTheme(
                darkTheme = dark,
                dynamicColor = theme.dynamicColor,
                reduceMotion = theme.reduceMotion
            ) {
                WordSpringNavHost()
            }
        }
    }
}

private data class ThemeState(
    val mode: ThemeMode,
    val dynamicColor: Boolean,
    val reduceMotion: Boolean
)
