package com.promptsaz.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.promptsaz.app.R

/** Bundled Vazirmatn v33.003 static weights (OFL). */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Vazirmatn),
    displayMedium = base.displayMedium.copy(fontFamily = Vazirmatn),
    displaySmall = base.displaySmall.copy(fontFamily = Vazirmatn),
    headlineLarge = base.headlineLarge.copy(fontFamily = Vazirmatn),
    headlineMedium = base.headlineMedium.copy(fontFamily = Vazirmatn),
    headlineSmall = base.headlineSmall.copy(fontFamily = Vazirmatn),
    titleLarge = base.titleLarge.copy(fontFamily = Vazirmatn),
    titleMedium = base.titleMedium.copy(fontFamily = Vazirmatn),
    titleSmall = base.titleSmall.copy(fontFamily = Vazirmatn),
    bodyLarge = base.bodyLarge.copy(fontFamily = Vazirmatn),
    bodyMedium = base.bodyMedium.copy(fontFamily = Vazirmatn),
    bodySmall = base.bodySmall.copy(fontFamily = Vazirmatn),
    labelLarge = base.labelLarge.copy(fontFamily = Vazirmatn),
    labelMedium = base.labelMedium.copy(fontFamily = Vazirmatn),
    labelSmall = base.labelSmall.copy(fontFamily = Vazirmatn),
)
