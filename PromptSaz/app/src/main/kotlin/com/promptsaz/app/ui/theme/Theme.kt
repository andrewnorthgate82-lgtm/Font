package com.promptsaz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.promptsaz.app.domain.model.ThemeMode

/**
 * Material 3 theme with the bundled Vazirmatn typography.
 *
 * Shape language: pills for anything tappable (chips, buttons, progress),
 * generously rounded cards (20dp), softer fields (14dp) — one consistent,
 * friendly system across every screen.
 */
@Composable
fun PromptSazTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(14.dp),
            small = RoundedCornerShape(50), // chips → pill
            medium = RoundedCornerShape(20.dp), // cards
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
