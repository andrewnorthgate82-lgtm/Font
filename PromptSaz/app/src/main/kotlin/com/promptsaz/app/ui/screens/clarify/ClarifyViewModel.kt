package com.promptsaz.app.ui.screens.clarify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.domain.engine.PromptEngine
import com.promptsaz.app.domain.model.ClarifyingAnswer
import com.promptsaz.app.domain.model.ClarifyingQuestion
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.usecase.GeneratePromptUseCase
import com.promptsaz.app.domain.usecase.ImprovePromptUseCase
import com.promptsaz.app.ui.session.GenerationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ClarifyViewModel @Inject constructor(
    private val session: GenerationSession,
    private val engine: PromptEngine,
    private val kbRepository: KbRepository,
    private val generateUseCase: GeneratePromptUseCase,
    private val improveUseCase: ImprovePromptUseCase,
) : ViewModel() {

    data class UiState(
        val questions: List<ClarifyingQuestion> = emptyList(),
        /** questionId -> selected chip label or free text; null = skipped. */
        val answers: Map<String, String?> = emptyMap(),
        val loading: Boolean = false,
        val errorFa: String? = null,
        val done: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val spec = session.spec
        if (spec == null) {
            _uiState.update { it.copy(errorFa = "خطای غیرمنتظره؛ دوباره از صفحه اول شروع کن") }
            return
        }
        if (spec.mode == PromptMode.IMPROVE) {
            // Improve mode analyzes the pasted prompt directly; no questions.
            return
        }
        viewModelScope.launch {
            val kb = runCatching { kbRepository.getDomain(spec.domainId) }.getOrNull()
            val questions = engine.clarifyingQuestions(kb, spec.idea)
            _uiState.update { it.copy(questions = questions) }
        }
    }

    fun answer(questionId: String, value: String) =
        _uiState.update { it.copy(answers = it.answers + (questionId to value)) }

    fun skip(questionId: String) =
        _uiState.update { it.copy(answers = it.answers + (questionId to null)) }

    fun skipAll() = _uiState.update { state ->
        state.copy(answers = state.questions.associate { it.id to null })
    }

    fun generate() {
        val spec = session.spec ?: run {
            _uiState.update { it.copy(errorFa = "خطای غیرمنتظره؛ دوباره از صفحه اول شروع کن") }
            return
        }
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, errorFa = null) }
        viewModelScope.launch {
            val answers = _uiState.value.questions.map { question ->
                ClarifyingAnswer(
                    questionId = question.id,
                    questionFa = question.questionFa,
                    value = _uiState.value.answers[question.id],
                )
            }
            val finalSpec = spec.copy(answers = answers)
            runCatching {
                if (finalSpec.mode == PromptMode.IMPROVE) improveUseCase(finalSpec) else generateUseCase(finalSpec)
            }.onSuccess { outcome ->
                session.baseResult = outcome.result
                session.baseGroupId = outcome.groupId
                session.baseSavedId = outcome.savedId
                session.aiErrorFa = outcome.aiErrorFa
                session.improveFindings = if (finalSpec.mode == PromptMode.IMPROVE) {
                    outcome.report.findings
                } else {
                    emptyList()
                }
                _uiState.update { it.copy(loading = false, done = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorFa = "ساخت پرامپت ناموفق بود: ${error.message ?: "خطای نامشخص"}",
                    )
                }
            }
        }
    }
}
