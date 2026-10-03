package com.promptsaz.app.ui.screens.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.ui.components.AppHeader
import com.promptsaz.app.ui.components.SectionLabel
import com.promptsaz.app.ui.components.SelectChipRow

/** Settings: theme, generation defaults, AI mode (key/model/test), about. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

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
                            text = "توجه: در این حالت، ایده و تنظیمات شما برای ساخت پرامپت به سرور شخص ثالثی که نشانی‌اش را پایین وارد کرده‌ای ارسال می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.aiBaseUrl,
                        onValueChange = viewModel::setAiBaseUrl,
                        label = { Text("نشانی سرور (Base URL)") },
                        placeholder = { Text("https://codecraftapi.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))
                    var keyInput by rememberSaveable { mutableStateOf("") }
                    var keyVisible by rememberSaveable { mutableStateOf(false) }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("کلید API") },
                        placeholder = { Text("cc_…") },
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
                        text = "کلید را از پنل حساب Codecraft (بخش API Keys) کپی کن و همین‌جا بچسبان و «ذخیره کلید» را بزن. کلید فقط روی همین گوشی، رمزنگاری‌شده نگه داشته می‌شود و هرگز جایی دیگر ذخیره یا ارسال نمی‌شود.",
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
                    OutlinedTextField(
                        value = settings.aiModel,
                        onValueChange = viewModel::setAiModel,
                        label = { Text("نام مدل") },
                        placeholder = { Text("مثلاً gpt-4o-mini") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            uiState.models.take(12).forEach { model ->
                                FilterChip(
                                    selected = settings.aiModel == model,
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
}
