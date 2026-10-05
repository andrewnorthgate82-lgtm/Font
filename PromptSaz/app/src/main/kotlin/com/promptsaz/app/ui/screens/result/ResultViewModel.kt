package com.promptsaz.app.ui.screens.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.domain.engine.PromptEngine
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.GeneratedPrompt
import com.promptsaz.app.domain.model.ImprovementFinding
import com.promptsaz.app.domain.model.VariantStyle
import com.promptsaz.app.domain.usecase.GeneratePromptUseCase
import com.promptsaz.app.domain.usecase.Regeneration
import com.promptsaz.app.ui.session.GenerationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val session: GenerationSession,
    private val engine: PromptEngine,
    private val generateUseCase: GeneratePromptUseCase,
) : ViewModel() {

    data class UiState(
        val result: GeneratedPrompt? = null,
        val findings: List<ImprovementFinding> = emptyList(),
        val aiErrorFa: String? = null,
        val working: Boolean = false,
        val variant: VariantStyle = VariantStyle.STANDARD,
        val savedNoticeFa: String? = null,
        val errorFa: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val result = session.baseResult
        _uiState.value = UiState(
            result = result,
            findings = session.improveFindings,
            aiErrorFa = session.aiErrorFa,
            savedNoticeFa = if (result != null) "در آرشیو ذخیره شد ✓" else null,
        )
    }

    /** Switches to a variant (offline IR transform) and saves it to the archive. */
    fun selectVariant(style: VariantStyle) {
        val base = session.baseResult ?: return
        if (_uiState.value.working) return
        if (base.isAiGenerated) {
            _uiState.update {
                it.copy(errorFa = "نسخه‌ها فقط برای پرامپت‌های موتور آفلاین در دسترس‌اند")
            }
            return
        }
        _uiState.update { it.copy(working = true, errorFa = null) }
        viewModelScope.launch {
            runCatching {
                val variant = engine.variantOf(base, style)
                val savedId = generateUseCase.saveVariant(variant, session.baseGroupId)
                variant to savedId
            }.onSuccess { (variant, savedId) ->
                _uiState.update {
                    it.copy(
                        result = variant,
                        variant = style,
                        working = false,
                        savedNoticeFa = "نسخه «${style.labelFa}» ساخته و ذخیره شد ✓",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(working = false, errorFa = error.message ?: "ساخت نسخه ناموفق بود")
                }
            }
        }
    }

    /** One-tap structural improvement: bump detail level and regenerate. */
    fun improveStructure() {
        val base = session.baseResult ?: return
        if (_uiState.value.working) return
        val nextLevel = when (base.spec.detailLevel) {
            DetailLevel.QUICK -> DetailLevel.STANDARD
            else -> DetailLevel.EXPERT
        }
        _uiState.update { it.copy(working = true, errorFa = null) }
        viewModelScope.launch {
            runCatching {
                generateUseCase.regenerateAiOnly(base.spec.copy(detailLevel = nextLevel))
            }.onSuccess { regeneration ->
                when (regeneration) {
                    is Regeneration.Saved -> {
                        session.baseResult = regeneration.result
                        session.baseGroupId = regeneration.groupId
                        session.baseSavedId = regeneration.savedId
                        session.aiErrorFa = null
                        _uiState.update {
                            it.copy(
                                result = regeneration.result,
                                variant = VariantStyle.STANDARD,
                                aiErrorFa = null,
                                working = false,
                                savedNoticeFa = "نسخه بهبودیافته ساخته و ذخیره شد ✓",
                            )
                        }
                    }
                    // The model failed (مثلاً سهمیه تمام شده) — keep the CURRENT
                    // prompt on screen; never swap it for a weaker offline one.
                    is Regeneration.Failed -> {
                        _uiState.update {
                            it.copy(working = false, errorFa = regeneration.messageFa)
                        }
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(working = false, errorFa = error.message ?: "بهبود ناموفق بود") }
            }
        }
    }

    fun regenerate() {
        val base = session.baseResult ?: return
        if (_uiState.value.working) return
        _uiState.update { it.copy(working = true, errorFa = null) }
        viewModelScope.launch {
            runCatching { generateUseCase.regenerateAiOnly(base.spec) }
                .onSuccess { regeneration ->
                    when (regeneration) {
                        is Regeneration.Saved -> {
                            session.baseResult = regeneration.result
                            session.baseGroupId = regeneration.groupId
                            session.baseSavedId = regeneration.savedId
                            session.aiErrorFa = null
                            _uiState.update {
                                it.copy(
                                    result = regeneration.result,
                                    variant = VariantStyle.STANDARD,
                                    aiErrorFa = null,
                                    working = false,
                                    savedNoticeFa = "دوباره ساخته و ذخیره شد ✓",
                                )
                            }
                        }
                        is Regeneration.Failed -> {
                            _uiState.update {
                                it.copy(working = false, errorFa = regeneration.messageFa)
                            }
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(working = false, errorFa = error.message ?: "ساخت ناموفق بود") }
                }
        }
    }

    fun dismissAiError() = _uiState.update { it.copy(aiErrorFa = null) }

    fun dismissError() = _uiState.update { it.copy(errorFa = null) }
}
