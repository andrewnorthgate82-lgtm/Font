package com.promptsaz.app.domain.engine

import com.promptsaz.app.domain.engine.assembler.PromptAssembler
import com.promptsaz.app.domain.engine.clarify.ClarificationEngine
import com.promptsaz.app.domain.engine.score.QualityScorer
import com.promptsaz.app.domain.engine.style.ClaudeStyleAdapter
import com.promptsaz.app.domain.engine.style.GeminiStyleAdapter
import com.promptsaz.app.domain.engine.style.ImageModelStyleAdapter
import com.promptsaz.app.domain.engine.style.MarkdownStyleAdapter
import com.promptsaz.app.domain.engine.style.TargetStyleAdapter
import com.promptsaz.app.domain.engine.style.VideoModelStyleAdapter
import com.promptsaz.app.domain.engine.variant.VariantGenerator
import com.promptsaz.app.domain.model.ClarifyingQuestion
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.GeneratedPrompt
import com.promptsaz.app.domain.model.PromptSection
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.VariantStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over the whole offline pipeline:
 * clarify → assemble (IR) → variants → target render → score.
 */
@Singleton
class PromptEngine @Inject constructor(
    private val clarificationEngine: ClarificationEngine,
    private val assembler: PromptAssembler,
    private val scorer: QualityScorer,
    private val variants: VariantGenerator,
) {

    private val adapters: Map<TargetAi, TargetStyleAdapter> = mapOf(
        TargetAi.CHATGPT to MarkdownStyleAdapter(),
        TargetAi.DEEPSEEK to MarkdownStyleAdapter(),
        TargetAi.ANY to MarkdownStyleAdapter(),
        TargetAi.GEMINI to GeminiStyleAdapter(),
        TargetAi.CLAUDE to ClaudeStyleAdapter(),
        TargetAi.MIDJOURNEY to ImageModelStyleAdapter(),
        TargetAi.VIDEO_GENERATORS to VideoModelStyleAdapter(),
    )

    fun clarifyingQuestions(kb: DomainKnowledge?, idea: String): List<ClarifyingQuestion> =
        clarificationEngine.questionsFor(kb, idea)

    /** Standard generation: assemble → render → score. */
    fun generate(spec: PromptSpec, kb: DomainKnowledge?): GeneratedPrompt {
        val sections = assembler.assemble(spec, kb)
        return GeneratedPrompt(
            spec = spec,
            title = assembler.makeTitle(spec, kb?.nameFa),
            text = render(sections, spec),
            sections = sections,
            score = scorer.scoreSections(sections, spec),
            variantStyle = VariantStyle.STANDARD,
        )
    }

    /** Builds a specific variant of an existing generation from its IR. */
    fun variantOf(base: GeneratedPrompt, style: VariantStyle): GeneratedPrompt {
        val sections = when (style) {
            VariantStyle.STANDARD -> base.sections
            VariantStyle.CONCISE -> variants.concise(base.sections)
            VariantStyle.CREATIVE -> variants.creative(base.sections)
        }
        return GeneratedPrompt(
            spec = base.spec,
            title = base.title,
            text = render(sections, base.spec),
            sections = sections,
            score = scorer.scoreSections(sections, base.spec),
            variantStyle = style,
            isAiGenerated = base.isAiGenerated,
        )
    }

    /** Renders the IR for the spec's target AI. */
    fun render(sections: List<PromptSection>, spec: PromptSpec): String =
        adapters.getValue(spec.targetAi).render(sections, spec)

    fun adapterFor(target: TargetAi): TargetStyleAdapter = adapters.getValue(target)
}
