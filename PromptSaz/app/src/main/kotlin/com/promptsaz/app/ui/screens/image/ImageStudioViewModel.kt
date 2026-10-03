package com.promptsaz.app.ui.screens.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.ImageGeneration
import com.promptsaz.app.domain.provider.ProviderRegistry
import com.promptsaz.app.domain.repository.ImageRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * تصویر tab: prompt → generated image, with its own model picker (image
 * models usually differ from chat models) and a local history.
 */
@HiltViewModel
class ImageStudioViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: ProviderRegistry,
    private val keyStore: ApiKeyStore,
) : ViewModel() {

    data class UiState(
        val prompt: String = "",
        val size: String = SIZE_SQUARE,
        val generating: Boolean = false,
        val errorFa: String? = null,
        val hasKey: Boolean = false,
        val selectedModel: String = "",
        val modelPickerVisible: Boolean = false,
        val models: List<String> = emptyList(),
        val modelsLoading: Boolean = false,
        val modelsErrorFa: String? = null,
        val history: List<ImageGeneration> = emptyList(),
        val current: ImageGeneration? = null,
    ) {
        val needsSetup: Boolean get() = !hasKey || selectedModel.isBlank()
        val canGenerate: Boolean get() = prompt.isNotBlank() && !generating && !needsSetup

        companion object {
            const val SIZE_SQUARE = "1024x1024"
            const val SIZE_PORTRAIT = "1024x1536"
            const val SIZE_LANDSCAPE = "1536x1024"
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        selectedModel = settings.aiImageModel.ifBlank { settings.aiModel },
                    )
                }
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(hasKey = keyStore.hasApiKey()) }
        }
        viewModelScope.launch {
            imageRepository.history().collect { history ->
                _uiState.update { state ->
                    val current = state.current ?: history.firstOrNull()
                    it.copy(history = history, current = current)
                }
            }
        }
    }

    fun updatePrompt(text: String) {
        _uiState.update { it.copy(prompt = text) }
    }

    fun selectSize(size: String) {
        _uiState.update { it.copy(size = size) }
    }

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
        viewModelScope.launch {
            _uiState.update { it.copy(modelsLoading = true, modelsErrorFa = null) }
            provider.listModels()
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
        viewModelScope.launch {
            settingsRepository.setAiImageModel(model)
            _uiState.update { it.copy(selectedModel = model) }
        }
    }

    fun openFromHistory(generation: ImageGeneration) {
        _uiState.update { it.copy(current = generation, prompt = generation.prompt, size = generation.size) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorFa = null) }
    }

    fun deleteFromHistory(generation: ImageGeneration) {
        viewModelScope.launch {
            imageRepository.delete(generation.id)
            if (_uiState.value.current?.id == generation.id) {
                _uiState.update { it.copy(current = null) }
            }
        }
    }

    fun readImage(fileName: String): ByteArray? = imageRepository.readImage(fileName)

    /** Generates one image with the selected model and shows it. */
    fun generate() {
        val state = _uiState.value
        if (!state.canGenerate) return
        viewModelScope.launch {
            _uiState.update { it.copy(generating = true, errorFa = null) }
            imageRepository.generate(state.prompt, state.selectedModel, state.size)
                .onSuccess { generation ->
                    _uiState.update { it.copy(generating = false, current = generation) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(generating = false, errorFa = error.message ?: "ساخت تصویر ناموفق بود.")
                    }
                }
        }
    }
}
