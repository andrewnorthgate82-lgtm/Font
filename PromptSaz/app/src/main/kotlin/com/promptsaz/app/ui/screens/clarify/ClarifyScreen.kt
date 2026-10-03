package com.promptsaz.app.ui.screens.clarify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.EmptyState
import com.promptsaz.app.ui.components.GradientButton
import com.promptsaz.app.ui.components.NumberBadge
import com.promptsaz.app.ui.components.PillChip
import com.promptsaz.app.ui.components.StepProgress
import com.promptsaz.app.util.toPersianDigits

/**
 * Clarifying questions: a numbered, tappable checklist. A gradient progress
 * bar shows how much is answered; every question can be skipped, and improve
 * mode skips straight through.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClarifyScreen(
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit,
    viewModel: ClarifyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) onNavigateToResult()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "چند سؤال کوتاه", onBack = onBack)

        when {
            state.errorFa != null && state.questions.isEmpty() && !state.loading -> {
                EmptyState(
                    icon = Icons.Rounded.Warning,
                    title = "مشکلی پیش آمد",
                    hint = state.errorFa ?: "",
                )
                GradientButton(
                    text = "بازگشت",
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
            state.questions.isEmpty() -> {
                // Improve mode or no questions: go straight to generation.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (state.loading) "در حال ساخت پرامپت…" else "سؤالی لازم نیست؛ مستقیم می‌سازم",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.loading) {
                        CircularProgressIndicator()
                    }
                    state.errorFa?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    GradientButton(
                        text = if (state.loading) "لطفاً صبر کن…" else "بساز",
                        icon = Icons.Rounded.AutoAwesome,
                        enabled = !state.loading,
                        onClick = viewModel::generate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> {
                val answered = state.questions.count { it.id in state.answers.keys }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StepProgress(
                        current = answered,
                        total = state.questions.size,
                        label = "سؤال‌های پاسخ‌داده‌شده",
                    )
                    Text(
                        text = "هرچه دقیق‌تر جواب بدهی، پرامپت قوی‌تر می‌شود.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.questions.forEachIndexed { index, question ->
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    NumberBadge(number = (index + 1).toPersianDigits())
                                    Text(
                                        text = question.questionFa,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    question.chips.forEach { chip ->
                                        PillChip(
                                            label = chip.labelFa,
                                            selected = state.answers[question.id] == chip.labelFa,
                                            onClick = { viewModel.answer(question.id, chip.labelFa) },
                                        )
                                    }
                                }
                                if (question.allowFreeText) {
                                    OutlinedTextField(
                                        value = state.answers[question.id]?.takeIf {
                                            it !in question.chips.map { c -> c.labelFa }
                                        } ?: "",
                                        onValueChange = { viewModel.answer(question.id, it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        placeholder = { Text("یا خودت بنویس…") },
                                        singleLine = true,
                                    )
                                }
                                TextButton(onClick = { viewModel.skip(question.id) }) {
                                    Text(
                                        text = if (question.id !in state.answers.keys) "رد کردن" else "رد شد ✓",
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(84.dp))
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 12.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        state.errorFa?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = viewModel::skipAll, enabled = !state.loading) {
                                Text("رد کردن همه")
                            }
                            Button(
                                onClick = viewModel::generate,
                                enabled = !state.loading,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (state.loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text(if (state.loading) "در حال ساخت…" else "طراحی پرامپت")
                            }
                        }
                    }
                }
            }
        }
    }
}
