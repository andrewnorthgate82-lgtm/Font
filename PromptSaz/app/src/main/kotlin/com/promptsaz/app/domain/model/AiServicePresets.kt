package com.promptsaz.app.domain.model

/**
 * One-tap templates for the "add service" dialog. A preset fills the name,
 * base URL and wire protocol so the user only has to paste a key.
 */
data class AiServicePreset(
    val id: String,
    val nameFa: String,
    val baseUrl: String,
    val type: String,
    val keyHintFa: String,
)

object AiServicePresets {

    const val CUSTOM_ID = "custom"

    val ALL: List<AiServicePreset> = listOf(
        AiServicePreset(
            id = "gemini",
            nameFa = "Google Gemini (AI Studio)",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            type = AiService.TYPE_GEMINI,
            keyHintFa = "کلید را از aistudio.google.com ← Get API key بگیر (با AIza… شروع می‌شود)",
        ),
        AiServicePreset(
            id = "codecraft",
            nameFa = "CodeCraft",
            baseUrl = "https://codecraftapi.com/v1",
            type = AiService.TYPE_OPENAI_COMPATIBLE,
            keyHintFa = "کلید cc_… از پنل CodeCraft",
        ),
        AiServicePreset(
            id = "openai",
            nameFa = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            type = AiService.TYPE_OPENAI_COMPATIBLE,
            keyHintFa = "کلید sk-… از platform.openai.com",
        ),
        AiServicePreset(
            id = "openrouter",
            nameFa = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            type = AiService.TYPE_OPENAI_COMPATIBLE,
            keyHintFa = "کلید sk-or-… از openrouter.ai/keys",
        ),
        AiServicePreset(
            id = "deepseek",
            nameFa = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            type = AiService.TYPE_OPENAI_COMPATIBLE,
            keyHintFa = "کلید sk-… از platform.deepseek.com",
        ),
        AiServicePreset(
            id = "groq",
            nameFa = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            type = AiService.TYPE_OPENAI_COMPATIBLE,
            keyHintFa = "کلید gsk_… از console.groq.com/keys",
        ),
        AiServicePreset(
            id = "ollama",
            nameFa = "Ollama (کامپیوتر خودت)",
            baseUrl = "http://localhost:11434/v1",
            type = AiService.TYPE_OPENAI_COMPATIBLE,
            keyHintFa = "کلید لازم نیست — Ollama را با OLLAMA_HOST=0.0.0.0 اجرا کن",
        ),
        AiServicePreset(
            id = CUSTOM_ID,
            nameFa = "سایر (سازگار با OpenAI)",
            baseUrl = "",
            type = AiService.TYPE_OPENAI_COMPATIBLE,
            keyHintFa = "هر سرویسی که مسیرهای /chat/completions و /models را دارد",
        ),
    )

    /** The preset matching a service (by protocol first, then base URL); custom as fallback. */
    fun matchOf(service: AiService): AiServicePreset? {
        if (service.isGemini) return ALL.firstOrNull { it.id == "gemini" }
        return ALL.firstOrNull { it.type == AiService.TYPE_OPENAI_COMPATIBLE && it.baseUrl == service.baseUrl }
    }
}
