package com.promptsaz.app.ui.screens.kb

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.repository.KbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class KbViewModel @Inject constructor(
    private val kbRepository: KbRepository,
) : ViewModel() {

    data class UiState(
        val ready: List<KbDomainEntry> = emptyList(),
        val comingSoon: List<KbDomainEntry> = emptyList(),
        val errorFa: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { kbRepository.getRegistry() }
                .onSuccess { registry ->
                    _uiState.value = UiState(
                        ready = registry.domains.filter { it.isReady },
                        comingSoon = registry.domains.filter { !it.isReady },
                    )
                }
                .onFailure { _uiState.value = UiState(errorFa = "بارگذاری دانش‌نامه ناموفق بود") }
        }
    }
}

@HiltViewModel
class KbDomainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val kbRepository: KbRepository,
) : ViewModel() {

    data class UiState(
        val knowledge: DomainKnowledge? = null,
        val entry: KbDomainEntry? = null,
        val loading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val domainId = savedStateHandle.get<String>("domainId") ?: ""
        viewModelScope.launch {
            val registry = runCatching { kbRepository.getRegistry() }.getOrNull()
            val entry = registry?.domains?.firstOrNull { it.id == domainId }
            val knowledge = if (entry?.isReady == true) kbRepository.getDomain(domainId) else null
            _uiState.value = UiState(knowledge = knowledge, entry = entry, loading = false)
        }
    }
}
