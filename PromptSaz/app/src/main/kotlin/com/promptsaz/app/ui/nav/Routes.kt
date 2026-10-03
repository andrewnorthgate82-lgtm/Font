package com.promptsaz.app.ui.nav

/** Navigation routes. Arguments carry ids only; flow state lives in GenerationSession. */
object Routes {
    const val HOME = "home"
    const val CLARIFY = "clarify"
    const val RESULT = "result"
    const val ARCHIVE = "archive"
    const val SETTINGS = "settings"
    const val KB = "kb"
    const val KB_DOMAIN = "kb/{domainId}"

    fun kbDomain(domainId: String) = "kb/$domainId"

    const val PROMPT_DETAIL = "prompt/{id}"

    fun promptDetail(id: Long) = "prompt/$id"
}
