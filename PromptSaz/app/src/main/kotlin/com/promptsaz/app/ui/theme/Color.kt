package com.promptsaz.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * «چیستا» unified 2026 palette — one accent family (electric indigo →
 * violet) on Apple-style neutral scaffolding: near-white canvas, pure cards,
 * true-black dark mode. Every screen draws only from this file.
 */

// --- brand ---------------------------------------------------------------------
private val Indigo10 = Color(0xFF12123A)
private val Indigo20 = Color(0xFF1E1E66)
private val Indigo30 = Color(0xFF3634A6)
private val Indigo40 = Color(0xFF4F46E5)
private val Indigo80 = Color(0xFFC3C4FF)
private val Indigo90 = Color(0xFFE4E3FF)

private val Violet20 = Color(0xFF35106E)
private val Violet30 = Color(0xFF4C1FA0)
private val Violet40 = Color(0xFF7C3AED)
private val Violet80 = Color(0xFFD9C2FF)
private val Violet90 = Color(0xFFEEDDFF)

private val Amber30 = Color(0xFF7A4500)
private val Amber40 = Color(0xFFA85F00)
private val Amber80 = Color(0xFFFFB877)
private val Amber90 = Color(0xFFFFDCC2)

// --- neutrals (system-gray family, tuned light + dark) ---------------------------
private val Night = Color(0xFF000000)      // canvas
private val Charcoal = Color(0xFF0F0F14)   // cards
private val Slate = Color(0xFF1D1D24)      // chips / fields
private val Mist = Color(0xFFEDEDF2)       // chips / fields (light)
private val Cloud = Color(0xFFF5F5F7)      // canvas (light)
private val Paper = Color(0xFFFFFFFF)      // cards (light)
private val Ink = Color(0xFF1C1C1E)        // primary text (light)

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
    onSurfaceVariant = Color(0xFF6E6E78),
    surfaceTint = Indigo40,
    inverseSurface = Slate,
    inverseOnSurface = Cloud,
    outline = Color(0xFF8A8A96),
    outlineVariant = Color(0xFFDDDE4),
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
    onBackground = Color(0xFFF5F5F7),
    surface = Charcoal,
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Slate,
    onSurfaceVariant = Color(0xFF9E9EA9),
    surfaceTint = Indigo80,
    inverseSurface = Mist,
    inverseOnSurface = Slate,
    outline = Color(0xFF84848F),
    outlineVariant = Color(0xFF34343E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
