package com.promptsaz.app.ui.nav

object Routes {
    /** Top-level tabs (bottom bar). */
    const val CHAT = "chat"
    const val HOME = "home" // تولید پرامپت
    const val IMAGE = "image"

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
