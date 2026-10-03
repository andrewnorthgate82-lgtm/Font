package com.promptsaz.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * «پرامپت‌ساز» brand palette — an indigo→violet creative gradient family with
 * a warm amber accent, tuned for long Persian reading sessions in both light
 * and dark. Contrast pairs are checked against WCAG AA on their surfaces.
 */

// --- core brand ---------------------------------------------------------------
private val Indigo10 = Color(0xFF101038)
private val Indigo20 = Color(0xFF1D1D63)
private val Indigo30 = Color(0xFF3433A8)
private val Indigo40 = Color(0xFF4F46E5)
private val Indigo80 = Color(0xFFBCC0FF)
private val Indigo90 = Color(0xFFE2E0FF)

private val Violet20 = Color(0xFF34106B)
private val Violet30 = Color(0xFF4B1FA0)
private val Violet40 = Color(0xFF7C3AED)
private val Violet80 = Color(0xFFD6BBFF)
private val Violet90 = Color(0xFFECDCFF)

private val Amber30 = Color(0xFF7A4500)
private val Amber40 = Color(0xFFA85F00)
private val Amber80 = Color(0xFFFFB877)
private val Amber90 = Color(0xFFFFDCC2)

// --- neutrals -----------------------------------------------------------------
private val Night = Color(0xFF0F0F17)
private val Charcoal = Color(0xFF1B1C26)
private val Slate = Color(0xFF252633)
private val Mist = Color(0xFFE9E8F3)
private val Cloud = Color(0xFFF4F4FB)
private val Paper = Color(0xFFFCFCFF)
private val Ink = Color(0xFF17171F)

val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Violet40,
    onSecondary = Color.White,
    secondaryContainer = Violet90,
    onSecondaryContainer = Violet20,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber30,
    background = Cloud,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF46455A),
    surfaceTint = Indigo40,
    inverseSurface = Slate,
    inverseOnSurface = Cloud,
    outline = Color(0xFF6E6D85),
    outlineVariant = Color(0xFFC9C8DC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo10,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    secondary = Violet80,
    onSecondary = Violet20,
    secondaryContainer = Violet30,
    onSecondaryContainer = Violet90,
    tertiary = Amber80,
    onTertiary = Amber30,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    background = Night,
    onBackground = Color(0xFFE5E3F0),
    surface = Night,
    onSurface = Color(0xFFE5E3F0),
    surfaceVariant = Slate,
    onSurfaceVariant = Color(0xFFC8C6DA),
    surfaceTint = Indigo80,
    inverseSurface = Mist,
    inverseOnSurface = Slate,
    outline = Color(0xFF93919F),
    outlineVariant = Color(0xFF484755),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
