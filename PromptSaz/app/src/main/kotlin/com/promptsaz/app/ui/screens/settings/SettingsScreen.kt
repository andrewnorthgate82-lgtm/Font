package com.promptsaz.app.ui.screens.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AiServicePresets
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.SectionLabel
import com.promptsaz.app.ui.components.SelectChipRow

/** Settings: theme, generation defaults, AI services (multi key/model), about. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val activeService = settings.activeService

    // service editor dialog state (null = closed; service = editing, null = new)
    var editorTarget by remember { mutableStateOf<AiService?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.testResult) {
        uiState.testResult?.let {
            snackbar.showSnackbar(it.second)
            viewModel.consumeTestResult()
        }
    }
    LaunchedEffect(uiState.keySavedNotice) {
        if (uiState.keySavedNotice) {
            snackbar.showSnackbar("کلید API ذخیره شد ✓ (فقط روی همین گوشی و رمزنگاری‌شده)")
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
            SectionLabel("ظاهر")
            SelectChipRow(
                options = ThemeMode.entries.map { it.id to it.labelFa },
                selectedId = settings.themeMode.id,
                onSelect = { id -> viewModel.setThemeMode(ThemeMode.fromId(id)) },
            )

            SectionLabel("پیش‌فرض‌های ساخت پرامپت")
            Text("حوزه", style = MaterialTheme.typography.labelMedium)
            SelectChipRow(
                options = uiState.domains.map { it.id to it.nameFa },
                selectedId = settings.defaultDomainId,
                onSelect = viewModel::setDefaultDomain,
            )
            Text("مدل هدف", style = MaterialTheme.typography.labelMedium)
            SelectChipRow(
                options = TargetAi.entries.map { it.id to it.labelFa },
                selectedId = settings.defaultTargetAi.id,
                onSelect = { id -> viewModel.setDefaultTarget(TargetAi.fromId(id)) },
            )
            Text("زبان پرامپت", style = MaterialTheme.typography.labelMedium)
            SelectChipRow(
                options = OutputLanguage.entries.map { it.id to it.labelFa },
                selectedId = settings.defaultOutputLanguage.id,
                onSelect = { id -> viewModel.setDefaultLanguage(OutputLanguage.fromId(id)) },
            )
            Text("سطح جزئیات", style = MaterialTheme.typography.labelMedium)
            SelectChipRow(
                options = DetailLevel.entries.map { it.id to it.labelFa },
                selectedId = settings.defaultDetailLevel.id,
                onSelect = { id -> viewModel.setDefaultDetail(DetailLevel.fromId(id)) },
            )

            SectionLabel("حالت هوش مصنوعی (اختیاری)")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ساخت پرامپت با هوش مصنوعی",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "خاموش = کاملاً آفلاین و بدون نیاز به کلید (پیش‌فرض)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.aiEnabled, onCheckedChange = viewModel::setAiEnabled)
                    }

                    if (settings.aiEnabled) {
                        Text(
                            text = "توجه: در این حالت، ایده و تنظیمات شما برای ساخت پرامپت به سرور شخص ثالثی که نشانی‌اش را وارد کرده‌ای ارسال می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    // --- services list --------------------------------------------
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "سرویس‌های هوش مصنوعی من",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "می‌توانی چند سرویس (مثلاً CodeCraft و OpenAI و OpenRouter) کنار هم نگه داری؛ با لمس هرکدام، آن سرویس فعال می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )

                    settings.aiServices.forEach { service ->
                        ServiceRow(
                            service = service,
                            active = service.id == activeService?.id,
                            onActivate = { viewModel.setActiveService(service.id) },
                            onEdit = {
                                editorTarget = service
                                editorOpen = true
                            },
                            onDelete = { viewModel.removeService(service.id) },
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            editorTarget = null
                            editorOpen = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("افزودن سرویس جدید")
                    }

                    // --- active service config ------------------------------------
                    if (activeService != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "تنظیمات سرویس فعال: ${activeService.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = activeService.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(8.dp))
                        var keyInput by rememberSaveable(activeService.id) { mutableStateOf("") }
                        var keyVisible by rememberSaveable { mutableStateOf(false) }
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("کلید API این سرویس") },
                            placeholder = { Text("AIza… / cc_… / sk-…") },
                            singleLine = true,
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (keyVisible) "پنهان کردن کلید" else "نمایش کلید",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "کلید هر سرویس جداگانه و فقط روی همین گوشی، رمزنگاری‌شده نگه داشته می‌شود و هرگز جایی دیگر ذخیره یا ارسال نمی‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { viewModel.saveApiKey(keyInput); keyInput = "" },
                                enabled = keyInput.isNotBlank(),
                            ) { Text("ذخیره کلید") }
                            if (uiState.hasKey) {
                                OutlinedButton(onClick = viewModel::clearApiKey) { Text("حذف کلید") }
                            }
                        }
                        if (uiState.hasKey) {
                            Text(
                                text = "کلید ذخیره‌شده: ${uiState.maskedKey ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        // local edit state + commit on Done / focus loss — typing never
                        // fights the async settings round-trip (the old base-URL bug)
                        var modelInput by rememberSaveable(activeService.id, activeService.model) {
                            mutableStateOf(activeService.model)
                        }
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("نام مدل این سرویس") },
                            placeholder = { Text("مثلاً gpt-4o-mini") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.setAiModel(modelInput.trim()) },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focus ->
                                    if (!focus.isFocused && modelInput.trim() != activeService.model) {
                                        viewModel.setAiModel(modelInput.trim())
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
                            OutlinedButton(onClick = viewModel::loadModels, enabled = !uiState.modelsLoading) {
                                Text("دریافت فهرست مدل‌ها")
                            }
                            OutlinedButton(onClick = viewModel::testConnection, enabled = !uiState.testing) {
                                Text("تست اتصال")
                            }
                            if (uiState.modelsLoading || uiState.testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp).padding(start = 4.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                        Text(
                            text = "اگر فهرست مدل‌ها دریافت نشد، نام مدل را دستی همان کادر بالا بنویس.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (uiState.models.isNotEmpty()) {
                            Text(
                                text = "مدل پیدا شد؛ یکی را انتخاب کن:",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                uiState.models.take(40).forEach { model ->
                                    FilterChip(
                                        selected = activeService.model == model,
                                        onClick = { viewModel.setAiModel(model) },
                                        label = { Text(model) },
                                    )
                                }
                            }
                        }
                        uiState.testResult?.let { (ok, message) ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (ok) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ok) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel("درباره پرامپت‌ساز")
            Text(
                text = "پرامپت‌ساز نسخه ۱.۰.۰ — ایده خام تو را به پرامپت حرفه‌ای و ساخت‌یافته تبدیل می‌کند. موتور آفلاین بدون اینترنت کار می‌کند؛ حالت هوش مصنوعی اختیاری است. فونت وزیرمتن (OFL) استفاده شده است.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )
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

/** One row of the services list: tap = activate, edit / delete icons on the side. */
@Composable
private fun ServiceRow(
    service: AiService,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clickable(onClick = onActivate)
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    text = service.name + if (active) "  (فعال)" else "",
                    style = MaterialTheme.typography.bodyMedium,
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
                contentDescription = "ویرایش سرویس",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onEdit),
            )
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "حذف سرویس",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete),
            )
        }
    }
}

/**
 * Add/edit dialog for a service — pick a ready preset (Gemini, CodeCraft,
 * OpenAI, …) and only paste the key later; everything else auto-fills.
 * Local state, saved with one explicit button.
 */
@Composable
private fun ServiceEditorDialog(
    initial: AiService?,
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, type: String) -> Unit,
) {
    // start from the preset matching the edited service (custom when unknown)
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
        title = { Text(if (initial == null) "افزودن سرویس جدید" else "ویرایش سرویس") },
        text = {
            Column {
                Text(
                    text = "سرویس‌ات را انتخاب کن؛ نشانی و تنظیمات خودکار پر می‌شود:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
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
                                preset == null ->
                                    "سرویس‌های سازگار با OpenAI معمولاً با /v1 تمام می‌شوند."
                                preset.id == "gemini" ->
                                    "همین نشانی پیش‌فرض گوگل را نگه دار؛ کلید را بعد از ذخیره در کادر «کلید API این سرویس» وارد کن."
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
