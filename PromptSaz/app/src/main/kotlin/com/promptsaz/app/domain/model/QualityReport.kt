package com.promptsaz.app.domain.model

/**
 * Structural-completeness score («امتیاز کامل بودن ساختار») — 0..100 with a
 * per-section breakdown and deterministic one-tap improvement suggestions.
 * It measures the structure of the prompt, never the target model's answer.
 */
data class QualityReport(
    val total: Int,
    val sectionScores: List<SectionScore>,
    val suggestionsFa: List<String>,
) {
    val isGood: Boolean get() = total >= 85
}

data class SectionScore(
    val kind: SectionKind,
    val titleFa: String,
    val earned: Int,
    val max: Int,
    val present: Boolean,
    val noteFa: String,
)
