package com.promptsaz.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.ui.components.ModelPickerSheet
import com.promptsaz.app.ui.components.SectionLabel
import com.promptsaz.app.ui.components.SelectChipRow
import com.promptsaz.app.ui.components.SoftIconButton
import com.promptsaz.app.ui.theme.BrandGradient
import com.promptsaz.app.util.toPersianDigits

/**
 * تولید پرامپت tab — the گفتگو tab's skeleton at its most minimal: a top bar
 * with ONE overflow menu (everything secondary lives there) + the model pill,
 * a calm scrollable middle (mode switch + prompt settings) and the bottom
 * input bar with the gradient action button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToClarify: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToKb: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var optionsExpanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // --- top bar: one menu + title + model pill — nothing else ------------
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Box {
                    SoftIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "گزینه‌های بیشتر",
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("دانش‌نامه حوزه‌ها") },
                            leadingIcon = { Icon(Icons.Rounded.MenuBook, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onNavigateToKb()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("آرشیو پرامپت‌ها") },
                            leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onNavigateToArchive()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("تنظیمات") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onNavigateToSettings()
                            },
                        )
                    }
                }
                Text(
                    text = "تولید پرامپت",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // model chip → picker sheet (offline engine when AI is off)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { if (state.aiEnabled) viewModel.openModelPicker() else onNavigateToSettings() },
                ) {
                    Text(
                        text = when {
                            !state.aiEnabled -> "موتور داخلی"
                            state.selectedModel.isNotBlank() -> state.selectedModel
                            else -> "انتخاب مدل"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 130.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // --- middle: mode + prompt settings ----------------------------------
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = if (state.improveMode) {
                    "پرامپت فعلی‌ات را همین‌جا بچسبان تا حرفه‌ای بازنویسی شود"
                } else {
                    "بگو چه می‌خواهی؛ پرامپت حرفه‌ایش را می‌سازم"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )

            // mode switch — full width, two short labels
            ModeSegment(
                firstLabel = "پرامپت جدید",
                secondLabel = "بهبود پرامپت",
                secondSelected = state.improveMode,
                onFirst = { viewModel.setImproveMode(false) },
                onSecond = { viewModel.setImproveMode(true) },
                modifier = Modifier.fillMaxWidth(),
            )

            // prompt settings (expandable)
            Surface(
                onClick = { optionsExpanded = !optionsExpanded },
                shape = RoundedCornerShape(50),
                color = if (optionsExpanded) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "تنظیمات پرامپت",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (optionsExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Icon(
                        imageVector = if (optionsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = if (optionsExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            AnimatedVisibility(visible = optionsExpanded) {
                Column(modifier = Modifier.animateContentSize()) {
                    SectionLabel("حوزه")
                    SelectChipRow(
                        options = state.domains.map { it.id to it.nameFa },
                        selectedId = state.domainId,
                        onSelect = viewModel::setDomain,
                    )
                    SectionLabel("هدفت کدام مدل است؟")
                    SelectChipRow(
                        options = TargetAi.entries.map { it.id to it.labelFa },
                        selectedId = state.targetAi.id,
                        onSelect = { id -> viewModel.setTarget(TargetAi.fromId(id)) },
                    )
                    SectionLabel("زبان پرامپت")
                    SelectChipRow(
                        options = OutputLanguage.entries.map { it.id to it.labelFa },
                        selectedId = state.outputLanguage.id,
                        onSelect = { id -> viewModel.setLanguage(OutputLanguage.fromId(id)) },
                    )
                    SectionLabel("سطح جزئیات")
                    SelectChipRow(
                        options = DetailLevel.entries.map { it.id to it.labelFa },
                        selectedId = state.detailLevel.id,
                        onSelect = { id -> viewModel.setDetail(DetailLevel.fromId(id)) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "چند سؤال کوتاه بعدی، پرامپت را دقیق‌تر می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- bottom input bar (like the گفتگو tab) ----------------------------
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                OutlinedTextField(
                    value = state.idea,
                    onValueChange = viewModel::setIdea,
                    placeholder = {
                        Text(
                            text = if (state.improveMode) {
                                "پرامپت فعلی را بچسبان…"
                            } else {
                                "مثلاً: تبلیغ اینستاگرام برای دورهٔ آنلاینی"
                            },
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    maxLines = 4,
                    isError = state.errorFa != null,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = state.errorFa ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.ideaLength > 0) {
                                Text(
                                    text = "${state.ideaLength.toPersianDigits()} از ${4000.toPersianDigits()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                val canGenerate = state.idea.isNotBlank()
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    onClick = { if (viewModel.startGeneration()) onNavigateToClarify() },
                    enabled = canGenerate,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (canGenerate) {
                                    BrandGradient
                                } else {
                                    SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                                },
                            )
                            .size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = if (state.improveMode) "بهبود و بازنویسی" else "طراحی پرامپت",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    ModelPickerSheet(
        visible = state.modelPickerVisible,
        models = state.models,
        selectedModel = state.selectedModel,
        loading = state.modelsLoading,
        errorFa = state.modelsErrorFa,
        onSelect = viewModel::selectModel,
        onRefresh = viewModel::loadModels,
        onDismiss = viewModel::dismissModelPicker,
    )
}

/** Two-option segmented switch — the app's single mode control. */
@Composable
private fun ModeSegment(
    firstLabel: String,
    secondLabel: String,
    secondSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
        ) {
            SegmentOption(
                label = firstLabel,
                selected = !secondSelected,
                onClick = onFirst,
                modifier = Modifier.weight(1f),
            )
            SegmentOption(
                label = secondLabel,
                selected = secondSelected,
                onClick = onSecond,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) BrandGradient else null
    val textColor = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (background != null) {
                    Modifier.background(background)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}
