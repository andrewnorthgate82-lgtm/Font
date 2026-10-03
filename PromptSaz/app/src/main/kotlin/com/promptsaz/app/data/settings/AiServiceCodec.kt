package com.promptsaz.app.data.settings

import com.promptsaz.app.domain.model.AiService
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Encodes/decodes the AI-service list for DataStore and derives the initial
 * service from the v1 single-service settings. Pure Kotlin — unit-tested.
 */
object AiServiceCodec {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AiService.serializer())

    fun encode(services: List<AiService>): String = json.encodeToString(serializer, services)

    /** null when the stored value is unreadable (corrupt/old format). */
    fun decode(value: String): List<AiService>? = runCatching {
        json.decodeFromString(serializer, value)
    }.getOrNull()

    /**
     * The service every v1 user already has: their single base URL + models,
     * named after the URL host so the settings list stays readable.
     */
    fun defaultFromLegacy(baseUrl: String, model: String, imageModel: String): AiService =
        AiService(
            id = AiService.LEGACY_DEFAULT_ID,
            name = hostOf(baseUrl) ?: "سرویس من",
            baseUrl = baseUrl.trim().trimEnd('/'),
            model = model.trim(),
            imageModel = imageModel.trim(),
        )

    /** "https://codecraftapi.com/v1" → "codecraftapi.com"; null when unparsable. */
    fun hostOf(baseUrl: String): String? = runCatching {
        val url = java.net.URI(baseUrl.trim())
        url.host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
