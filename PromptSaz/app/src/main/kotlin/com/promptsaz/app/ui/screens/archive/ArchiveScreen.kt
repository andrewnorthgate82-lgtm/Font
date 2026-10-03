package com.promptsaz.app.ui.screens.archive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.ConfirmDialog
import com.promptsaz.app.ui.components.EmptyState
import com.promptsaz.app.util.PlatformUtils
import com.promptsaz.app.util.TimeAgo
import com.promptsaz.app.util.toPersianDigits

/** Archive: search, filters, favorites, quick actions, export/import (SAF). */
@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    onOpenPrompt: (Long) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val domains by viewModel.domains.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<ArchivedPrompt?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportTo(context.contentResolver, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importFrom(context.contentResolver, it) } }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "آرشیو پرامپت‌ها",
            onBack = onBack,
            actions = {
                IconButton(onClick = { exportLauncher.launch("promptsaz-archive.json") }) {
                    Icon(Icons.Rounded.Download, contentDescription = "خروجی گرفتن")
                }
                IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Icon(Icons.Rounded.Upload, contentDescription = "وارد کردن")
                }
            },
        )

        OutlinedTextField(
            value = filters.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("جست‌وجو در عنوان، متن و برچسب‌ها…") },
            singleLine = true,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filters.favoritesOnly,
                onClick = viewModel::toggleFavoritesOnly,
                label = { Text("علاقه‌مندی‌ها") },
            )
            FilterChip(
                selected = filters.domainId == null,
                onClick = { viewModel.setDomain(null) },
                label = { Text("همه حوزه‌ها") },
            )
            domains.forEach { domain ->
                FilterChip(
                    selected = filters.domainId == domain.id,
                    onClick = { viewModel.setDomain(domain.id) },
                    label = { Text(domain.nameFa) },
                )
            }
        }

        if (prompts.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.StarBorder,
                title = if (filters.query.isBlank() && filters.domainId == null && !filters.favoritesOnly) {
                    "آرشیو خالی است"
                } else {
                    "چیزی پیدا نشد"
                },
                hint = "هر پرامپتی که بسازی، خودکار این‌جا ذخیره می‌شود",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(prompts, key = { it.id }) { prompt ->
                    ArchiveCard(
                        prompt = prompt,
                        domainName = domains.firstOrNull { it.id == prompt.domainId }?.nameFa,
                        onOpen = { onOpenPrompt(prompt.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(prompt.id) },
                        onCopy = { PlatformUtils.copyWithFeedback(context, prompt.promptText) },
                        onDuplicate = { viewModel.duplicate(prompt.id) },
                        onDelete = { deleteTarget = prompt },
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbar)
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "حذف پرامپت",
            text = "«${target.title}» برای همیشه حذف می‌شود. مطمئنی؟",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                viewModel.delete(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ArchiveCard(
    prompt: ArchivedPrompt,
    domainName: String?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (prompt.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (prompt.isFavorite) "حذف از علاقه‌مندی‌ها" else "افزودن به علاقه‌مندی‌ها",
                        tint = if (prompt.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = domainName ?: "عمومی",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("•", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = prompt.targetAi.labelFa,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("•", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "امتیاز ${prompt.score.toPersianDigits()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (prompt.isAiGenerated) {
                    Text("• هوش مصنوعی", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = prompt.promptText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = TimeAgo.format(prompt.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(modifier = Modifier.align(Alignment.End)) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "کپی", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Rounded.FileCopy,
                        contentDescription = "ساخت کپی",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
