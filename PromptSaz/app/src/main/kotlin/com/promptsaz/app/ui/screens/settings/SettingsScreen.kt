package com.promptsaz.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.promptsaz.app.ui.components.ConfirmDialog
import com.promptsaz.app.ui.components.ListGroup
import com.promptsaz.app.ui.components.ModelPickerSheet
import com.promptsaz.app.ui.components.RowChevron
import com.promptsaz.app.ui.components.SectionLabel
import com.promptsaz.app.ui.components.SelectChipRow
import com.promptsaz.app.ui.components.SettingsRow
import com.promptsaz.app.ui.components.SoftIconButton
import com.promptsaz.app.ui.theme.BrandGradient

/**
 * Settings, redesigned as TABS + POPUPS:
 *  ┌ گفتگو ┬ پرامپت ┬ تصویر ┬ سرویس‌ها ┐   ← one tab per section
 *  هر بخش سرویس و کلید و مدل مخصوص خودش را دارد — هیچ «سرویس پیش‌فرضی» وجود
 *  ندارد. Secondary actions (سرویس، کلید، مدل، ظاهر) همگی پاپ‌آپ هستند تا هر
 *  تب فقط کارهای همان بخش را نشان دهد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var themeDialogOpen by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<AiService?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AiService?>(null) }
    /** popups of the section tabs, keyed by mode id. */
    var serviceSheetMode by remember { mutableStateOf<String?>(null) }
    var modelSheetMode by remember { mutableStateOf<String?>(null) }
    var keyDialogService by remember { mutableStateOf<AiService?>(null) }

    val tabTitles = listOf("گفتگو", "پرامپت", "تصویر", "سرویس‌ها")

    LaunchedEffect(uiState.keySavedNotice) {
        if (uiState.keySavedNotice) {
            snackbar.showSnackbar("کلید ذخیره شد ✓")
            viewModel.consumeKeyNotice()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "تنظیمات",
            onBack = onBack,
            actions = {
                Box {
                    SoftIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "گزینه‌ها",
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("ظاهر") },
                            leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                themeDialogOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("درباره برنامه") },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onNavigateToAbout()
                            },
                        )
                    }
                }
            },
        )

        // --- tabs with the animated gradient indicator --------------------------
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            divider = {},
            indicator = { positions ->
                if (tab < positions.size) {
                    Box(
                        modifier = Modifier
                            .tabIndicatorOffset(positions[tab])
                            .padding(horizontal = 26.dp)
                            .height(3.dp)
                            .background(BrandGradient, RoundedCornerShape(50)),
                    )
                }
            },
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (tab == index) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (tab == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // --- pages, sliding as you switch ---------------------------------------
        AnimatedContent(
            targetState = tab,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                val pageSpring = spring<IntOffset>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
                (slideInHorizontally(pageSpring) { it / 3 * direction } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(pageSpring) { -it / 3 * direction } + fadeOut(tween(150)))
            },
            label = "settingsPages",
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                when (page) {
                    0 -> SectionSettingsTab(
                        modeId = AppSettings.MODE_CHAT,
                        icon = Icons.Rounded.Forum,
                        sectionName = "گفتگو",
                        isImage = false,
                        settings = settings,
                        viewModel = viewModel,
                        onPickService = { serviceSheetMode = AppSettings.MODE_CHAT },
                        onPickModel = {
                            modelSheetMode = AppSettings.MODE_CHAT
                            viewModel.loadModels(AppSettings.MODE_CHAT)
                        },
                        onEditKey = { keyDialogService = it },
                    )
                    1 -> SectionSettingsTab(
                        modeId = AppSettings.MODE_PROMPT,
                        icon = Icons.Rounded.EditNote,
                        sectionName = "پرامپت",
                        isImage = false,
                        settings = settings,
                        viewModel = viewModel,
                        onPickService = { serviceSheetMode = AppSettings.MODE_PROMPT },
                        onPickModel = {
                            modelSheetMode = AppSettings.MODE_PROMPT
                            viewModel.loadModels(AppSettings.MODE_PROMPT)
                        },
                        onEditKey = { keyDialogService = it },
                        promptDefaults = {
                            Spacer(Modifier.height(8.dp))
                            ListGroup {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Tune,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(7.dp),
                                            )
                                        }
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
                                        Switch(
                                            checked = settings.aiEnabled,
                                            onCheckedChange = viewModel::setAiEnabled,
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(
                                            text = "پیش‌فرض‌های پرامپت",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        PromptDefaultRow("حوزه", uiState.domains.map { it.id to it.nameFa }, settings.defaultDomainId, viewModel::setDefaultDomain)
                                        PromptDefaultRow("مدل هدف", TargetAi.entries.map { it.id to it.labelFa }, settings.defaultTargetAi.id) { id ->
                                            viewModel.setDefaultTarget(TargetAi.fromId(id))
                                        }
                                        PromptDefaultRow("زبان", OutputLanguage.entries.map { it.id to it.labelFa }, settings.defaultOutputLanguage.id) { id ->
                                            viewModel.setDefaultLanguage(OutputLanguage.fromId(id))
                                        }
                                        PromptDefaultRow("سطح جزئیات", DetailLevel.entries.map { it.id to it.labelFa }, settings.defaultDetailLevel.id) { id ->
                                            viewModel.setDefaultDetail(DetailLevel.fromId(id))
                                        }
                                    }
                                }
                            }
                        },
                    )
                    2 -> SectionSettingsTab(
                        modeId = AppSettings.MODE_IMAGE,
                        icon = Icons.Rounded.Image,
                        sectionName = "تصویر",
                        isImage = true,
                        settings = settings,
                        viewModel = viewModel,
                        onPickService = { serviceSheetMode = AppSettings.MODE_IMAGE },
                        onPickModel = {
                            modelSheetMode = AppSettings.MODE_IMAGE
                            viewModel.loadModels(AppSettings.MODE_IMAGE)
                        },
                        onEditKey = { keyDialogService = it },
                    )
                    else -> ServicesTab(
                        settings = settings,
                        onEdit = {
                            editorTarget = it
                            editorOpen = true
                        },
                        onDelete = { deleteTarget = it },
                        onAdd = {
                            editorTarget = null
                            editorOpen = true
                        },
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        SnackbarHost(hostState = snackbar)
    }

    // ======================= popups ============================================

    if (themeDialogOpen) {
        ThemeDialog(
            current = settings.themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                themeDialogOpen = false
            },
            onDismiss = { themeDialogOpen = false },
        )
    }

    serviceSheetMode?.let { modeId ->
        ServicePickerSheet(
            sectionName = sectionNameOf(modeId),
            services = settings.aiServices,
            selectedId = settings.serviceFor(modeId)?.id.orEmpty(),
            onSelect = {
                viewModel.setModeService(modeId, it)
                serviceSheetMode = null
            },
            onManage = {
                serviceSheetMode = null
                tab = 3
            },
            onDismiss = { serviceSheetMode = null },
        )
    }

    // close the model sheet if its section no longer has a service
    LaunchedEffect(modelSheetMode) {
        val modeId = modelSheetMode ?: return@LaunchedEffect
        if (settings.serviceFor(modeId) == null) modelSheetMode = null
    }
    modelSheetMode?.let { modeId ->
        val service = settings.serviceFor(modeId)
        if (service != null) {
            val isImage = modeId == AppSettings.MODE_IMAGE
            val test = uiState.testByMode[modeId]
            ModelPickerSheet(
                visible = true,
                models = uiState.modelsByMode[modeId].orEmpty(),
                selectedModel = if (isImage) service.imageModel else service.model,
                loading = uiState.loadingByMode[modeId] == true,
                errorFa = test?.takeIf { !it.first }?.second,
                onSelect = { viewModel.setServiceModel(service.id, it, imageModel = isImage) },
                onRefresh = { viewModel.loadModels(modeId) },
                onDismiss = { modelSheetMode = null },
            )
        }
    }

    keyDialogService?.let { service ->
        KeyDialog(
            service = service,
            hasKey = uiState.hasKey(service.id),
            maskedKey = uiState.maskedKey(service.id),
            onSave = {
                viewModel.saveApiKey(service.id, it)
                keyDialogService = null
            },
            onClear = {
                viewModel.clearApiKey(service.id)
                keyDialogService = null
            },
            onDismiss = { keyDialogService = null },
        )
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

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "حذف سرویس",
            text = "«${target.name}» و کلیدش حذف می‌شود؛ بخش‌هایی که به آن وصل بودند باید سرویس جدید انتخاب کنند.",
            confirmLabel = "حذف",
            dismissLabel = "انصراف",
            onConfirm = {
                viewModel.removeService(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** اسم فارسی هر بخش از روی شناسه‌اش. */
private fun sectionNameOf(modeId: String): String = when (modeId) {
    AppSettings.MODE_CHAT -> "گفتگو"
    AppSettings.MODE_PROMPT -> "پرامپت"
    AppSettings.MODE_IMAGE -> "تصویر"
    else -> "بخش"
}

/** One labeled chip row. */
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
 * One section's page: service → key → model → test, in two grouped cards.
 * Everything here belongs to THIS section only.
 */
@Composable
private fun SectionSettingsTab(
    modeId: String,
    icon: ImageVector,
    sectionName: String,
    isImage: Boolean,
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onPickService: () -> Unit,
    onPickModel: () -> Unit,
    onEditKey: (AiService) -> Unit,
    promptDefaults: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val service = settings.serviceFor(modeId)
    val test = uiState.testByMode[modeId]

    SectionLabel("سرویس $sectionName")
    ListGroup {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            onClick = onPickService,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(7.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service?.name ?: "انتخاب نشده",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (service != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = service?.baseUrl ?: "هر سرویسی که بخواهی — اینجا انتخاب کن",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                RowChevron()
            }
        }
    }
    service?.let { AiServicePresets.matchOf(it) }?.consoleUrl?.let { consoleUrl ->
        Text(
            text = "ساخت یا مدیریت کلید این سرویس ↗",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { openInBrowser(context, consoleUrl) },
        )
    }

    if (service != null) {
        SectionLabel("اتصال")
        ListGroup {
            Column {
                SettingsRow(
                    icon = Icons.Rounded.Key,
                    title = "کلید API",
                    subtitle = if (uiState.hasKey(service.id)) {
                        "ذخیره‌شده: ${uiState.maskedKey(service.id) ?: ""}"
                    } else {
                        "وارد نشده"
                    },
                    onClick = { onEditKey(service) },
                    trailing = { RowChevron() },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = if (isImage) "مدل تصویر" else "مدل",
                    subtitle = (if (isImage) service.imageModel else service.model)
                        .ifBlank { "انتخاب نشده" },
                    onClick = onPickModel,
                    trailing = { RowChevron() },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsRow(
                    icon = Icons.Rounded.Speed,
                    title = "تست اتصال",
                    subtitle = when {
                        uiState.loadingByMode[modeId] == true -> "در حال بررسی…"
                        test != null -> test.second
                        else -> "بررسی سریع سرویس و کلید"
                    },
                    onClick = { viewModel.testConnection(modeId) },
                    trailing = {
                        if (uiState.loadingByMode[modeId] == true) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            RowChevron()
                        }
                    },
                )
            }
        }
    } else if (settings.aiServices.isNotEmpty()) {
        Text(
            text = "این بخش هنوز سرویسی ندارد؛ از بالا یکی انتخاب کن.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    promptDefaults?.invoke()
}

/** The services page: every card shows where each service is used. */
@Composable
private fun ServicesTab(
    settings: AppSettings,
    onEdit: (AiService) -> Unit,
    onDelete: (AiService) -> Unit,
    onAdd: () -> Unit,
) {
    SectionLabel("سرویس‌ها")
    Text(
        text = "هر بخش در تب خودش به یک سرویس وصل می‌شود.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (settings.aiServices.isEmpty()) {
        ListGroup {
            Text(
                text = "هنوز سرویسی نداری؛ با دکمهٔ زیر اضافه کن.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        ListGroup {
            Column {
                settings.aiServices.forEachIndexed { index, service ->
                    ServiceCard(
                        service = service,
                        settings = settings,
                        onEdit = { onEdit(service) },
                        onDelete = { onDelete(service) },
                    )
                    if (index != settings.aiServices.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        onClick = onAdd,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "افزودن سرویس",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One service with its usage badges (کجا وصل است). */
@Composable
private fun ServiceCard(
    service: AiService,
    settings: AppSettings,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val usedIn = buildList {
        if (settings.chatServiceId == service.id) add("گفتگو")
        if (settings.promptServiceId == service.id) add("پرامپت")
        if (settings.imageServiceId == service.id) add("تصویر")
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ) {
                Icon(
                    Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(7.dp),
                )
            }
            Text(
                text = service.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
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
        Text(
            text = service.baseUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        if (usedIn.isEmpty()) {
            Text(
                text = "به هیچ بخشی وصل نیست",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                usedIn.forEach { section ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = section,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** پاپ‌آپ انتخاب سرویس یک بخش (bottom sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServicePickerSheet(
    sectionName: String,
    services: List<AiService>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "سرویس $sectionName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "این بخش فقط با همین سرویس کار می‌کند",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            services.forEach { service ->
                val selected = service.id == selectedId
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        Color.Transparent
                    },
                    onClick = { onSelect(service.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ) {
                            Icon(
                                Icons.Rounded.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(7.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = service.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                            Text(
                                text = service.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = "انتخاب‌شده",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onManage) {
                Text("مدیریت سرویس‌ها")
            }
        }
    }
}

/** پاپ‌آپ تغییر کلید API یک سرویس. */
@Composable
private fun KeyDialog(
    service: AiService,
    hasKey: Boolean,
    maskedKey: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var keyInput by rememberSaveable(service.id) { mutableStateOf("") }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کلید API ${service.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("کلید جدید") },
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
                if (hasKey) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "کلید فعلی: ${maskedKey ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClear) {
                        Text("حذف کلید ذخیره‌شده", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(keyInput) }, enabled = keyInput.isNotBlank()) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}

/** پاپ‌آپ انتخاب زمینهٔ برنامه. */
@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ظاهر برنامه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == current
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        } else {
                            Color.Transparent
                        },
                        onClick = { onSelect(mode) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                    ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                },
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = mode.labelFa,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        },
    )
}

/**
 * Add/edit a service: pick a ready preset (Gemini, CodeCraft, OpenAI, …) and
 * everything auto-fills — the user only pastes a key later, in each section.
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
