package com.promptsaz.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.provider.ProviderRegistry
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import com.promptsaz.app.ui.session.GenerationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val kbRepository: KbRepository,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: ProviderRegistry,
    private val session: GenerationSession,
) : ViewModel() {

    data class UiState(
        val idea: String = "",
        val improveMode: Boolean = false,
        val domainId: String = "general",
        val targetAi: TargetAi = TargetAi.ANY,
        val outputLanguage: OutputLanguage = OutputLanguage.SAME_AS_INPUT,
        val detailLevel: DetailLevel = DetailLevel.STANDARD,
        val domains: List<KbDomainEntry> = emptyList(),
        val aiEnabled: Boolean = false,
        val errorFa: String? = null,
        val selectedModel: String = "",
        val modelPickerVisible: Boolean = false,
        val models: List<String> = emptyList(),
        val modelsLoading: Boolean = false,
        val modelsErrorFa: String? = null,
        val services: List<com.promptsaz.app.domain.model.AiService> = emptyList(),
        val serviceId: String = "",
    ) {
        val ideaLength: Int get() = idea.length
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // live AI settings: badge + the model pill — تولید پرامپت has its OWN service
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val service = settings.serviceFor(com.promptsaz.app.domain.model.AppSettings.MODE_PROMPT)
                _uiState.update {
                    it.copy(
                        aiEnabled = settings.aiEnabled,
                        selectedModel = service?.model.orEmpty(),
                        services = settings.aiServices,
                        serviceId = service?.id.orEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val ready = runCatching { kbRepository.getReadyDomains() }.getOrDefault(emptyList())
            _uiState.update { state ->
                state.copy(
                    domainId = settings.defaultDomainId.takeIf { id -> ready.any { it.id == id } } ?: "general",
                    targetAi = settings.defaultTargetAi,
                    outputLanguage = settings.defaultOutputLanguage,
                    detailLevel = settings.defaultDetailLevel,
                    domains = ready,
                    aiEnabled = settings.aiEnabled,
                )
            }
        }
    }

    fun setIdea(value: String) {
        if (value.length <= MAX_IDEA_CHARS) {
            _uiState.update { it.copy(idea = value, errorFa = null) }
        }
    }

    fun setImproveMode(enabled: Boolean) = _uiState.update { it.copy(improveMode = enabled, errorFa = null) }

    fun setDomain(id: String) = _uiState.update { it.copy(domainId = id) }

    fun setTarget(target: TargetAi) = _uiState.update { it.copy(targetAi = target) }

    fun setLanguage(language: OutputLanguage) = _uiState.update { it.copy(outputLanguage = language) }

    fun setDetail(level: DetailLevel) = _uiState.update { it.copy(detailLevel = level) }

    // --- in-app model picker (mirrors the گفتگو tab) --------------------------

    fun openModelPicker() {
        _uiState.update { it.copy(modelPickerVisible = true) }
        if (_uiState.value.models.isEmpty() && !_uiState.value.modelsLoading) {
            loadModels()
        }
    }

    fun dismissModelPicker() {
        _uiState.update { it.copy(modelPickerVisible = false) }
    }

    fun loadModels() {
        val provider = providerRegistry.active() ?: return
        val serviceId = _uiState.value.serviceId.ifBlank { null }
        viewModelScope.launch {
            _uiState.update { it.copy(modelsLoading = true, modelsErrorFa = null) }
            provider.listModels(serviceId)
                .onSuccess { models ->
                    _uiState.update { it.copy(modelsLoading = false, models = models) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            modelsLoading = false,
                            modelsErrorFa = error.message ?: "خطای ناشناخته",
                        )
                    }
                }
        }
    }

    fun selectModel(model: String) {
        val serviceId = _uiState.value.serviceId
        viewModelScope.launch {
            settingsRepository.setServiceModel(serviceId, model, imageModel = false)
            _uiState.update { it.copy(selectedModel = model) }
        }
    }

    /** Binds the تولید پرامپت tab to a different service (per-section AI). */
    fun selectService(serviceId: String) {
        viewModelScope.launch {
            settingsRepository.setModeService(com.promptsaz.app.domain.model.AppSettings.MODE_PROMPT, serviceId)
            _uiState.update { it.copy(models = emptyList(), modelsErrorFa = null) }
        }
    }

    /** Validates and hands the spec to the session; null when invalid. */
    fun startGeneration(): Boolean {
        val state = _uiState.value
        val idea = state.idea.trim()
        return when {
            idea.isEmpty() -> {
                _uiState.update {
                    it.copy(
                        errorFa = if (it.improveMode) {
                            "اول پرامپت فعلی‌ات را در کادر بچسبان"
                        } else {
                            "اول توضیح کوتاهی از چیزی که می‌خواهی بنویس"
                        },
                    )
                }
                false
            }
            idea.length < MIN_IDEA_CHARS -> {
                _uiState.update { it.copy(errorFa = "یک جمله کامل‌تر بنویس تا نتیجه دقیق‌تر شود") }
                false
            }
            else -> {
                session.clear()
                session.spec = PromptSpec(
                    idea = idea,
                    domainId = state.domainId,
                    targetAi = state.targetAi,
                    outputLanguage = state.outputLanguage,
                    detailLevel = state.detailLevel,
                    mode = if (state.improveMode) PromptMode.IMPROVE else PromptMode.NEW,
                )
                true
            }
        }
    }

    companion object {
        /** ids of the segmented control on the prompt tab */
        const val MODE_NEW = "new"
        const val MODE_IMPROVE = "improve"

        const val MAX_IDEA_CHARS = 4000
        const val MIN_IDEA_CHARS = 3
    }
}
