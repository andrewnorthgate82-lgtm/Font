package com.promptsaz.app.ui.screens.kb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.EmptyState
import com.promptsaz.app.ui.components.SectionLabel

/** One knowledge base: personas, terminology, structures, guardrails, examples. */
@Composable
fun KbDomainScreen(
    onBack: () -> Unit,
    viewModel: KbDomainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = state.entry?.nameFa ?: "حوزه", onBack = onBack)

        when {
            state.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }
            state.knowledge == null -> {
                EmptyState(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    title = "به‌زودی",
                    hint = "دانش‌نامه این حوزه هنوز کامل نشده است",
                )
            }
            else -> {
                val kb = checkNotNull(state.knowledge)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = state.entry?.descriptionFa ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    SectionLabel("پیشنهاد نقش‌های متخصص")
                    kb.personas.forEach { persona ->
                        TitledCard(title = persona.titleFa, body = persona.expertiseFa, hint = persona.whenToUseFa)
                    }

                    SectionLabel("واژگان کلیدی")
                    kb.terminology.forEach { term ->
                        TitledCard(
                            title = "${term.termFa} (${term.termEn})",
                            body = term.definitionFa,
                            hint = null,
                        )
                    }

                    SectionLabel("ساختارهای استاندارد خروجی")
                    kb.outputStructures.forEach { structure ->
                        TitledCard(title = structure.titleFa, body = structure.templateFa, hint = structure.descriptionFa)
                    }

                    SectionLabel("نگه‌داشت‌ها")
                    kb.guardrails.forEach { guardrail ->
                        TitledCard(
                            title = guardrail.doFa,
                            body = guardrail.dontFa,
                            hint = guardrail.whyFa,
                        )
                    }

                    SectionLabel("خطاهای رایج و درمانشان")
                    kb.failureModes.forEach { mode ->
                        TitledCard(title = mode.symptomFa, body = mode.fixFa, hint = null)
                    }

                    SectionLabel("پرامپت‌های نمونه")
                    kb.examples.forEach { example ->
                        ExpandableExample(title = example.titleFa, body = example.promptFa)
                    }

                    Text(
                        text = "این محتوا از فایل JSON دانشنامه خوانده می‌شود و بدون تغییر کد قابل گسترش است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TitledCard(title: String, body: String, hint: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ExpandableExample(title: String, body: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
