package com.promptsaz.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Models for the editable knowledge bases shipped as JSON assets
 * (app/src/main/assets/knowledge/). See docs/KNOWLEDGE_BASE_GUIDE.md and
 * assets/knowledge/kb.schema.json for the authoritative shape.
 *
 * Adding a domain = adding one JSON file + one registry entry. No code changes.
 */

@Serializable
data class KbRegistry(
    val version: Int,
    val domains: List<KbDomainEntry>,
)

@Serializable
data class KbDomainEntry(
    val id: String,
    val nameFa: String,
    val descriptionFa: String,
    /** "ready" = full knowledge base shipped; "coming_soon" = roadmap entry. */
    val status: String = "coming_soon",
    /** File name inside assets/knowledge/; null for coming_soon entries. */
    val kbFile: String? = null,
) {
    val isReady: Boolean get() = status == "ready"
}

@Serializable
data class DomainKnowledge(
    val id: String,
    val nameFa: String,
    val status: String,
    val personas: List<KbPersona>,
    val terminology: List<KbTerm>,
    val outputStructures: List<KbOutputStructure>,
    val guardrails: List<KbGuardrail>,
    val failureModes: List<KbFailureMode>,
    val examples: List<KbExample>,
    val clarifyingQuestions: List<KbClarifyingQuestion>,
)

@Serializable
data class KbPersona(
    val id: String,
    val titleFa: String,
    val expertiseFa: String,
    val whenToUseFa: String,
)

@Serializable
data class KbTerm(
    val termFa: String,
    val termEn: String,
    val definitionFa: String,
)

@Serializable
data class KbOutputStructure(
    val id: String,
    val titleFa: String,
    val descriptionFa: String,
    val templateFa: String,
)

@Serializable
data class KbGuardrail(
    val id: String,
    /** What to do — phrased positively (the primary instruction). */
    val doFa: String,
    /** What to avoid — explicit prohibition where genuinely needed. */
    val dontFa: String,
    /** Why it matters; used by the engine to pick relevant guardrails. */
    val whyFa: String,
)

@Serializable
data class KbFailureMode(
    val id: String,
    val symptomFa: String,
    val fixFa: String,
)

@Serializable
data class KbExample(
    val id: String,
    val titleFa: String,
    /** Target AI id (see TargetAi); "any" means model-agnostic. */
    val targetAi: String,
    /** A complete, high-quality example prompt in Persian. */
    val promptFa: String,
)

@Serializable
data class KbClarifyingQuestion(
    val id: String,
    val questionFa: String,
    /** Lower = asked earlier (1 = first). The engine shows at most 4 questions. */
    val priority: Int,
    val chips: List<KbChip> = emptyList(),
    val allowFreeText: Boolean = true,
    /** Keywords that, when present in the idea, mark this gap as already answered. */
    val gapKeywordsFa: List<String> = emptyList(),
)

@Serializable
data class KbChip(
    val id: String,
    val labelFa: String,
)
