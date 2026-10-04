package com.promptsaz.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Theme preference for the app (default follows the system).
 */
enum class ThemeMode(val id: String, val labelFa: String) {
    SYSTEM(id = "system", labelFa = "هماهنگ با سیستم"),
    LIGHT(id = "light", labelFa = "روشن"),
    DARK(id = "dark", labelFa = "تیره"),
    ;

    companion object {
        fun fromId(value: String): ThemeMode = entries.firstOrNull { it.id == value } ?: SYSTEM
    }
}

/**
 * User-facing app settings. Persisted via DataStore; the AI API key is NOT here —
 * it lives only in EncryptedSharedPreferences (see SecureKeyStore) and is never
 * exported or backed up.
 */
/**
 * One configured AI service (OpenAI-compatible): a name the user gives it,
 * its base URL, its API key id reference and its own model picks. Users can
 * keep several services side by side and switch the active one anytime.
 */
@Serializable
data class AiService(
    val id: String,
    val name: String,
    val baseUrl: String,
    /** Wire protocol: [TYPE_OPENAI_COMPATIBLE] (default) or [TYPE_GEMINI]. */
    val type: String = TYPE_OPENAI_COMPATIBLE,
    /** Chat/prompt model of THIS service. */
    val model: String = "",
    /** Image model of THIS service (falls back to [model]). */
    val imageModel: String = "",
) {
    val isGemini: Boolean get() = type == TYPE_GEMINI
    val isPixazo: Boolean get() = type == TYPE_PIXAZO

    companion object {
        /** Id of the service auto-created from the v1 single-service settings. */
        const val LEGACY_DEFAULT_ID = "default"

        /** OpenAI-compatible wire format ({base}/chat/completions, Bearer auth). */
        const val TYPE_OPENAI_COMPATIBLE = "openai_compatible"

        /** Google Gemini native format ({base}/models/{model}:generateContent, X-goog-api-key). */
        const val TYPE_GEMINI = "gemini"

        /** Pixazo image gateway ({base}/text-to-image + job polling, Ocp-Apim-Subscription-Key). */
        const val TYPE_PIXAZO = "pixazo"
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultDomainId: String = "general",
    val defaultTargetAi: TargetAi = TargetAi.ANY,
    val defaultOutputLanguage: OutputLanguage = OutputLanguage.SAME_AS_INPUT,
    val defaultDetailLevel: DetailLevel = DetailLevel.STANDARD,
    val aiEnabled: Boolean = false,
    /** All configured AI services. */
    val aiServices: List<AiService> = emptyList(),
    /** Legacy v1 field — kept only to migrate old installs; never shown in UI. */
    val activeServiceId: String = "",
    /**
     * Per-section service bindings — each section (گفتگو / تولید پرامپت /
     * تولید تصویر) owns its service. There is NO global default anymore.
     */
    val chatServiceId: String = "",
    val promptServiceId: String = "",
    val imageServiceId: String = "",
) {
    /** Legacy single-service view (v1 provider defaults). */
    val activeService: AiService?
        get() = aiServices.firstOrNull { it.id == activeServiceId } ?: aiServices.firstOrNull()

    /**
     * The service a section calls right now: its OWN binding — or, when only
     * one service exists, that one (there is nothing else to choose) — never
     * an implicit global default.
     */
    fun serviceFor(modeId: String): AiService? {
        val binding = when (modeId) {
            MODE_CHAT -> chatServiceId
            MODE_PROMPT -> promptServiceId
            MODE_IMAGE -> imageServiceId
            else -> return null
        }
        aiServices.firstOrNull { it.id == binding }
            ?: aiServices.takeIf { it.size == 1 }?.firstOrNull()
    }

    /** Views kept for the v1 single-service consumers (provider, view models). */
    val aiBaseUrl: String get() = activeService?.baseUrl?.trim()?.trimEnd('/') ?: ""
    val aiModel: String get() = activeService?.model ?: ""
    val aiImageModel: String get() = activeService?.imageModel ?: ""

    companion object {
        /**
         * Default suggestion for the OpenAI-compatible provider base URL.
         * A URL is not a secret, so it may ship in code; the API key never does.
         */
        const val DEFAULT_AI_BASE_URL: String = "https://codecraftapi.com/v1"

        /** Section ids for [serviceFor] / setModeService. */
        const val MODE_CHAT = "chat"
        const val MODE_PROMPT = "prompt"
        const val MODE_IMAGE = "image"
    }
}
