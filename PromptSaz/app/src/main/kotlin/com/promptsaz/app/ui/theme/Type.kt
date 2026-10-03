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
 * Typography scale: Persian needs slightly larger sizes and looser line
 * heights than the Material default, and a clear jump between the title and
 * body levels so hierarchy survives even at small sizes.
 */
private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Vazirmatn),
    displayMedium = base.displayMedium.copy(fontFamily = Vazirmatn),
    displaySmall = base.displaySmall.copy(fontFamily = Vazirmatn),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = Vazirmatn, fontSize = 32.sp, lineHeight = 44.sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = Vazirmatn, fontSize = 28.sp, lineHeight = 40.sp,
    ),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = Vazirmatn, fontSize = 24.sp, lineHeight = 36.sp,
    ),
    titleLarge = base.titleLarge.copy(
        fontFamily = Vazirmatn, fontSize = 21.sp, lineHeight = 32.sp,
    ),
    titleMedium = base.titleMedium.copy(
        fontFamily = Vazirmatn, fontSize = 17.sp, lineHeight = 28.sp,
    ),
    titleSmall = base.titleSmall.copy(
        fontFamily = Vazirmatn, fontSize = 15.sp, lineHeight = 24.sp,
    ),
    bodyLarge = base.bodyLarge.copy(
        fontFamily = Vazirmatn, fontSize = 16.sp, lineHeight = 30.sp,
    ),
    bodyMedium = base.bodyMedium.copy(
        fontFamily = Vazirmatn, fontSize = 14.sp, lineHeight = 26.sp,
    ),
    bodySmall = base.bodySmall.copy(
        fontFamily = Vazirmatn, fontSize = 12.sp, lineHeight = 20.sp,
    ),
    labelLarge = base.labelLarge.copy(
        fontFamily = Vazirmatn, fontSize = 14.sp, lineHeight = 22.sp,
    ),
    labelMedium = base.labelMedium.copy(
        fontFamily = Vazirmatn, fontSize = 12.sp, lineHeight = 18.sp,
    ),
    labelSmall = base.labelSmall.copy(
        fontFamily = Vazirmatn, fontSize = 11.sp, lineHeight = 16.sp,
    ),
)
