package com.promptsaz.app.ui.session

import com.promptsaz.app.domain.model.ClarifyingQuestion
import com.promptsaz.app.domain.model.GeneratedPrompt
import com.promptsaz.app.domain.model.ImprovementFinding
import com.promptsaz.app.domain.model.PromptSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory carrier for the Home → Clarify → Result flow.
 * The archive (Room) remains the source of truth for everything saved;
 * this holder only survives navigation, not process death.
 */
@Singleton
class GenerationSession @Inject constructor() {

    var spec: PromptSpec? = null
    var questions: List<ClarifyingQuestion> = emptyList()
    var baseResult: GeneratedPrompt? = null
    var baseGroupId: String = ""
    var baseSavedId: Long = 0L
    var aiErrorFa: String? = null
    var improveFindings: List<ImprovementFinding> = emptyList()

    fun clear() {
        spec = null
        questions = emptyList()
        baseResult = null
        baseGroupId = ""
        baseSavedId = 0L
        aiErrorFa = null
        improveFindings = emptyList()
    }
}
