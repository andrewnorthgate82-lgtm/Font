package com.promptsaz.app.domain.model

/**
 * Weakness analysis of a user's existing prompt (Improve mode).
 */
data class ImprovementReport(
    val findings: List<ImprovementFinding>,
) {
    val criticalCount: Int get() = findings.count { it.severity == FindingSeverity.CRITICAL }
    val hasFindings: Boolean get() = findings.isNotEmpty()
}

enum class FindingSeverity(val labelFa: String) {
    CRITICAL("حیاتی"),
    MAJOR("مهم"),
    MINOR("پیشنهاد"),
}

data class ImprovementFinding(
    val severity: FindingSeverity,
    /** What is wrong, in Persian. */
    val messageFa: String,
    /** The deterministic fix, in Persian (used as the improvement suggestion). */
    val fixFa: String,
)
