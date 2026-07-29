package com.wordspring.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EDE1),
    onPrimaryContainer = Color(0xFF063C2E),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEC0),
    onSecondaryContainer = Color(0xFF542600),
    tertiary = BrandTertiary,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1A2D25),
    surface = LightSurface,
    onSurface = Color(0xFF1A2D25),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF56675D),
    outline = LightOutline,
    error = IncorrectColor,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF063C2E),
    primaryContainer = Color(0xFF285D4D),
    onPrimaryContainer = Color(0xFFD9EDE1),
    secondary = BrandSecondaryDark,
    onSecondary = Color(0xFF542600),
    secondaryContainer = Color(0xFF754408),
    onSecondaryContainer = Color(0xFFFFDEC0),
    tertiary = CorrectColorDark,
    onTertiary = Color(0xFF00391F),
    background = DarkBackground,
    onBackground = Color(0xFFE2F1E7),
    surface = DarkSurface,
    onSurface = Color(0xFFE2F1E7),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB6CDC0),
    outline = DarkOutline,
    error = IncorrectColorDark,
    onError = Color(0xFF690000)
)

/** Whether the user has asked us to reduce motion. Read by animation helpers. */
val LocalReduceMotion = compositionLocalOf { false }

/** Whether dynamic color (Android 12+) is enabled. */
val LocalDynamicColorEnabled = compositionLocalOf { true }

enum class ThemeMode { System, Light, Dark }

@Composable
fun WordSpringTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalDynamicColorEnabled provides dynamicColor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WordSpringTypography,
            shapes = WordSpringShapes,
            content = content
        )
    }
}
