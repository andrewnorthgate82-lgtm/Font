package com.promptsaz.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AiServicePresets
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.ListGroup
import com.promptsaz.app.ui.components.RowChevron
import com.promptsaz.app.ui.components.SectionLabel
import com.promptsaz.app.ui.components.SegmentedControl
import com.promptsaz.app.ui.components.SelectChipRow
import com.promptsaz.app.ui.components.SettingsRow

/**
 * Settings — grouped inset lists (Apple style). Each section (گفتگو / تولید
 * پرامپت / تولید تصویر) binds its own AI service, key and model; services
 * management and about live below. Persian-first, minimal, no filler text.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var editorTarget by remember { mutableStateOf<AiService?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.keySavedNotice) {
        if (uiState.keySavedNotice) {
            snackbar.showSnackbar("کلید ذخیره شد ✓")
            viewModel.consumeKeyNotice()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "تنظیمات", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // --- ظاهر --------------------------------------------------------------
            SectionLabel("ظاهر")
            ListGroup {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("زمینهٔ برنامه", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    SegmentedControl(
                        options = ThemeMode.entries.map { it.id to it.labelFa },
                        selectedId = settings.themeMode.id,
                        onSelect = { id -> viewModel.setThemeMode(ThemeMode.fromId(id)) },
                    )
                }
            }

            // --- هر بخش: سرویس و مدل خودش -------------------------------------------
            SectionLabel("گفتگو")
            SectionAiBlock(
                modeId = AppSettings.MODE_CHAT,
                icon = Icons.Rounded.Forum,
                settings = settings,
                viewModel = viewModel,
                isImage = false,
            )

            SectionLabel("تولید پرامپت")
            SectionAiBlock(
                modeId = AppSettings.MODE_PROMPT,
                icon = Icons.Rounded.EditNote,
                settings = settings,
                viewModel = viewModel,
                isImage = false,
                header = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ساخت با هوش مصنوعی",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "خاموش = آفلاین",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.aiEnabled, onCheckedChange = viewModel::setAiEnabled)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                },
            )

            SectionLabel("تولید تصویر")
            SectionAiBlock(
                modeId = AppSettings.MODE_IMAGE,
                icon = Icons.Rounded.Image,
                settings = settings,
                viewModel = viewModel,
                isImage = true,
            )

            // --- پیش‌فرض‌های پرامپت ---------------------------------------------------
            SectionLabel("پیش‌فرض‌های پرامپت")
            ListGroup {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    PromptDefaultRow("حوزه", uiState.domains.map { it.id to it.nameFa }, settings.defaultDomainId, viewModel::setDefaultDomain)
                    PromptDefaultRow("مدل هدف", TargetAi.entries.map { it.id to it.labelFa }, settings.defaultTargetAi.id) { id ->
                        viewModel.setDefaultTarget(TargetAi.fromId(id))
                    }
                    PromptDefaultRow("زبان", OutputLanguage.entries.map { it.id to it.labelFa }, settings.defaultOutputLanguage.id) { id ->
                        viewModel.setDefaultLanguage(OutputLanguage.fromId(id))
                    }
                    PromptDefaultRow(
                        "سطح جزئیات",
                        DetailLevel.entries.map { it.id to it.labelFa },
                        settings.defaultDetailLevel.id,
                    ) { id -> viewModel.setDefaultDetail(DetailLevel.fromId(id)) }
                }
            }

            // --- سرویس‌ها -------------------------------------------------------------
            SectionLabel("سرویس‌ها")
            Text(
                text = "سرویس پیش‌فرض برای بخش‌های بدون انتخاب است.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.aiServices.isEmpty()) {
                ListGroup {
                    Text(
                        text = "هنوز سرویسی اضافه نشده.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                ListGroup {
                    Column {
                        settings.aiServices.forEachIndexed { index, service ->
                            ServiceRow(
                                service = service,
                                active = service.id == settings.activeService?.id,
                                onActivate = { viewModel.setActiveService(service.id) },
                                onEdit = {
                                    editorTarget = service
                                    editorOpen = true
                                },
                                onDelete = { viewModel.removeService(service.id) },
                            )
                            if (index != settings.aiServices.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
                onClick = {
                    editorTarget = null
                    editorOpen = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("افزودن سرویس", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            // --- درباره ----------------------------------------------------------------
            SectionLabel("درباره")
            ListGroup {
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    title = "درباره پرامپت‌ساز",
                    subtitle = "طراحی و اجرا: محسن ابوطالبیان",
                    onClick = onNavigateToAbout,
                    trailing = { RowChevron() },
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(hostState = snackbar)
    }

    if (editorOpen) {
        ServiceEditorDialog(
            initial = editorTarget,
            onDismiss = { editorOpen = false },
            onSave = { name, baseUrl, type ->
                val target = editorTarget
                if (target == null) {
                    viewModel.addService(name, baseUrl, type)
                } else {
                    viewModel.updateService(target.id, name, baseUrl, type)
                }
                editorOpen = false
            },
        )
    }
}

/** One labeled chip row inside the پیش‌فرض‌ها group. */
@Composable
private fun PromptDefaultRow(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectChipRow(options = options, selectedId = selectedId, onSelect = onSelect)
    }
}

/**
 * One section's AI block: service chips, the service's key, its model and a
 * connection test — everything scoped to this mode.
 */
@Composable
private fun SectionAiBlock(
    modeId: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    settings: AppSettings,
    viewModel: SettingsViewModel,
    isImage: Boolean,
    header: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val service = settings.serviceFor(modeId)
    val models = uiState.modelsByMode[modeId].orEmpty()
    val loading = uiState.loadingByMode[modeId] == true
    val testResult = uiState.testByMode[modeId]

    ListGroup {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            header?.invoke()

            // --- service binding ---------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "سرویس " + if (isImage) "تصویر" else if (modeId == AppSettings.MODE_CHAT) "گفتگو" else "پرامپت",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                service?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                settings.aiServices.forEach { candidate ->
                    val selected = candidate.id == service?.id
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setModeService(modeId, candidate.id) },
                        label = { Text(candidate.name, maxLines = 1) },
                    )
                }
            }

            if (service != null) {
                AiServicePresets.matchOf(service)?.consoleUrl?.let { consoleUrl ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openInBrowser(context, consoleUrl) }
                            .padding(vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "ساخت یا مدیریت کلید این سرویس ↗",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // --- key -------------------------------------------------------
                var keyInput by rememberSaveable(service.id) { mutableStateOf("") }
                var keyVisible by rememberSaveable { mutableStateOf(false) }
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("کلید API") },
                    placeholder = { Text("AIza… / cc_… / sk-…") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (keyVisible) "پنهان" else "نمایش",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.hasKey(service.id)) {
                    Text(
                        text = "ذخیره‌شده: ${uiState.maskedKey(service.id) ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    Button(
                        onClick = { viewModel.saveApiKey(service.id, keyInput); keyInput = "" },
                        enabled = keyInput.isNotBlank(),
                    ) { Text("ذخیره کلید") }
                    if (uiState.hasKey(service.id)) {
                        OutlinedButton(onClick = { viewModel.clearApiKey(service.id) }) { Text("حذف") }
                    }
                }

                // --- model --------------------------------------------------------
                var modelInput by rememberSaveable(service.id, if (isImage) service.imageModel else service.model) {
                    mutableStateOf(if (isImage) service.imageModel else service.model)
                }
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text(if (isImage) "مدل تصویر" else "مدل") },
                    placeholder = { Text("مثلاً gemini-flash-latest") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { viewModel.setServiceModel(service.id, modelInput.trim(), imageModel = isImage) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .onFocusChanged { focus ->
                            val current = if (isImage) service.imageModel else service.model
                            if (!focus.isFocused && modelInput.trim() != current) {
                                viewModel.setServiceModel(service.id, modelInput.trim(), imageModel = isImage)
                            }
                        },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    OutlinedButton(onClick = { viewModel.loadModels(modeId) }, enabled = !loading) {
                        Text("مدل‌ها")
                    }
                    OutlinedButton(onClick = { viewModel.testConnection(modeId) }, enabled = !loading) {
                        Text("تست اتصال")
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .height(18.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                if (models.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        models.take(40).forEach { model ->
                            FilterChip(
                                selected = (if (isImage) service.imageModel else service.model) == model,
                                onClick = { viewModel.setServiceModel(service.id, model, imageModel = isImage) },
                                label = { Text(model) },
                            )
                        }
                    }
                }
                testResult?.let { (ok, message) ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                Text(
                    text = "اول یک سرویس اضافه کن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** One service in the services group: tap = default, edit / delete aside. */
@Composable
private fun ServiceRow(
    service: AiService,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActivate)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Cloud,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (active) "${service.name} (پیش‌فرض)" else service.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = service.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Rounded.Edit,
            contentDescription = "ویرایش",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onEdit),
        )
        Icon(
            Icons.Rounded.Delete,
            contentDescription = "حذف",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onDelete),
        )
    }
}

/**
 * Add/edit a service: pick a ready preset (Gemini, CodeCraft, OpenAI, …) and
 * everything auto-fills — the user only pastes a key later.
 */
@Composable
private fun ServiceEditorDialog(
    initial: AiService?,
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, type: String) -> Unit,
) {
    var presetId by rememberSaveable {
        mutableStateOf(initial?.let { AiServicePresets.matchOf(it)?.id } ?: "")
    }
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var baseUrl by rememberSaveable { mutableStateOf(initial?.baseUrl ?: "") }
    val preset = AiServicePresets.ALL.firstOrNull { it.id == presetId }
    val type = preset?.type ?: initial?.type ?: AiService.TYPE_OPENAI_COMPATIBLE
    val urlValid = baseUrl.trim().startsWith("http") || baseUrl.trim().contains('.')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "افزودن سرویس" else "ویرایش سرویس") },
        text = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    AiServicePresets.ALL.forEach { candidate ->
                        FilterChip(
                            selected = candidate.id == presetId,
                            onClick = {
                                presetId = candidate.id
                                name = candidate.nameFa
                                baseUrl = candidate.baseUrl
                            },
                            label = { Text(candidate.nameFa, maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام سرویس") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("نشانی سرور (Base URL)") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = when {
                                preset == null -> "سرویس‌های سازگار با OpenAI معمولاً با /v1 تمام می‌شوند."
                                preset.id == "gemini" -> "نشانی پیش‌فرض را نگه دار."
                                else -> preset.keyHintFa
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (preset != null && preset.id != AiServicePresets.CUSTOM_ID) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = preset.keyHintFa,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, baseUrl, type) },
                enabled = name.isNotBlank() && urlValid,
            ) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}

/** Opens the service's key console; silent when no browser is installed. */
private fun openInBrowser(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
