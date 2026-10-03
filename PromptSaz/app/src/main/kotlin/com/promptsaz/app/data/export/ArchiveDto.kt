package com.promptsaz.app.data.export

import com.promptsaz.app.data.db.PromptEntity
import com.promptsaz.app.data.db.toEntity
import kotlinx.serialization.Serializable

/**
 * Archive export/import format (v1). Written to a user-chosen file via the
 * Storage Access Framework in Phase 4; importing is additive (new row ids).
 *
 * The AI API key is intentionally not part of any export.
 */
@Serializable
data class ArchiveExportFile(
    val app: String = "PromptSaz",
    val formatVersion: Int = 1,
    val exportedAt: Long,
    val prompts: List<PromptExportDto>,
)

@Serializable
data class PromptExportDto(
    val title: String,
    val promptText: String,
    val originalIdea: String,
    val domainId: String,
    val targetAi: String,
    val outputLanguage: String,
    val detailLevel: String,
    val score: Int,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val isAiGenerated: Boolean = false,
    val variantStyle: String = "standard",
    val createdAt: Long,
    val updatedAt: Long,
)

fun PromptEntity.toExportDto(): PromptExportDto = PromptExportDto(
    title = title,
    promptText = promptText,
    originalIdea = originalIdea,
    domainId = domainId,
    targetAi = targetAi,
    outputLanguage = outputLanguage,
    detailLevel = detailLevel,
    score = score,
    isFavorite = isFavorite,
    tags = tags,
    notes = notes,
    isAiGenerated = isAiGenerated,
    variantStyle = variantStyle,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PromptExportDto.toEntity(): PromptEntity = PromptEntity(
    id = 0L,
    groupId = "imported-${System.currentTimeMillis()}",
    title = title,
    promptText = promptText,
    originalIdea = originalIdea,
    domainId = domainId,
    targetAi = targetAi,
    outputLanguage = outputLanguage,
    detailLevel = detailLevel,
    score = score,
    isFavorite = isFavorite,
    tags = tags,
    notes = notes,
    isAiGenerated = isAiGenerated,
    variantStyle = variantStyle,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Direct mapping to the domain model for archive import. */
fun PromptExportDto.toArchived(): com.promptsaz.app.domain.model.ArchivedPrompt =
    com.promptsaz.app.domain.model.ArchivedPrompt(
        groupId = "imported-${System.currentTimeMillis()}",
        title = title,
        promptText = promptText,
        originalIdea = originalIdea,
        domainId = domainId,
        targetAi = com.promptsaz.app.domain.model.TargetAi.fromId(targetAi),
        outputLanguage = com.promptsaz.app.domain.model.OutputLanguage.fromId(outputLanguage),
        detailLevel = com.promptsaz.app.domain.model.DetailLevel.fromId(detailLevel),
        score = score,
        isFavorite = isFavorite,
        tags = tags,
        notes = notes,
        isAiGenerated = isAiGenerated,
        variantStyle = variantStyle,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
