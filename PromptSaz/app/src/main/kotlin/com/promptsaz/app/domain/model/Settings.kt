package com.promptsaz.app.domain.model

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
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultDomainId: String = "general",
    val defaultTargetAi: TargetAi = TargetAi.ANY,
    val defaultOutputLanguage: OutputLanguage = OutputLanguage.SAME_AS_INPUT,
    val defaultDetailLevel: DetailLevel = DetailLevel.STANDARD,
    val aiEnabled: Boolean = false,
    val aiBaseUrl: String = DEFAULT_AI_BASE_URL,
    val aiModel: String = "",
    /** Last model used in the تصویر tab (image models often differ from chat models). */
    val aiImageModel: String = "",
) {
    companion object {
        /**
         * Default suggestion for the OpenAI-compatible provider base URL.
         * A URL is not a secret, so it may ship in code; the API key never does.
         */
        const val DEFAULT_AI_BASE_URL: String = "https://codecraftapi.com/v1"
    }
}
