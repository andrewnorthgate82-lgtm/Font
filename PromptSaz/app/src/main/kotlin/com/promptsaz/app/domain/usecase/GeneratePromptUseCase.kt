package com.promptsaz.app.domain.usecase

import com.promptsaz.app.domain.engine.PromptEngine
import com.promptsaz.app.domain.engine.score.QualityScorer
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.DomainKnowledge
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
        /** groupId shared by all variants of this generation. */
        val groupId: String,
        /** Persian error message when AI mode failed and we fell back offline. */
        val aiErrorFa: String? = null,
    )

    suspend operator fun invoke(spec: PromptSpec): Outcome {
        val kb = kbRepository.getDomain(spec.domainId)
        val settings = settingsRepository.settings.first()

        val attempt = tryAi(spec, kb, settings)
        val finalResult = attempt.result ?: engine.generate(spec, kb)
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val savedId = promptRepository.save(finalResult.toArchived(groupId, now, now))
        return Outcome(result = finalResult, savedId = savedId, groupId = groupId, aiErrorFa = attempt.errorFa)
    }

    /**
     * AI-ONLY regeneration for the Result screen (بهبود ساختار / دوباره
     * ساخت): when the model call fails — e.g. the key's quota just ran out —
     * it must NEVER replace an existing AI prompt with a degraded offline
     * rewrite. Nothing is saved on failure; the Persian reason comes back so
     * the user knows exactly what happened and can retry later.
     */
    suspend fun regenerateAiOnly(spec: PromptSpec): Regeneration {
        val kb = kbRepository.getDomain(spec.domainId)
        val settings = settingsRepository.settings.first()

        val attempt = tryAi(spec, kb, settings)
        val result = attempt.result
            ?: return Regeneration.Failed(
                attempt.errorFa ?: "حالت هوش مصنوعی فعال نیست؛ در تنظیمات روشن کن.",
            )
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val savedId = promptRepository.save(result.toArchived(groupId, now, now))
        return Regeneration.Saved(result = result, savedId = savedId, groupId = groupId)
    }

    /** Saves a variant generated on the Result screen. */
    suspend fun saveVariant(variant: GeneratedPrompt, groupId: String): Long {
        val now = System.currentTimeMillis()
        return promptRepository.save(variant.toArchived(groupId, now, now))
    }

    /** One AI attempt for the تولید پرامپت section — result null = failed. */
    private suspend fun tryAi(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        settings: AppSettings,
    ): AiAttempt {
        if (!settings.aiEnabled) return AiAttempt(null, null)
        val provider = providerRegistry.active()
        // تولید پرامپت runs on ITS OWN bound service (per-section AI).
        val serviceId = settings.serviceFor(AppSettings.MODE_PROMPT)?.id
        if (provider == null) {
            return AiAttempt(null, "هیچ سرویس هوش مصنوعی نصب نیست؛ با موتور آفلاین ساخته شد.")
        }
        if (serviceId == null) {
            return AiAttempt(null, "سرویس بخش پرامپت در تنظیمات انتخاب نشده؛ با موتور آفلاین ساخته شد.")
        }
        if (!provider.isConfigured(serviceId)) {
            return AiAttempt(null, "حالت هوش مصنوعی فعال است اما کلید یا مدل تنظیم نشده؛ با موتور آفلاین ساخته شد.")
        }
        val generation = provider.generatePrompt(spec, kb, serviceId)
        val generated = generation.getOrNull()
        return if (generated != null) {
            AiAttempt(
                result = GeneratedPrompt(
                    spec = spec,
                    title = generated.title.trim().take(40),
                    text = generated.prompt.trim(),
                    sections = emptyList(),
                    score = scorer.scoreText(generated.prompt, spec),
                    variantStyle = VariantStyle.STANDARD,
                    isAiGenerated = true,
                ),
                errorFa = null,
            )
        } else {
            AiAttempt(
                null,
                generation.exceptionOrNull()?.message ?: "ارتباط با سرور هوش مصنوعی برقرار نشد.",
            )
        }
    }

    private data class AiAttempt(
        val result: GeneratedPrompt?,
        val errorFa: String?,
    )
}

/** Result of an AI-only regeneration (never downgrades to the offline engine). */
sealed interface Regeneration {
    data class Saved(val result: GeneratedPrompt, val savedId: Long, val groupId: String) : Regeneration
    data class Failed(val messageFa: String) : Regeneration
}

/** Maps a generation into its archive representation. */
fun GeneratedPrompt.toArchived(groupId: String, createdAt: Long, updatedAt: Long): ArchivedPrompt =
    ArchivedPrompt(
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
