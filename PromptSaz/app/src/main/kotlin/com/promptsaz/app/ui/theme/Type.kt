package com.promptsaz.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.promptsaz.app.R

/** Bundled Vazirmatn v33.003 static weights (OFL). */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/**
 * 2026 type scale: compact titles with clear jumps between levels, roomy
 * body line-heights for Persian readability, small quiet labels.
 */
private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Vazirmatn),
    displayMedium = base.displayMedium.copy(fontFamily = Vazirmatn),
    displaySmall = base.displaySmall.copy(fontFamily = Vazirmatn),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = Vazirmatn, fontSize = 30.sp, lineHeight = 42.sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = Vazirmatn, fontSize = 26.sp, lineHeight = 38.sp,
    ),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = Vazirmatn, fontSize = 22.sp, lineHeight = 32.sp,
    ),
    titleLarge = base.titleLarge.copy(
        fontFamily = Vazirmatn, fontSize = 20.sp, lineHeight = 30.sp,
    ),
    titleMedium = base.titleMedium.copy(
        fontFamily = Vazirmatn, fontSize = 17.sp, lineHeight = 27.sp,
    ),
    titleSmall = base.titleSmall.copy(
        fontFamily = Vazirmatn, fontSize = 15.sp, lineHeight = 24.sp,
    ),
    bodyLarge = base.bodyLarge.copy(
        fontFamily = Vazirmatn, fontSize = 16.sp, lineHeight = 29.sp,
    ),
    bodyMedium = base.bodyMedium.copy(
        fontFamily = Vazirmatn, fontSize = 14.sp, lineHeight = 25.sp,
    ),
    bodySmall = base.bodySmall.copy(
        fontFamily = Vazirmatn, fontSize = 12.sp, lineHeight = 19.sp,
    ),
    labelLarge = base.labelLarge.copy(
        fontFamily = Vazirmatn, fontSize = 14.sp, lineHeight = 21.sp,
    ),
    labelMedium = base.labelMedium.copy(
        fontFamily = Vazirmatn, fontSize = 12.sp, lineHeight = 17.sp,
    ),
    labelSmall = base.labelSmall.copy(
        fontFamily = Vazirmatn, fontSize = 11.sp, lineHeight = 15.sp,
    ),
)
