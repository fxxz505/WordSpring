package com.wordspring.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Font sizes respect 200% scaling via Compose density. Use sp everywhere.
val WordSpringTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, lineHeight = 48.sp),
    displayMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp),
    displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
)
