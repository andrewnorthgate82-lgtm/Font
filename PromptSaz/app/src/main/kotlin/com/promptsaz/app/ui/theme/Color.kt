package com.promptsaz.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand: deep teal family, tuned for Persian reading in light and dark.
private val Teal10 = Color(0xFF001F24)
private val Teal20 = Color(0xFF00363C)
private val Teal40 = Color(0xFF004F58)
private val Teal80 = Color(0xFF63DBD8)
private val Teal90 = Color(0xFF83F5F0)

private val TealGrey10 = Color(0xFF191C1D)
private val TealGrey20 = Color(0xFF2D3132)
private val TealGrey90 = Color(0xFFDDE3E3)
private val TealGrey95 = Color(0xFFF0FBF9)
private val TealGrey99 = Color(0xFFFBFDFF)

private val Accent40 = Color(0xFF815600)
private val Accent90 = Color(0xFFFFB95C)

val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Accent40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0AE),
    onTertiaryContainer = Accent40,
    background = TealGrey99,
    onBackground = TealGrey10,
    surface = TealGrey99,
    onSurface = TealGrey10,
    surfaceVariant = TealGrey95,
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7979),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal10,
    primaryContainer = Teal20,
    onPrimaryContainer = Teal90,
    secondary = Teal80,
    onSecondary = Teal10,
    secondaryContainer = Teal20,
    onSecondaryContainer = Teal90,
    tertiary = Accent90,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFE0AE),
    background = TealGrey10,
    onBackground = TealGrey90,
    surface = TealGrey10,
    onSurface = TealGrey90,
    surfaceVariant = TealGrey20,
    onSurfaceVariant = Color(0xFFBEC8C8),
    outline = Color(0xFF889392),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
