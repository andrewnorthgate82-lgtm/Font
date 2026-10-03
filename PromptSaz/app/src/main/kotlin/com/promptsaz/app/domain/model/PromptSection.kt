package com.promptsaz.app.domain.model

/**
 * The intermediate representation: one of the 10 anatomy parts, still structured.
 * Style adapters render a list of sections into the final prompt text.
 */
enum class SectionKind {
    ROLE,
    OBJECTIVE,
    CONTEXT,
    INPUTS,
    PROCESS,
    OUTPUT_FORMAT,
    CONSTRAINTS,
    EXAMPLES,
    QUALITY,
    CLARIFICATION,
}

data class PromptSection(
    val kind: SectionKind,
    val titleFa: String,
    val body: String,
)
