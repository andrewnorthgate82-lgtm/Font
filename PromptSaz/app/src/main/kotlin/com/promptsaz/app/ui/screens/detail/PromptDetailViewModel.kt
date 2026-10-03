package com.promptsaz.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.repository.PromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PromptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val promptRepository: PromptRepository,
    private val kbRepository: KbRepository,
) : ViewModel() {

    val promptId: Long = savedStateHandle.get<Long>("id") ?: 0L

    val prompt: StateFlow<ArchivedPrompt?> = promptRepository.observePrompt(promptId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _domains = MutableStateFlow<List<KbDomainEntry>>(emptyList())
    val domains: StateFlow<List<KbDomainEntry>> = _domains.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        viewModelScope.launch {
            _domains.value = runCatching { kbRepository.getReadyDomains() }.getOrDefault(emptyList())
        }
    }

    fun toggleFavorite() {
        prompt.value?.let { current ->
            viewModelScope.launch {
                runCatching {
                    promptRepository.update(current.copy(isFavorite = !current.isFavorite, updatedAt = System.currentTimeMillis()))
                }.onFailure { _message.value = "ذخیره علاقه‌مندی ناموفق بود" }
            }
        }
    }

    fun saveEdits(title: String, tags: List<String>, notes: String) {
        val current = prompt.value ?: return
        viewModelScope.launch {
            runCatching {
                promptRepository.update(
                    current.copy(
                        title = title.trim().ifEmpty { current.title },
                        tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                        notes = notes,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess { _message.value = "تغییرات ذخیره شد ✓" }
                .onFailure { _message.value = "ذخیره تغییرات ناموفق بود" }
        }
    }

    fun duplicate() {
        viewModelScope.launch {
            runCatching { promptRepository.duplicate(promptId) }
                .onSuccess { _message.value = "کپی ساخته شد" }
                .onFailure { _message.value = "ساخت کپی ناموفق بود" }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { promptRepository.delete(promptId) }
                .onSuccess { _deleted.value = true }
                .onFailure { _message.value = "حذف ناموفق بود" }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
