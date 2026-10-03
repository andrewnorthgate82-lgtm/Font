package com.promptsaz.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.ui.components.SectionLabel
import com.promptsaz.app.ui.components.SelectChipRow
import com.promptsaz.app.util.toPersianDigits

/**
 * The minimal home: one text box, one primary button — plus a collapsed
 * options row (domain / target AI / output language / detail level).
 */
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "پرامپت‌ساز",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNavigateToKb) {
                Icon(Icons.Rounded.MenuBook, contentDescription = "دانش‌نامه حوزه‌ها")
            }
            IconButton(onClick = onNavigateToArchive) {
                Icon(Icons.Rounded.Archive, contentDescription = "آرشیو پرامپت‌ها")
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "تنظیمات")
            }
        }

        Text(
            text = if (state.improveMode) {
                "پرامپت فعلی‌ات را همین‌جا بچسبان تا نقاط ضعفش پیدا و حرفه‌ای بازنویسی شود"
            } else {
                "بگو چه می‌خواهی؛ پرامپت حرفه‌ایش را می‌سازم"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        OutlinedTextField(
            value = state.idea,
            onValueChange = viewModel::setIdea,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            placeholder = {
                Text(
                    text = if (state.improveMode) "پرامپت فعلی را کامل بچسبان…" else "مثلاً: می‌خواهم برای فروش دوره آنلاینم تبلیغ اینستاگرام بنویسم",
                )
            },
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
                    )
                    Text(
                        text = "${state.ideaLength.toPersianDigits()} از ۴۰۰۰",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !state.improveMode,
                onClick = { viewModel.setImproveMode(false) },
                label = { Text("پرامپت جدید") },
            )
            FilterChip(
                selected = state.improveMode,
                onClick = { viewModel.setImproveMode(true) },
                label = { Text("بهبود پرامپت موجود") },
            )
            if (state.aiEnabled) {
                Icon(
                    Icons.Rounded.Cloud,
                    contentDescription = "حالت هوش مصنوعی فعال است",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }

        TextButton(
            onClick = { optionsExpanded = !optionsExpanded },
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(
                text = "تنظیمات پرامپت",
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (optionsExpanded) {
                    Icons.AutoMirrored.Rounded.KeyboardArrowUp
                } else {
                    Icons.AutoMirrored.Rounded.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(visible = optionsExpanded) {
            Column {
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

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { if (viewModel.startGeneration()) onNavigateToClarify() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(
                text = if (state.improveMode) "بهبود و بازنویسی" else "طراحی پرامپت",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(
            text = "پاسخ چند سؤال کوتاه، پرامپت را دقیق‌تر می‌کند؛ می‌توانی همه را رد کنی.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .align(Alignment.CenterHorizontally),
        )
    }
}
