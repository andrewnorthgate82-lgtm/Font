package com.promptsaz.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.promptsaz.app.ui.theme.BrandGradient
import com.promptsaz.app.util.toPersianDigits

/**
 * The PromptSaz component kit: every screen is assembled from these pieces so
 * the whole app shares one visual language — pill chips, soft cards, one
 * signature gradient, and generous touch targets.
 */

// ---------------------------------------------------------------- headers ----

/** Screen header: soft back button, gradient title, optional actions. */
@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(9.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    brush = BrandGradient,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )
            actions()
        }
    }
}

// ------------------------------------------------------------------ chips ----

/** The one chip of the app: animated pill, brand color when selected. */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(200),
        label = "chipBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "chipContent",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = contentColor,
        shadowElevation = if (selected) 2.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            },
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Single-select chip row, horizontally scrollable for small screens. */
@Composable
fun SelectChipRow(
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) ->
            PillChip(label = label, selected = id == selectedId, onClick = { onSelect(id) })
        }
    }
}

// --------------------------------------------------------------- buttons ----

/**
 * The primary call-to-action: the brand gradient, pill-shaped, glowing.
 * Use once per screen, for the main action.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(50)
    val disabledBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    )
    Box(
        modifier = modifier
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
            )
            .clip(shape)
            .background(if (enabled) BrandGradient else disabledBrush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Soft circular icon button for headers and toolbars. */
@Composable
fun SoftIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.padding(9.dp),
        )
    }
}

// ------------------------------------------------------------ structure ----

/** Section label with the brand accent bar — the app's rhythm marker. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BrandGradient),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Round number badge for list steps (۱، ۲، ۳…). */
@Composable
fun NumberBadge(number: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Rounded progress bar with a gradient fill — used for multi-step flows.
 * Always shows the numbers too, never color-only.
 */
@Composable
fun StepProgress(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String = "پیشرفت",
) {
    val fraction = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(400),
        label = "stepProgress",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${current.toPersianDigits()} از ${total.toPersianDigits()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(50))
                    .background(BrandGradient),
            )
        }
    }
}

// ------------------------------------------------------------- feedback ----

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.Bold)
        },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

/** Animated score ring: «۸۶ از ۱۰۰» — number always included, never color-only. */
@Composable
fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(700),
        label = "scoreProgress",
    )
    val color = when {
        score >= 85 -> MaterialTheme.colorScheme.primary
        score >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.size(68.dp),
                color = color,
                strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Text(
                text = score.toPersianDigits(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color,
            )
        }
        Column {
            Text(
                text = "از ${100.toPersianDigits()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = "امتیاز کامل بودن ساختار",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
