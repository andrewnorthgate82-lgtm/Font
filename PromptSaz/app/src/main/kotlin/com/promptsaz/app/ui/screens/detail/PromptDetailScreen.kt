package com.promptsaz.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.ConfirmDialog
import com.promptsaz.app.ui.components.EmptyState
import com.promptsaz.app.ui.components.SectionLabel
import com.promptsaz.app.util.JalaliCalendar
import com.promptsaz.app.util.PlatformUtils
import com.promptsaz.app.util.toPersianDigits

/** Full view of one archived prompt with edit / copy / share / duplicate / delete. */
@Composable
fun PromptDetailScreen(
    onBack: () -> Unit,
    viewModel: PromptDetailViewModel = hiltViewModel(),
) {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val domains by viewModel.domains.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var editOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    val current = prompt
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(title = "جزئیات پرامپت", onBack = onBack)
            EmptyState(
                icon = Icons.Rounded.Delete,
                title = "پرامپت پیدا نشد",
                hint = "شاید حذف شده باشد",
            )
            SnackbarHost(hostState = snackbar)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "جزئیات پرامپت",
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (current.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "علاقه‌مندی",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                IconButton(onClick = { editOpen = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "ویرایش")
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = current.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val domainName = domains.firstOrNull { it.id == current.domainId }?.nameFa ?: "عمومی"
                Text(domainName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("•", style = MaterialTheme.typography.labelMedium)
                Text(current.targetAi.labelFa, style = MaterialTheme.typography.labelMedium)
                Text("•", style = MaterialTheme.typography.labelMedium)
                Text("امتیاز ${current.score.toPersianDigits()}", style = MaterialTheme.typography.labelMedium)
                if (current.isAiGenerated) {
                    Text("• با هوش مصنوعی", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = "ساخته‌شده در ${JalaliCalendar.formatLong(JalaliCalendar.fromEpochMillis(current.createdAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            SectionLabel("پرامپت")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = current.promptText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                    )
                }
            }

            SectionLabel("ایده اصلی")
            Text(
                text = current.originalIdea,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (current.tags.isNotEmpty()) {
                SectionLabel("برچسب‌ها")
                Text(current.tags.joinToString("، "), style = MaterialTheme.typography.bodyMedium)
            }
            if (current.notes.isNotBlank()) {
                SectionLabel("یادداشت")
                Text(current.notes, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { PlatformUtils.copyWithFeedback(context, current.promptText) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("کپی", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { PlatformUtils.shareText(context, current.promptText) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Text("اشتراک", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = viewModel::duplicate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileCopy, contentDescription = null)
                    Text("ساخت کپی", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { deleteOpen = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("حذف", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        SnackbarHost(hostState = snackbar)
    }

    if (editOpen && current != null) {
        EditPromptDialog(
            initialTitle = current.title,
            initialTags = current.tags.joinToString("، "),
            initialNotes = current.notes,
            onSave = { title, tags, notes ->
                viewModel.saveEdits(title, tags, notes)
                editOpen = false
            },
            onDismiss = { editOpen = false },
        )
    }
    if (deleteOpen) {
        ConfirmDialog(
            title = "حذف پرامپت",
            text = "«${current.title}» برای همیشه حذف می‌شود. مطمئنی؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                deleteOpen = false
                viewModel.delete()
            },
            onDismiss = { deleteOpen = false },
        )
    }
}

@Composable
private fun EditPromptDialog(
    initialTitle: String,
    initialTags: String,
    initialNotes: String,
    onSave: (title: String, tags: List<String>, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var tags by remember { mutableStateOf(initialTags) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش پرامپت") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("برچسب‌ها (با «،» جدا کن)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("یادداشت") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, tags.split("،", ","), notes) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
