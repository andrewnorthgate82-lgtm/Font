package com.promptsaz.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.promptsaz.app.domain.model.ThemeMode

/**
 * The one shape system of the app (2026 minimal):
 *  - pills (50%) for anything tappable-small (chips, buttons, segments)
 *  - 16dp fields, 24dp cards / list groups, 32dp sheets and hero tiles
 * Everything else inherits; screens never pass ad-hoc corner radii.
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

    // edge-to-edge: keep the status/navigation icon contrast in sync with the
    // app theme (not just the system one)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(16.dp), // fields
            medium = RoundedCornerShape(24.dp), // cards / groups
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp), // sheets / hero tiles
        ),
        content = content,
    )
}
