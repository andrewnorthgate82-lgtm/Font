package com.promptsaz.app.domain.engine.style

import com.promptsaz.app.domain.model.PromptSection
import com.promptsaz.app.domain.model.PromptSpec

/**
 * Renders the section IR into the final prompt text for one target AI.
 * Implementations: markdown (ChatGPT/DeepSeek/Any), Gemini grouping,
 * Claude XML tags, and image/video descriptive adapters.
 */
interface TargetStyleAdapter {
    fun render(sections: List<PromptSection>, spec: PromptSpec): String
}

/** Markdown with `## ` headers — ChatGPT, DeepSeek and «هر مدلی». */
class MarkdownStyleAdapter : TargetStyleAdapter {
    override fun render(sections: List<PromptSection>, spec: PromptSpec): String =
        sections.joinToString("\n\n") { section -> "## ${section.titleFa}\n${section.body}" }
}

/** Gemini: three grouped blocks — Instructions / Context / Response Format. */
class GeminiStyleAdapter : TargetStyleAdapter {
    override fun render(sections: List<PromptSection>, spec: PromptSpec): String {
        val byKind = sections.associateBy { it.kind }
        fun block(title: String, kinds: List<com.promptsaz.app.domain.model.SectionKind>): String {
            val bodies = kinds.mapNotNull { byKind[it] }.map { "**${it.titleFa}:** ${it.body}" }
            return if (bodies.isEmpty()) "" else "## $title\n" + bodies.joinToString("\n\n")
        }
        return listOf(
            block(
                "دستورالعمل",
                listOf(
                    com.promptsaz.app.domain.model.SectionKind.ROLE,
                    com.promptsaz.app.domain.model.SectionKind.OBJECTIVE,
                    com.promptsaz.app.domain.model.SectionKind.PROCESS,
                    com.promptsaz.app.domain.model.SectionKind.CONSTRAINTS,
                    com.promptsaz.app.domain.model.SectionKind.QUALITY,
                    com.promptsaz.app.domain.model.SectionKind.CLARIFICATION,
                ),
            ),
            block(
                "زمینه",
                listOf(
                    com.promptsaz.app.domain.model.SectionKind.CONTEXT,
                    com.promptsaz.app.domain.model.SectionKind.INPUTS,
                    com.promptsaz.app.domain.model.SectionKind.EXAMPLES,
                ),
            ),
            block("قالب پاسخ", listOf(com.promptsaz.app.domain.model.SectionKind.OUTPUT_FORMAT)),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }
}

/** Claude: each part wrapped in XML-style tags. */
class ClaudeStyleAdapter : TargetStyleAdapter {
    override fun render(sections: List<PromptSection>, spec: PromptSpec): String =
        sections.joinToString("\n\n") { section ->
            "<${section.kind.tag}>\n${section.body}\n</${section.kind.tag}>"
        }

    private val com.promptsaz.app.domain.model.SectionKind.tag: String
        get() = when (this) {
            com.promptsaz.app.domain.model.SectionKind.ROLE -> "role"
            com.promptsaz.app.domain.model.SectionKind.OBJECTIVE -> "objective"
            com.promptsaz.app.domain.model.SectionKind.CONTEXT -> "context"
            com.promptsaz.app.domain.model.SectionKind.INPUTS -> "inputs"
            com.promptsaz.app.domain.model.SectionKind.PROCESS -> "process"
            com.promptsaz.app.domain.model.SectionKind.OUTPUT_FORMAT -> "output_format"
            com.promptsaz.app.domain.model.SectionKind.CONSTRAINTS -> "constraints"
            com.promptsaz.app.domain.model.SectionKind.EXAMPLES -> "examples"
            com.promptsaz.app.domain.model.SectionKind.QUALITY -> "quality_criteria"
            com.promptsaz.app.domain.model.SectionKind.CLARIFICATION -> "clarification"
        }
}
