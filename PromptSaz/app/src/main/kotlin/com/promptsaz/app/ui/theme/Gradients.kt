package com.promptsaz.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The brand's only gradients — used sparingly (primary action, hero tile,
 * progress fill). Everything else stays flat and neutral.
 */

/** Indigo → violet: the signature. */
val BrandGradient = Brush.linearGradient(
    listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6)),
)

/** Indigo → violet → amber: hero moments only. */
val BrandGradientWarm = Brush.linearGradient(
    listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6), Color(0xFFF59E0B)),
)

/** Soft diagonal wash for hero cards; pass the page background color. */
fun brandWash(base: Color): Brush = Brush.linearGradient(
    listOf(
        Color(0xFF4F46E5).copy(alpha = 0.08f),
        Color(0xFF8B5CF6).copy(alpha = 0.03f),
        base,
    ),
)

/** Hairline gradient border for cards that should feel alive. */
fun brandGlowBorder(): Brush = Brush.linearGradient(
    listOf(
        Color(0xFF4F46E5).copy(alpha = 0.35f),
        Color(0xFF8B5CF6).copy(alpha = 0.15f),
        Color(0x00FFFFFF),
    ),
)
