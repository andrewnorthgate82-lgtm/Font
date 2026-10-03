package com.promptsaz.app.ui.screens.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.VariantStyle
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.EmptyState
import com.promptsaz.app.ui.components.ScoreBadge
import com.promptsaz.app.ui.components.SectionLabel
import com.promptsaz.app.util.PlatformUtils
import com.promptsaz.app.util.toPersianDigits

/**
 * The result: score + breakdown, one-tap improvements, variants, the prompt
 * card with copy/share, and (in improve mode) the weakness findings.
 */
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var breakdownExpanded by remember { mutableStateOf(false) }

    val result = state.result
    if (result == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(title = "نتیجه", onBack = onBack)
            EmptyState(
                icon = Icons.Rounded.Warning,
                title = "نتیجه‌ای برای نمایش نیست",
                hint = "از صفحه اول یک پرامپت جدید بساز",
            )
            Button(
                onClick = onNavigateHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) { Text("بازگشت به خانه") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = result.title,
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            state.savedNoticeFa?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // AI fallback notice
            state.aiErrorFa?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "هوش مصنوعی در دسترس نبود؛ با موتور آفلاین ساخته شد:\n$message",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // Score card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreBadge(score = result.score.total)
                        Spacer(Modifier.weight(1f))
                        if (result.score.suggestionsFa.isNotEmpty()) {
                            OutlinedButton(onClick = viewModel::improveStructure, enabled = !state.working) {
                                Text("بهبود ساختار")
                            }
                        }
                    }
                    if (result.score.sectionScores.isNotEmpty()) {
                        TextButton(onClick = { breakdownExpanded = !breakdownExpanded }) {
                            Text(
                                if (breakdownExpanded) "بستن جزئیات امتیاز" else "جزئیات امتیاز هر بخش",
                            )
                        }
                        if (breakdownExpanded) {
                            result.score.sectionScores.forEach { section ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = section.titleFa,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${section.earned.toPersianDigits()}/${section.max.toPersianDigits()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (section.earned == section.max) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.tertiary
                                        },
                                    )
                                }
                                Text(
                                    text = section.noteFa,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    result.score.suggestionsFa.takeIf { it.isNotEmpty() }?.let { suggestions ->
                        SectionLabel("پیشنهادهای بهبود")
                        suggestions.forEach { suggestion ->
                            Text(
                                text = "• $suggestion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Improve-mode findings
            if (state.findings.isNotEmpty()) {
                SectionLabel("نقاط ضعف پرامپت اصلی")
                state.findings.forEach { finding ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (finding.severity == com.promptsaz.app.domain.model.FindingSeverity.CRITICAL) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${finding.severity.labelFa}: ${finding.messageFa}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "راه‌حل: ${finding.fixFa}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Variants
            if (!result.isAiGenerated) {
                SectionLabel("نسخه‌های جایگزین")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VariantStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.variant == style,
                            onClick = { viewModel.selectVariant(style) },
                            label = { Text(style.labelFa) },
                            enabled = !state.working,
                        )
                    }
                    if (state.working) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    }
                }
            }

            // Prompt card
            SectionLabel("پرامپت نهایی")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = result.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                    )
                }
            }

            state.errorFa?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
            }

            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { PlatformUtils.copyWithFeedback(context, result.text) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("کپی", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { PlatformUtils.shareText(context, result.text) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Text("اشتراک", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = viewModel::regenerate,
                    enabled = !state.working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("بازسازی", modifier = Modifier.padding(start = 8.dp))
                }
            }

            Text(
                text = "این پرامپت را در ${result.spec.targetAi.labelFa} کپی کن و نتیجه را ببین.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
