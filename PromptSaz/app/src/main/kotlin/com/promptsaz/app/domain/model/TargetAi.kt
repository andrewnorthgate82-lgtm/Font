package com.promptsaz.app.domain.model

/**
 * Target AI the generated prompt is optimized for.
 *
 * Each id maps 1:1 to a style adapter in the prompt engine (Phase 3):
 * markdown sections, XML tags, grouped instructions, image/video descriptors.
 *
 * Display names keep the Latin product names (the only English allowed in the UI,
 * per the product spec); Persian hint text is shown alongside in the UI.
 */
enum class TargetAi(
    val id: String,
    val labelFa: String,
    val isImageOrVideoModel: Boolean,
) {
    CHATGPT(id = "chatgpt", labelFa = "ChatGPT", isImageOrVideoModel = false),
    CLAUDE(id = "claude", labelFa = "Claude", isImageOrVideoModel = false),
    GEMINI(id = "gemini", labelFa = "Gemini", isImageOrVideoModel = false),
    DEEPSEEK(id = "deepseek", labelFa = "DeepSeek", isImageOrVideoModel = false),
    MIDJOURNEY(id = "midjourney", labelFa = "Midjourney", isImageOrVideoModel = true),
    VIDEO_GENERATORS(id = "video", labelFa = "ویدیوسازها (Sora، Runway، Kling)", isImageOrVideoModel = true),
    ANY(id = "any", labelFa = "هر مدلی", isImageOrVideoModel = false),
    ;

    companion object {
        fun fromId(value: String): TargetAi = entries.firstOrNull { it.id == value } ?: ANY
    }
}
