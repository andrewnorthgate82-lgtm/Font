package com.promptsaz.app.domain.model

/**
 * One complete generation result: the section IR, the rendered text,
 * its structural score, and which variant it is.
 */
data class GeneratedPrompt(
    val spec: PromptSpec,
    val title: String,
    val text: String,
    val sections: List<PromptSection>,
    val score: QualityReport,
    val variantStyle: VariantStyle,
    val isAiGenerated: Boolean = false,
)

enum class VariantStyle(val id: String, val labelFa: String) {
    CONCISE("concise", "فشرده"),
    STANDARD("standard", "استاندارد"),
    CREATIVE("creative", "خلاقانه"),
    ;

    companion object {
        fun fromId(value: String): VariantStyle = entries.firstOrNull { it.id == value } ?: STANDARD
    }
}
