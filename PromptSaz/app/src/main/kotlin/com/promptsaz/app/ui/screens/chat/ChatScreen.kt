package com.promptsaz.app.ui.screens.chat

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.ChatMessage
import com.promptsaz.app.ui.components.ModelPickerSheet
import com.promptsaz.app.ui.components.SoftIconButton
import com.promptsaz.app.ui.theme.BrandGradient
import kotlinx.coroutines.launch

/**
 * گفتگوی جدید — the ChatGPT-style chat tab: a conversations drawer, a clean
 * message list, an input bar with image attachment (vision) and an in-chat
 * model picker. Minimal, Persian, on the app's design system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(ModalDrawerValue.Closed)

    var input by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    val listState = rememberLazyListState()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val extension = context.contentResolver.getType(uri)
                        ?.substringAfterLast('/')
                        ?.takeIf { it in setOf("jpeg", "jpg", "png", "webp") }
                        ?: "jpg"
                    pendingImage = bytes to extension
                }
            }
        }
    }

    // auto-scroll to the newest message
    LaunchedEffect(state.messages.size, state.sending) {
        if (state.messages.isNotEmpty() || state.sending) {
            listState.animateScrollToItem((state.messages.size + if (state.sending) 1 else 0) - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { ConversationsDrawer(state, viewModel, drawerState, onNavigateToSettings) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                                .imePadding(),
        ) {
            // --- top bar -----------------------------------------------------
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    SoftIconButton(
                        icon = Icons.Rounded.Menu,
                        contentDescription = "فهرست گفتگوها",
                        onClick = { scope.launch { drawerState.open() } },
                    )
                    Text(
                        text = "گفتگو",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    // model chip → picker sheet
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
                                .widthIn(max = 140.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    SoftIconButton(
                        icon = Icons.Rounded.AddCircle,
                        contentDescription = "گفتگوی جدید",
                        onClick = viewModel::newConversation,
                    )
                }
            }

            // --- setup banner ------------------------------------------------
            if (state.needsSetup) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = if (!state.hasKey) {
                                "برای شروع گفتگو، کلید API را در تنظیمات وارد کن."
                            } else {
                                "یک مدل انتخاب کن تا گفتگو شروع شود."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiary,
                            onClick = {
                                if (!state.hasKey) onNavigateToSettings() else viewModel.openModelPicker()
                            },
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
            }

            // --- error banner ------------------------------------------------
            state.errorFa?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
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
                                .clickable(viewModel::dismissError),
                        )
                    }
                }
            }

            // --- messages ----------------------------------------------------
            if (state.messages.isEmpty() && !state.sending) {
                EmptyChat(
                    onSuggestion = { suggestion -> input = suggestion },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            readImage = viewModel::readImageFile,
                        )
                    }
                    if (state.sending) {
                        item(key = "typing") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "در حال نوشتن…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- input bar ---------------------------------------------------
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    pendingImage?.let { (bytes, extension) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            val bitmap = remember(bytes) {
                                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "تصویر پیوست‌شده",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            }
                            Text(
                                text = "تصویر پیوست می‌شود",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "حذف تصویر",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { pendingImage = null },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        SoftIconButton(
                            icon = Icons.Rounded.Image,
                            contentDescription = "پیوست تصویر",
                            onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("پیامت را بنویس…") },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            maxLines = 4,
                            enabled = !state.sending,
                            modifier = Modifier.weight(1f),
                        )
                        val canSend = (input.isNotBlank() || pendingImage != null) && !state.sending
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent,
                            onClick = {
                                if (canSend) {
                                    viewModel.send(input, pendingImage?.first, pendingImage?.second)
                                    input = ""
                                    pendingImage = null
                                }
                            },
                            enabled = canSend,
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(if (canSend) BrandGradient else MaterialTheme.colorScheme.surfaceVariant)
                                    .size(width = 52.dp, height = 52.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "ارسال",
                                    tint = Color.White,
                                )
                            }
                        }
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

@Composable
private fun EmptyChat(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "گفتگوی جدید",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "هر سوالی داری بپرس؛ تصویر هم می‌توانی پیوست کنی تا تحلیل شود.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "این تصویر را دقیق توصیف و تحلیل کن",
                "یک ایمیل رسمی برای درخواست جلسه بنویس",
                "این متن را ساده‌تر و روان‌تر بازنویسی کن",
            ).forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    onClick = { onSuggestion(suggestion) },
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, readImage: (String) -> ByteArray?) {
    val fromUser = message.isFromUser
    Row(
        horizontalArrangement = if (fromUser) Arrangement.Start else Arrangement.End,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(horizontalAlignment = if (fromUser) Alignment.Start else Alignment.End) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (fromUser) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                },
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    message.imageFileName?.let { fileName ->
                        val bitmap = remember(fileName) {
                            readImage(fileName)?.let {
                                runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
                            }
                        }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "تصویر پیوست‌شده",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationsDrawer(
    state: ChatViewModel.UiState,
    viewModel: ChatViewModel,
    drawerState: androidx.compose.material3.DrawerState,
    onNavigateToSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = "گفتگوها",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary,
            onClick = {
                viewModel.newConversation()
                scope.launch { drawerState.close() }
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = Color.White)
                Text("گفتگوی جدید", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.conversations.isEmpty()) {
            Text(
                "هنوز گفتگویی نداری.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.conversations, key = { it.id }) { conversation ->
                    val active = conversation.id == state.conversationId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        onClick = {
                            viewModel.openConversation(conversation.id)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "حذف گفتگو",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.deleteConversation(conversation.id) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            onClick = onNavigateToSettings,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("تنظیمات", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
