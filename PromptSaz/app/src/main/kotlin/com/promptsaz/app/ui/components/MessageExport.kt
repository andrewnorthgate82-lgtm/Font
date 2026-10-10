package com.promptsaz.app.ui.components

/**
 * Turns an assistant reply into a real file on the phone — the چیستا
 * equivalent of the download button in ChatGPT / Gemini.
 *
 * The delivery contract with the model (see CHAT_SYSTEM_PROMPT_FA): when the
 * user asks for a file, the model must reply with the COMPLETE final content
 * inside one fenced code block (```markdown … ```). These helpers pick that
 * block out of the reply and suggest the file name the model mentioned.
 */
object MessageExport {

    /** Extensions we are willing to auto-detect from the reply text. */
    private val FILENAME_REGEX = Regex(
        "[\\w\\-\\u0600-\\u06FF][\\w\\-.\\u0600-\\u06FF ]{0,60}" +
            "\\.(md|markdown|txt|json|csv|html?|xml|ya?ml|py|js|ts|kt|java|sql|sh|css)\\b",
    )

    /**
     * The content to save: the LARGEST fenced code block when the model
     * wrapped a file in ``` fences, otherwise the whole reply. An unclosed
     * fence (truncated reply) still exports everything that follows it.
     */
    fun exportContent(text: String): String {
        val fenced = Regex(
            "```[a-zA-Z0-9+#.\\-]*[ \\t]*\\r?\\n(.*?)(?:\\r?\\n)?(?:```|$)",
            RegexOption.DOT_MATCHES_ALL,
        )
            .findAll(text)
            .maxByOrNull { it.groupValues[1].length }
            ?.groupValues?.get(1)
        return (fenced ?: text).trimIndent().trim() + "\n"
    }

    /**
     * Suggested file name for the save dialog: a name the model actually
     * mentioned (e.g. «chista-knowledge-base-FINAL.md»), else a Persian
     * default. Path separators are stripped so it is a plain display name.
     */
    fun suggestedFileName(text: String): String {
        val mentioned = FILENAME_REGEX.findAll(text)
            .map { it.value.trim() }
            .firstOrNull { it.length in 3..80 }
            ?.substringAfterLast('/')
            ?.replace(Regex("[\\\\/:*?\"<>|]"), "")
            ?.trim()
        return if (!mentioned.isNullOrBlank()) mentioned else DEFAULT_FILE_NAME
    }

    const val DEFAULT_FILE_NAME = "پاسخ-چیستا.md"
}
