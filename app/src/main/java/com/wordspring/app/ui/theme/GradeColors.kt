package com.wordspring.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Fixed feedback colours for grading and answers: 记得/正确 = green, 模糊 = amber,
 * 不记得/错误 = red. These deliberately ignore dynamic (wallpaper) colour, which would
 * otherwise turn "记得" purple or blue. Colour is never the only signal; labels and icons
 * always accompany it.
 */
@Immutable
data class GradeColors(
    val remembered: Color,
    val fuzzy: Color,
    val forgot: Color
)

private val LightGradeColors = GradeColors(
    remembered = Color(0xFF1E8A4C),
    fuzzy = Color(0xFFC06A00),
    forgot = Color(0xFFC8433B)
)

private val DarkGradeColors = GradeColors(
    remembered = Color(0xFF7FDA9F),
    fuzzy = Color(0xFFFFB766),
    forgot = Color(0xFFFF9F95)
)

/** Picks the light or dark palette from the current surface, so forced themes work too. */
@Composable
fun gradeColors(): GradeColors =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkGradeColors else LightGradeColors
