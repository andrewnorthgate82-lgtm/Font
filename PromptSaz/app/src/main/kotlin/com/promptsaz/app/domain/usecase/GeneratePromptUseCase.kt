package com.promptsaz.app.domain.usecase

import com.promptsaz.app.domain.engine.PromptEngine
import com.promptsaz.app.domain.engine.score.QualityScorer
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.GeneratedPrompt
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.VariantStyle
import com.promptsaz.app.domain.provider.ProviderRegistry
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.repository.PromptRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * One generation pass: offline engine by default; AI mode when the user has
 * enabled it AND the provider is configured. AI failures degrade gracefully
 * to the offline engine and surface their Persian error to the UI.
 * Every result is auto-saved to the archive (product spec).
 */
@Singleton
class GeneratePromptUseCase @Inject constructor(
    private val engine: PromptEngine,
    private val scorer: QualityScorer,
    private val kbRepository: KbRepository,
    private val promptRepository: PromptRepository,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: ProviderRegistry,
) {

    data class Outcome(
        val result: GeneratedPrompt,
        val savedId: Long,
        /** Persian error message when AI mode failed and we fell back offline. */
        val aiErrorFa: String? = null,
    )

    suspend operator fun invoke(spec: PromptSpec): Outcome {
        val kb = kbRepository.getDomain(spec.domainId)
        val settings = settingsRepository.settings.first()

        var aiError: String? = null
        var result: GeneratedPrompt? = null

        if (settings.aiEnabled) {
            val provider = providerRegistry.active()
            if (provider == null) {
                aiError = "هیچ سرویس هوش مصنوعی نصب نیست؛ با موتور آفلاین ساخته شد."
            } else if (!provider.isConfigured()) {
                aiError = "حالت هوش مصنوعی فعال است اما کلید یا مدل تنظیم نشده؛ با موتور آفلاین ساخته شد."
            } else {
                provider.generatePrompt(spec, kb)
                    .onSuccess { generation ->
                        result = GeneratedPrompt(
                            spec = spec,
                            title = generation.title.trim().take(40),
                            text = generation.prompt.trim(),
                            sections = emptyList(),
                            score = scorer.scoreText(generation.prompt, spec),
                            variantStyle = VariantStyle.STANDARD,
                            isAiGenerated = true,
                        )
                    }
                    .onFailure { error ->
                        aiError = error.message ?: "ارتباط با سرور هوش مصنوعی برقرار نشد."
                    }
            }
        }

        val finalResult = result ?: engine.generate(spec, kb)
        val groupId = UUID.randomUUID().toString()
        val savedId = save(finalResult, groupId)
        return Outcome(result = finalResult, savedId = savedId, groupId = groupId, aiErrorFa = aiError)
    }

    /** Saves a variant generated on the Result screen. */
    suspend fun saveVariant(variant: GeneratedPrompt, groupId: String): Long =
        promptRepository.save(variant.toArchived(groupId))

    private suspend fun save(result: GeneratedPrompt): Long {
        val now = System.currentTimeMillis()
        return promptRepository.save(
            result.toArchived(UUID.randomUUID().toString(), createdAt = now, updatedAt = now),
        )
    }
}

/** Maps a generation into its archive representation. */
fun GeneratedPrompt.toArchived(groupId: String, createdAt: Long, updatedAt: Long): ArchivedPrompt {
    val domain = ArchivedPrompt(
        groupId = groupId,
        title = title,
        promptText = text,
        originalIdea = spec.idea,
        domainId = spec.domainId,
        targetAi = spec.targetAi,
        outputLanguage = spec.outputLanguage,
        detailLevel = spec.detailLevel,
        score = score.total,
        isFavorite = false,
        tags = emptyList(),
        notes = "",
        isAiGenerated = isAiGenerated,
        variantStyle = variantStyle.id,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
    return domain
}
