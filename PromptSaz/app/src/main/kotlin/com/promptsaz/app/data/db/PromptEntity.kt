package com.promptsaz.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi

/**
 * Archive row. One row = one generated prompt (or one variant of it;
 * variants of the same generation share [groupId]).
 *
 * [tags] is converted to a JSON array string by [Converters].
 */
@Entity(
    tableName = "prompts",
    indices = [
        Index("createdAt"),
        Index("domainId"),
        Index("isFavorite"),
        Index("groupId"),
    ],
)
data class PromptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val groupId: String,
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

/** Maps the database row to its domain representation. */
fun PromptEntity.toDomain(): ArchivedPrompt = ArchivedPrompt(
    id = id,
    groupId = groupId,
    title = title,
    promptText = promptText,
    originalIdea = originalIdea,
    domainId = domainId,
    targetAi = TargetAi.fromId(targetAi),
    outputLanguage = OutputLanguage.fromId(outputLanguage),
    detailLevel = DetailLevel.fromId(detailLevel),
    score = score,
    isFavorite = isFavorite,
    tags = tags,
    notes = notes,
    isAiGenerated = isAiGenerated,
    variantStyle = variantStyle,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps a domain prompt to a database row. */
fun ArchivedPrompt.toEntity(): PromptEntity = PromptEntity(
    id = id,
    groupId = groupId,
    title = title,
    promptText = promptText,
    originalIdea = originalIdea,
    domainId = domainId,
    targetAi = targetAi.id,
    outputLanguage = outputLanguage.id,
    detailLevel = detailLevel.id,
    score = score,
    isFavorite = isFavorite,
    tags = tags,
    notes = notes,
    isAiGenerated = isAiGenerated,
    variantStyle = variantStyle,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
