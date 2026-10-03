package com.promptsaz.app.ui.screens.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.promptsaz.app.util.PlatformUtils
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.ImageGeneration
import com.promptsaz.app.ui.components.GradientButton
import com.promptsaz.app.ui.components.ModelPickerSheet
import com.promptsaz.app.ui.components.SoftIconButton
import java.io.File

/**
 * تصویر tab — the image studio: describe → generate → view/save/share,
 * with a model picker and a thumbnail history. Minimal and Persian-first.
 */
@Composable
fun ImageStudioScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ImageStudioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var saveMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
                        .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // --- header --------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "تولید تصویر",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = viewModel::openModelPicker,
            ) {
                Text(
                    text = state.selectedModel.ifBlank { "انتخاب مدل" },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // --- setup banner --------------------------------------------------
        if (state.needsSetup) {
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (!state.hasKey) {
                            "برای ساخت تصویر، کلید API را در تنظیمات وارد کن."
                        } else {
                            "یک مدل ساخت تصویر انتخاب کن."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = { if (!state.hasKey) onNavigateToSettings() else viewModel.openModelPicker() },
                    ) {
                        Text(
                            text = if (!state.hasKey) "تنظیمات" else "انتخاب مدل",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // --- prompt --------------------------------------------------------
        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            placeholder = { Text("توصیف تصویر دلخواهت… مثلاً: گربهٔ نارنجی روی کاناپهٔ مخملی، نور غروب") },
            shape = RoundedCornerShape(16.dp),
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        // --- size selector -------------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                ImageStudioViewModel.UiState.SIZE_SQUARE to "مربع ۱:۱",
                ImageStudioViewModel.UiState.SIZE_PORTRAIT to "عمودی",
                ImageStudioViewModel.UiState.SIZE_LANDSCAPE to "افقی",
            ).forEach { (size, label) ->
                val selected = state.size == size
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    onClick = { viewModel.selectSize(size) },
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // --- error ---------------------------------------------------------
        state.errorFa?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "بستن خطا",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { viewModel.dismissError() },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // --- generate ------------------------------------------------------
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.generating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(20.dp))
                    Text(
                        "در حال ساخت تصویر…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                GradientButton(
                    text = "ساخت تصویر",
                    onClick = viewModel::generate,
                    enabled = state.canGenerate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // --- image-prompt fallback (services without image generation) -----
        if (state.imageUnsupported) {
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = "این سرویس یا مدل، عکس نمی‌سازد",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "به‌جای عکس، می‌توانی از همین توصیف یک «پرامپت تصویر» حرفه‌ای (انگلیسی) بسازی " +
                            "و آن را در Midjourney، DALL·E یا هر ابزار ساخت عکس دیگری پیست کنی.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (state.imagePromptLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(18.dp))
                            Text(
                                text = "در حال نوشتن پرامپت تصویر…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        GradientButton(
                            text = "ساخت پرامپت تصویر",
                            onClick = viewModel::generateImagePrompt,
                            enabled = state.canMakeImagePrompt,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        state.imagePrompt?.let { suggestion ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "پرامپت تصویر (برای Midjourney و DALL·E)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "بستن پرامپت",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { viewModel.dismissImagePrompt() },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionChip(
                        icon = Icons.Rounded.ContentCopy,
                        label = "کپی پرامپت",
                        onClick = { PlatformUtils.copyWithFeedback(context, suggestion) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // --- result --------------------------------------------------------
        state.current?.let { current ->
            val bitmap = remember(current.fileName) {
                viewModel.readImage(current.fileName)?.let {
                    runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "تصویر ساخته‌شده",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip(
                        icon = Icons.Rounded.SaveAlt,
                        label = "ذخیره در گالری",
                        onClick = {
                            val bytes = viewModel.readImage(current.fileName)
                            if (bytes != null) {
                                saveMessage = saveToGallery(context, bytes, current.fileName)
                            }
                        },
                    )
                    ActionChip(
                        icon = Icons.Rounded.IosShare,
                        label = "اشتراک‌گذاری",
                        onClick = { shareImage(context, current, viewModel) },
                    )
                }
                saveMessage?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "«${current.prompt}» — ${current.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        // --- history -------------------------------------------------------
        if (state.history.isNotEmpty()) {
            Text(
                text = "ساخت‌های اخیر",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.history, key = { it.id }) { generation ->
                    HistoryThumb(
                        generation = generation,
                        viewModel = viewModel,
                        selected = state.current?.id == generation.id,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
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

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun HistoryThumb(
    generation: ImageGeneration,
    viewModel: ImageStudioViewModel,
    selected: Boolean,
) {
    val bitmap = remember(generation.fileName) {
        viewModel.readImage(generation.fileName)?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = generation.prompt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.openFromHistory(generation) },
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                )
            }
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "حذف از تاریخچه",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(2.dp)
                    .clickable { viewModel.deleteFromHistory(generation) },
            )
        }
    }
}

/** Saves into the system gallery (MediaStore, API 29+); below that, hints to share. */
private fun saveToGallery(context: Context, bytes: ByteArray, fileName: String): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return "برای ذخیره، از دکمهٔ اشتراک‌گذاری استفاده کن."
    }
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "promptsaz-$fileName")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PromptSaz")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "ذخیره ناموفق بود."
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        "در گالری، پوشهٔ PromptSaz ذخیره شد."
    }.getOrElse { "ذخیره ناموفق بود: ${it.message ?: ""}" }
}

/** Shares the current image via the system share sheet. */
private fun shareImage(context: Context, generation: ImageGeneration, viewModel: ImageStudioViewModel) {
    runCatching {
        val bytes = viewModel.readImage(generation.fileName) ?: return
        val file = File(context.cacheDir, "share-${generation.fileName}")
        file.writeBytes(bytes)
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری تصویر"))
    }
}
