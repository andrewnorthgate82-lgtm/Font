package com.promptsaz.app.domain.model

/**
 * A generated prompt as stored in the archive (domain representation,
 * independent of Room).
 */
data class ArchivedPrompt(
    val id: Long = 0L,
    /** Prompts that are variants of the same original share a groupId. */
    val groupId: String,
    val title: String,
    val promptText: String,
    val originalIdea: String,
    val domainId: String,
    val targetAi: TargetAi,
    val outputLanguage: OutputLanguage,
    val detailLevel: DetailLevel,
    /** Structural-completeness score, 0..100 (computed by QualityScorer, Phase 3). */
    val score: Int,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val isAiGenerated: Boolean = false,
    /** One of: concise / standard / creative. */
    val variantStyle: String = "standard",
    val createdAt: Long,
    val updatedAt: Long,
)
