package com.promptsaz.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Native Pixazo image-gateway wire format (gateway.pixazo.ai):
 * POST {base}/text-to-image with the Ocp-Apim-Subscription-Key header and a
 * {prompt, size, n} body → the gateway answers with a queued job
 * (request_id + polling_url); polling GET on that URL ends with
 * status COMPLETED and output.media_url[0] pointing at the public image.
 * Pure Kotlin so it is unit-testable; the provider drives it.
 */
object PixazoWire {

    /** Auth header every Pixazo call must carry (their docs). */
    const val API_KEY_HEADER = "Ocp-Apim-Subscription-Key"

    // --- request -------------------------------------------------------------------

    @Serializable
    data class PixazoSubmitRequest(
        val prompt: String,
        val size: String,
        val n: Int = 1,
        val format: String = "png",
    )

    // --- responses -----------------------------------------------------------------

    @Serializable
    data class PixazoSubmitResponse(
        @SerialName("request_id") val requestId: String = "",
        val status: String = "",
        @SerialName("polling_url") val pollingUrl: String = "",
    )

    @Serializable
    data class PixazoStatusResponse(
        @SerialName("request_id") val requestId: String = "",
        val status: String = "",
        val error: String? = null,
        val output: PixazoOutput? = null,
    )

    @Serializable
    data class PixazoOutput(
        @SerialName("media_url") val mediaUrl: List<String> = emptyList(),
        @SerialName("media_type") val mediaType: String? = null,
    )

    // --- endpoints -----------------------------------------------------------------

    fun textToImageUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/text-to-image"

    /** Prefers the polling_url from the submit reply; falls back to the documented shape. */
    fun statusUrl(pollingUrl: String, requestId: String, baseUrl: String): String {
        if (pollingUrl.isNotBlank()) return pollingUrl
        val host = runCatching { java.net.URI(baseUrl.trim()).host }.getOrNull()
            ?: "gateway.pixazo.ai"
        return "https://$host/v2/requests/status/$requestId"
    }

    /** "https://gateway.pixazo.ai/gpt-image-2-5-flare/v1" → "gpt-image-2-5-flare". */
    fun parseModelId(baseUrl: String): String = runCatching {
        val segments = java.net.URI(baseUrl.trim()).path.orEmpty()
            .split('/')
            .filter { it.isNotBlank() }
        val versionIndex = segments.indexOfFirst { it == "v1" || it.startsWith("v1") }
        if (versionIndex > 0) segments[versionIndex - 1] else segments.lastOrNull()
    }.getOrNull() ?: "pixazo"

    // --- status machine --------------------------------------------------------------

    fun isCompleted(status: String): Boolean = status == "COMPLETED"

    fun isFailed(status: String): Boolean = status == "FAILED" || status == "ERROR"
}
