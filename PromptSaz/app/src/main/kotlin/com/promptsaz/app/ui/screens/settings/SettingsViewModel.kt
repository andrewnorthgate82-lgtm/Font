package com.promptsaz.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.data.settings.SecureKeyStore
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
    private val providerRegistry: ProviderRegistry,
    private val kbRepository: KbRepository,
) : ViewModel() {

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val domains: List<KbDomainEntry> = emptyList(),
        val hasKey: Boolean = false,
        val maskedKey: String? = null,
        val keySavedNotice: Boolean = false,
        val models: List<String> = emptyList(),
        val modelsLoading: Boolean = false,
        val testing: Boolean = false,
        /** null = no result yet; Pair(ok, persian message with exact HTTP details). */
        val testResult: Pair<Boolean, String>? = null,
    )

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // key status follows the ACTIVE service (switching services updates it)
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                val serviceId = current.activeService?.id
                _uiState.update {
                    it.copy(
                        hasKey = serviceId != null && secureKeyStore.hasApiKey(serviceId),
                        maskedKey = serviceId?.let { id -> secureKeyStore.maskApiKey(id) },
                    )
                }
            }
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(domains = runCatching { kbRepository.getReadyDomains() }.getOrDefault(emptyList()))
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) = launchSetting { settingsRepository.setThemeMode(mode) }

    fun setDefaultDomain(domainId: String) = launchSetting { settingsRepository.setDefaultDomain(domainId) }

    fun setDefaultTarget(target: TargetAi) = launchSetting { settingsRepository.setDefaultTarget(target) }

    fun setDefaultLanguage(language: OutputLanguage) =
        launchSetting { settingsRepository.setDefaultLanguage(language) }

    fun setDefaultDetail(level: DetailLevel) = launchSetting { settingsRepository.setDefaultDetail(level) }

    fun setAiEnabled(enabled: Boolean) = launchSetting { settingsRepository.setAiEnabled(enabled) }

    fun setAiBaseUrl(url: String) = launchSetting { settingsRepository.setAiBaseUrl(url) }

    fun setAiModel(model: String) = launchSetting { settingsRepository.setAiModel(model) }

    // --- multi-service management ----------------------------------------------

    fun setActiveService(id: String) = launchSetting { settingsRepository.setActiveService(id) }

    fun addService(name: String, baseUrl: String) = launchSetting {
        settingsRepository.addService(name, normalizeUrl(baseUrl))
    }

    fun updateService(id: String, name: String, baseUrl: String) = launchSetting {
        settingsRepository.updateService(id, name, normalizeUrl(baseUrl))
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

    fun saveApiKey(rawKey: String) {
        if (rawKey.isBlank()) return
        val serviceId = settings.value.activeService?.id ?: return
        secureKeyStore.saveApiKey(serviceId, rawKey)
        _uiState.update {
            it.copy(
                keySavedNotice = true,
                hasKey = true,
                maskedKey = secureKeyStore.maskApiKey(serviceId),
            )
        }
    }

    fun clearApiKey() {
        val serviceId = settings.value.activeService?.id ?: return
        secureKeyStore.clearApiKey(serviceId)
        _uiState.update { it.copy(keySavedNotice = false, hasKey = false, maskedKey = null) }
    }

    fun consumeKeyNotice() = _uiState.update { it.copy(keySavedNotice = false) }

    /** GET {base}/models — populates the model picker; manual entry stays as fallback. */
    fun loadModels() {
        if (_uiState.value.modelsLoading) return
        _uiState.update { it.copy(modelsLoading = true) }
        viewModelScope.launch {
            val provider = providerRegistry.active() ?: run {
                _uiState.update { it.copy(modelsLoading = false) }
                return@launch
            }
            provider.listModels()
                .onSuccess { models ->
                    _uiState.update { it.copy(modelsLoading = false, models = models) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            modelsLoading = false,
                            testResult = false to (error.message ?: "دریافت فهرست مدل‌ها ناموفق بود"),
                        )
                    }
                }
        }
    }

    /** Test connection: exact HTTP status and error body are shown in Persian. */
    fun testConnection() {
        if (_uiState.value.testing) return
        _uiState.update { it.copy(testing = true) }
        viewModelScope.launch {
            val provider = providerRegistry.active()
            if (provider == null) {
                _uiState.update { it.copy(testing = false, testResult = false to "سرویسی نصب نیست") }
                return@launch
            }
            when (val health = provider.testConnection()) {
                is ProviderHealth.Ok -> {
                    val count = health.models.size
                    _uiState.update {
                        it.copy(
                            testing = false,
                            testResult = true to "اتصال موفق ✓" +
                                if (count > 0) " (${count.toPersianDigits()} مدل پیدا شد)" else "",
                            models = health.models,
                        )
                    }
                }
                is ProviderHealth.Failed -> {
                    _uiState.update { it.copy(testing = false, testResult = false to health.messageFa) }
                }
            }
        }
    }

    fun consumeTestResult() = _uiState.update { it.copy(testResult = null) }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
