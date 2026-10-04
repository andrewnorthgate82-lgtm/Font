package com.promptsaz.app.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ChatMessage
import com.promptsaz.app.domain.provider.ProviderRegistry
import com.promptsaz.app.domain.repository.ChatRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * گفتگو tab: conversations list, active conversation messages, model picker
 * state and the send flow (text + attached image → vision model).
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: ProviderRegistry,
    private val keyStore: ApiKeyStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val conversationId: Long = 0L,
        val conversations: List<ChatConversation> = emptyList(),
        val messages: List<ChatMessage> = emptyList(),
        val sending: Boolean = false,
        val errorFa: String? = null,
        val aiEnabled: Boolean = false,
        val hasKey: Boolean = false,
        val selectedModel: String = "",
        val modelPickerVisible: Boolean = false,
        val models: List<String> = emptyList(),
        val modelsLoading: Boolean = false,
        val modelsErrorFa: String? = null,
        val services: List<com.promptsaz.app.domain.model.AiService> = emptyList(),
        val serviceId: String = "",
    ) {
        val canSend: Boolean get() = !sending
        val needsSetup: Boolean get() = !hasKey || selectedModel.isBlank()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                // گفتگو runs on ITS OWN bound service (per-section AI).
                val service = settings.serviceFor(com.promptsaz.app.domain.model.AppSettings.MODE_CHAT)
                _uiState.update {
                    it.copy(
                        aiEnabled = settings.aiEnabled,
                        selectedModel = service?.model.orEmpty(),
                        hasKey = service != null && keyStore.hasApiKey(service.id),
                        services = settings.aiServices,
                        serviceId = service?.id.orEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            chatRepository.conversations().collect { list ->
                _uiState.update { it.copy(conversations = list) }
            }
        }
        // deep link from the app menu: open a specific conversation
        savedStateHandle.get<Long>("conversationId")?.takeIf { it > 0L }?.let { id ->
            openConversation(id)
        }
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

    /** Binds the گفتگو tab to a different service (per-section AI). */
    fun selectService(serviceId: String) {
        viewModelScope.launch {
            settingsRepository.setModeService(com.promptsaz.app.domain.model.AppSettings.MODE_CHAT, serviceId)
            _uiState.update { it.copy(models = emptyList(), modelsErrorFa = null) }
        }
    }

    fun newConversation() {
        messagesJob?.cancel()
        _uiState.update {
            it.copy(conversationId = 0L, messages = emptyList(), errorFa = null)
        }
    }

    fun openConversation(conversationId: Long) {
        messagesJob?.cancel()
        _uiState.update { it.copy(conversationId = conversationId, errorFa = null) }
        messagesJob = viewModelScope.launch {
            chatRepository.messages(conversationId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
            if (_uiState.value.conversationId == conversationId) {
                newConversation()
            }
        }
    }

    /** Reads a stored attachment for the message list. */
    fun readImageFile(fileName: String): ByteArray? = chatRepository.readImage(fileName)

    fun dismissError() {
        _uiState.update { it.copy(errorFa = null) }
    }

    /** Sends the user's text (+ optional attached image) and shows the reply. */
    fun send(text: String, imageBytes: ByteArray?, imageExtension: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && imageBytes == null) return
        if (!_uiState.value.canSend) return

        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, errorFa = null) }
            val conversationId = _uiState.value.conversationId
            val result = chatRepository.sendMessage(
                conversationId = conversationId,
                text = trimmed.ifBlank { "این تصویر را تحلیل کن." },
                image = imageBytes?.inputStream(),
                imageExtension = imageExtension,
            )
            // A new conversation row exists after the send (even on failure),
            // so attach this screen to it to show the saved user message.
            if (conversationId == 0L) {
                val newest = _uiState.value.conversations.firstOrNull()
                if (newest != null) openConversation(newest.id)
            }
            result.onFailure { error ->
                _uiState.update { it.copy(errorFa = error.message ?: "ارسال ناموفق بود.") }
            }
            _uiState.update { it.copy(sending = false) }
        }
    }
}
