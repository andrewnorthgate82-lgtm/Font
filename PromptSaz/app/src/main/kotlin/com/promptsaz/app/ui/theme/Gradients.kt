package com.promptsaz.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand gradients — the single visual signature of the app. Used sparingly:
 * the logo tile, primary call-to-action, progress fill, score accents and
 * section markers. Everything else stays calm and neutral.
 */

/** Indigo → violet: the primary brand gradient. */
val BrandGradient = Brush.linearGradient(
    listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6)),
)

/** Indigo → violet → amber: used only for the big hero moments. */
val BrandGradientWarm = Brush.linearGradient(
    listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6), Color(0xFFF59E0B)),
)

/** Soft diagonal wash for hero cards; pass the page background color. */
fun brandWash(base: Color): Brush = Brush.linearGradient(
    listOf(
        Color(0xFF4F46E5).copy(alpha = 0.10f),
        Color(0xFF8B5CF6).copy(alpha = 0.04f),
        base,
    ),
)

/** Gentle glow border for cards that should feel alive. */
fun brandGlowBorder(): Brush = Brush.linearGradient(
    listOf(
        Color(0xFF4F46E5).copy(alpha = 0.45f),
        Color(0xFF8B5CF6).copy(alpha = 0.20f),
        Color(0x00FFFFFF),
    ),
)
