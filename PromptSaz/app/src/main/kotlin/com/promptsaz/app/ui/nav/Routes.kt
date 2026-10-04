package com.promptsaz.app.ui.nav

object Routes {
    /**
     * Top-level sections (menu navigation — the bottom bar is gone).
     * CHAT/IMAGE take an optional deep-link argument so the app menu can open
     * a specific conversation / generation directly.
     */
    const val CHAT = "chat?conversationId={conversationId}"
    const val CHAT_TAB = "chat"
    const val HOME = "home" // تولید پرامپت
    const val IMAGE = "image?generationId={generationId}"
    const val IMAGE_TAB = "image"

    fun chat(conversationId: Long) = "chat?conversationId=$conversationId"

    fun image(generationId: Long) = "image?generationId=$generationId"

    const val CLARIFY = "clarify"
    const val RESULT = "result"
    const val ARCHIVE = "archive"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val KB = "kb"
    const val KB_DOMAIN = "kb/{domainId}"

    fun kbDomain(domainId: String) = "kb/$domainId"

    const val PROMPT_DETAIL = "prompt/{id}"

    fun promptDetail(id: Long) = "prompt/$id"
}
