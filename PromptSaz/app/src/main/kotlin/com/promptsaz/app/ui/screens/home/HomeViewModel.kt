package com.promptsaz.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
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
    ) {
        val ideaLength: Int get() = idea.length
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
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
        const val MAX_IDEA_CHARS = 4000
        const val MIN_IDEA_CHARS = 3
    }
}
