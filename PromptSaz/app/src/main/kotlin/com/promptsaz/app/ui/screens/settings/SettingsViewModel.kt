package com.promptsaz.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.data.settings.SecureKeyStore
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.provider.ProviderHealth
import com.promptsaz.app.domain.provider.ProviderRegistry
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import com.promptsaz.app.util.toPersianDigits
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings view state for the per-section AI world: every section (گفتگو /
 * تولید پرامپت / تولید تصویر) binds to its own service, whose key, model list
 * and connection test are tracked separately (keyed by mode id).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
    private val providerRegistry: ProviderRegistry,
    private val kbRepository: KbRepository,
) : ViewModel() {

    data class UiState(
        val domains: List<KbDomainEntry> = emptyList(),
        /** serviceId → (hasKey, maskedKey). */
        val keyStatus: Map<String, Pair<Boolean, String?>> = emptyMap(),
        val keySavedNotice: Boolean = false,
        /** Per-section model lists, keyed by AppSettings.MODE_*. */
        val modelsByMode: Map<String, List<String>> = emptyMap(),
        val loadingByMode: Map<String, Boolean> = emptyMap(),
        /** Per-section connection test results, keyed by mode id. */
        val testByMode: Map<String, Pair<Boolean, String>> = emptyMap(),
    ) {

        fun hasKey(serviceId: String): Boolean = keyStatus[serviceId]?.first == true

        fun maskedKey(serviceId: String): String? = keyStatus[serviceId]?.second
    }

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(domains = runCatching { kbRepository.getReadyDomains() }.getOrDefault(emptyList()))
            }
        }
    }

    // --- appearance + prompt defaults ------------------------------------------------

    fun setThemeMode(mode: com.promptsaz.app.domain.model.ThemeMode) = launchSetting { settingsRepository.setThemeMode(mode) }

    fun setDefaultDomain(domainId: String) = launchSetting { settingsRepository.setDefaultDomain(domainId) }

    fun setDefaultTarget(target: com.promptsaz.app.domain.model.TargetAi) =
        launchSetting { settingsRepository.setDefaultTarget(target) }

    fun setDefaultLanguage(language: com.promptsaz.app.domain.model.OutputLanguage) =
        launchSetting { settingsRepository.setDefaultLanguage(language) }

    fun setDefaultDetail(level: com.promptsaz.app.domain.model.DetailLevel) =
        launchSetting { settingsRepository.setDefaultDetail(level) }

    fun setAiEnabled(enabled: Boolean) = launchSetting { settingsRepository.setAiEnabled(enabled) }

    // --- per-section binding + models --------------------------------------------------

    /** Binds a section to one of the configured services. */
    fun setModeService(modeId: String, serviceId: String) = launchSetting {
        settingsRepository.setModeService(modeId, serviceId)
    }

    /** Saves the model picked inside a section's block. */
    fun setServiceModel(serviceId: String, model: String, imageModel: Boolean) = launchSetting {
        settingsRepository.setServiceModel(serviceId, model, imageModel)
    }

    /** GET {base}/models of the section's bound service. */
    fun loadModels(modeId: String) {
        if (_uiState.value.loadingByMode[modeId] == true) return
        val serviceId = settings.value.serviceFor(modeId)?.id ?: return
        _uiState.update { it.copy(loadingByMode = it.loadingByMode + (modeId to true)) }
        viewModelScope.launch {
            val provider = providerRegistry.active() ?: run {
                _uiState.update { it.copy(loadingByMode = it.loadingByMode - modeId) }
                return@launch
            }
            provider.listModels(serviceId)
                .onSuccess { models ->
                    _uiState.update {
                        it.copy(
                            loadingByMode = it.loadingByMode - modeId,
                            modelsByMode = it.modelsByMode + (modeId to models),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingByMode = it.loadingByMode - modeId,
                            testByMode = it.testByMode +
                                (modeId to (false to (error.message ?: "دریافت فهرست مدل‌ها ناموفق بود"))),
                        )
                    }
                }
        }
    }

    /** Tests the section's bound service; Persian result with exact HTTP details. */
    fun testConnection(modeId: String) {
        if (_uiState.value.loadingByMode[modeId] == true) return
        val serviceId = settings.value.serviceFor(modeId)?.id ?: return
        _uiState.update { it.copy(loadingByMode = it.loadingByMode + (modeId to true)) }
        viewModelScope.launch {
            val provider = providerRegistry.active()
            if (provider == null) {
                _uiState.update {
                    it.copy(
                        loadingByMode = it.loadingByMode - modeId,
                        testByMode = it.testByMode + (modeId to (false to "سرویسی نصب نیست")),
                    )
                }
                return@launch
            }
            when (val health = provider.testConnection(serviceId)) {
                is ProviderHealth.Ok -> {
                    val count = health.models.size
                    _uiState.update {
                        it.copy(
                            loadingByMode = it.loadingByMode - modeId,
                            testByMode = it.testByMode +
                                (
                                    modeId to (
                                        true to
                                            "اتصال موفق ✓" +
                                                if (count > 0) " (${count.toPersianDigits()} مدل پیدا شد)" else ""
                                        )
                                    ),
                            modelsByMode = if (health.models.isNotEmpty()) {
                                it.modelsByMode + (modeId to health.models)
                            } else {
                                it.modelsByMode
                            },
                        )
                    }
                }
                is ProviderHealth.Failed -> {
                    _uiState.update {
                        it.copy(
                            loadingByMode = it.loadingByMode - modeId,
                            testByMode = it.testByMode + (modeId to (false to health.messageFa)),
                        )
                    }
                }
            }
        }
    }

    // --- service management --------------------------------------------------------------

    fun setActiveService(id: String) = launchSetting { settingsRepository.setActiveService(id) }

    fun addService(name: String, baseUrl: String, type: String) = launchSetting {
        settingsRepository.addService(name, normalizeUrl(baseUrl), type)
    }

    fun updateService(id: String, name: String, baseUrl: String, type: String) = launchSetting {
        settingsRepository.updateService(id, name, normalizeUrl(baseUrl), type)
    }

    fun removeService(id: String) = launchSetting {
        settingsRepository.removeService(id)
        secureKeyStore.clearApiKey(id)
    }

    /** Makes a bare host usable: "api.openai.com/v1" → "https://api.openai.com/v1". */
    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    // --- per-service keys ------------------------------------------------------------------

    fun saveApiKey(serviceId: String, rawKey: String) {
        if (rawKey.isBlank()) return
        secureKeyStore.saveApiKey(serviceId, rawKey)
        _uiState.update {
            it.copy(
                keySavedNotice = true,
                keyStatus = it.keyStatus + (serviceId to (true to secureKeyStore.maskApiKey(serviceId))),
            )
        }
        // smooth the flow: with the key saved and no model picked yet on this
        // service, fetch the model list of the section being configured
        val modeWithModel = settings.value.aiServices
            .firstOrNull { it.id == serviceId }
            ?.let { service -> service.model.isBlank() || service.imageModel.isBlank() }
        if (modeWithModel == true) {
            val modeId = modeOfService(serviceId) ?: return
            loadModels(modeId)
        }
    }

    fun clearApiKey(serviceId: String) {
        secureKeyStore.clearApiKey(serviceId)
        _uiState.update {
            it.copy(keySavedNotice = false, keyStatus = it.keyStatus + (serviceId to (false to null)))
        }
    }

    fun consumeKeyNotice() = _uiState.update { it.copy(keySavedNotice = false) }

    /** Which section (if any) is currently bound to this service → refresh its model list. */
    private fun modeOfService(serviceId: String): String? {
        val current = settings.value
        return when (serviceId) {
            current.serviceFor(AppSettings.MODE_CHAT)?.id -> AppSettings.MODE_CHAT
            current.serviceFor(AppSettings.MODE_PROMPT)?.id -> AppSettings.MODE_PROMPT
            current.serviceFor(AppSettings.MODE_IMAGE)?.id -> AppSettings.MODE_IMAGE
            else -> null
        }
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
