package com.promptsaz.app.domain.model

/**
 * Everything needed to generate one prompt.
 * Built on the Home screen, refined by clarifying answers, consumed by the engine.
 */
data class PromptSpec(
    val idea: String,
    val domainId: String,
    val targetAi: TargetAi,
    val outputLanguage: OutputLanguage,
    val detailLevel: DetailLevel,
    val mode: PromptMode = PromptMode.NEW,
    val answers: List<ClarifyingAnswer> = emptyList(),
) {
    fun answerFor(questionId: String): ClarifyingAnswer? = answers.firstOrNull { it.questionId == questionId }
}

enum class PromptMode { NEW, IMPROVE }

/** A clarifying question the engine decided to ask before generating. */
data class ClarifyingQuestion(
    val id: String,
    val questionFa: String,
    val chips: List<ClarifyChip>,
    val allowFreeText: Boolean,
    val priority: Int,
)

data class ClarifyChip(val id: String, val labelFa: String)

/** One answered (or skipped) question. Skipped answers become [BRACKETED] variables. */
data class ClarifyingAnswer(
    val questionId: String,
    val questionFa: String,
    /** Selected chip label or free text; null when skipped. */
    val value: String?,
) {
    val skipped: Boolean get() = value.isNullOrBlank()
}
