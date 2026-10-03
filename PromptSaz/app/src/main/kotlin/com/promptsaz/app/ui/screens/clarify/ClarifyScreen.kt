package com.promptsaz.app.ui.screens.clarify

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.EmptyState
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.ui.session.GenerationSession
import javax.inject.Inject

/**
 * Clarifying questions: tap-to-answer chips where possible, optional free
 * text, per-question and global skip. Improve mode skips straight through.
 */
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
                EmptyState(icon = androidx.compose.material.icons.Icons.Rounded.Warning, title = "مشکلی پیش آمد", hint = state.errorFa ?: "")
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) { Text("بازگشت") }
            }
            state.questions.isEmpty() -> {
                // Improve mode or no questions: go straight to generation.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (state.loading) "در حال ساخت پرامپت…" else "سؤالی لازم نیست؛ مستقیم می‌سازم",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.loading) {
                        CircularProgressIndicator()
                    }
                    state.errorFa?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = viewModel::generate,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.loading) "لطفاً صبر کن…" else "بساز") }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "هرچه دقیق‌تر جواب بدهی، پرامپت قوی‌تر می‌شود.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.questions.forEach { question ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = question.questionFa,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    question.chips.forEach { chip ->
                                        FilterChip(
                                            selected = state.answers[question.id] == chip.labelFa,
                                            onClick = { viewModel.answer(question.id, chip.labelFa) },
                                            label = { Text(chip.labelFa) },
                                        )
                                    }
                                }
                                if (question.allowFreeText) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = state.answers[question.id]?.takeIf { it !in question.chips.map { c -> c.labelFa } } ?: "",
                                        onValueChange = { viewModel.answer(question.id, it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("یا خودت بنویس…") },
                                        singleLine = true,
                                    )
                                }
                                TextButton(onClick = { viewModel.skip(question.id) }) {
                                    Text(
                                        text = if (state.answers[question.id] == null) "رد کردن" else "رد شد ✓",
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(84.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    state.errorFa?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
