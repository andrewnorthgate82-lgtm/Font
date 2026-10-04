package com.promptsaz.app.domain.usecase

import com.promptsaz.app.domain.engine.PromptEngine
import com.promptsaz.app.domain.engine.assembler.PromptAssembler
import com.promptsaz.app.domain.engine.improve.PromptAnalyzer
import com.promptsaz.app.domain.engine.improve.PromptRewriter
import com.promptsaz.app.domain.engine.score.QualityScorer
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.GeneratedPrompt
import com.promptsaz.app.domain.model.ImprovementReport
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.VariantStyle
import com.promptsaz.app.domain.provider.ProviderRegistry
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.repository.PromptRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import com.promptsaz.app.domain.usecase.toArchived
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Improve-existing-prompt mode: analyze weaknesses, then rewrite.
 * Offline: heuristic analysis + structured rewrite. AI mode: the provider
 * improves the text using the same 10-part anatomy system prompt.
 * The result is auto-saved to the archive.
 */
@Singleton
class ImprovePromptUseCase @Inject constructor(
    private val analyzer: PromptAnalyzer,
    private val rewriter: PromptRewriter,
    private val engine: PromptEngine,
    private val scorer: QualityScorer,
    private val assembler: PromptAssembler,
    private val kbRepository: KbRepository,
    private val promptRepository: PromptRepository,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: ProviderRegistry,
) {

    data class Outcome(
        val report: ImprovementReport,
        val result: GeneratedPrompt,
        val savedId: Long,
        val groupId: String,
        val aiErrorFa: String? = null,
    )

    suspend operator fun invoke(spec: PromptSpec): Outcome {
        require(spec.mode == PromptMode.IMPROVE) { "ImprovePromptUseCase requires IMPROVE mode" }
        val original = spec.idea
        val report = analyzer.analyze(original, spec)
        val kb: DomainKnowledge? = kbRepository.getDomain(spec.domainId)
        val settings = settingsRepository.settings.first()

        var aiError: String? = null
        var result: GeneratedPrompt? = null

        if (settings.aiEnabled) {
            val provider = providerRegistry.active()
            val serviceId = settings.serviceFor(AppSettings.MODE_PROMPT)?.id
            if (provider != null && provider.isConfigured(serviceId)) {
                provider.generatePrompt(spec, kb, serviceId)
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
                    .onFailure { error -> aiError = error.message }
            }
        }

        val finalResult = result ?: run {
            val sections = rewriter.rewrite(original, spec, kb)
            GeneratedPrompt(
                spec = spec,
                title = assembler.makeTitle(spec, kb?.nameFa),
                text = engine.render(sections, spec),
                sections = sections,
                score = scorer.scoreSections(sections, spec),
                variantStyle = VariantStyle.STANDARD,
            )
        }

        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val savedId = promptRepository.save(finalResult.toArchived(groupId, now, now))
        return Outcome(report, finalResult, savedId, groupId, aiError)
    }
}
