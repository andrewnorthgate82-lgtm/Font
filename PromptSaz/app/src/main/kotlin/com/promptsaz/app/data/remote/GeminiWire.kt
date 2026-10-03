package com.promptsaz.app.data.remote

import com.promptsaz.app.domain.model.ChatTurn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Native Google Gemini wire format (generativelanguage.googleapis.com/v1beta):
 * POST {base}/models/{model}:generateContent with the X-goog-api-key header
 * and a contents/parts body. Kept pure Kotlin so it is unit-testable; the
 * provider calls into it when the active service is of type [TYPE_GEMINI].
 */
object GeminiWire {

    // --- request -------------------------------------------------------------------

    @Serializable
    data class GeminiGenerateRequest(
        val systemInstruction: GeminiSystemInstruction? = null,
        val contents: List<GeminiContent>,
        val generationConfig: GeminiGenerationConfig? = null,
    )

    @Serializable
    data class GeminiSystemInstruction(val parts: List<GeminiPart>)

    @Serializable
    data class GeminiGenerationConfig(
        val temperature: Double = 0.7,
        @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null,
    )

    @Serializable
    data class GeminiContent(
        val role: String? = null,
        val parts: List<GeminiPart>,
    )

    @Serializable
    data class GeminiPart(
        val text: String? = null,
        val inlineData: GeminiInlineData? = null,
    )

    @Serializable
    data class GeminiInlineData(val mimeType: String, val data: String)

    // --- responses -----------------------------------------------------------------

    @Serializable
    data class GeminiGenerateResponse(
        val candidates: List<GeminiCandidate> = emptyList(),
    )

    @Serializable
    data class GeminiCandidate(val content: GeminiContent? = null)

    @Serializable
    data class GeminiModelsResponse(
        val models: List<GeminiModelDto> = emptyList(),
    )

    @Serializable
    data class GeminiModelDto(
        val name: String = "",
        val supportedGenerationMethods: List<String> = emptyList(),
    )

    // --- endpoints -----------------------------------------------------------------

    fun generateContentUrl(baseUrl: String, model: String): String =
        baseUrl.trimEnd('/') + "/models/" + model + ":generateContent"

    fun modelsUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/models"

    /** Auth header name every Gemini call must carry (their curl example). */
    const val API_KEY_HEADER = "X-goog-api-key"

    // --- mapping -------------------------------------------------------------------

    /**
     * Converts the app's neutral [ChatTurn]s into a Gemini request: system
     * turns merge into systemInstruction, user/assistant turns become
     * contents (assistant → role "model") and image data URLs become
     * inlineData parts.
     */
    fun fromTurns(turns: List<ChatTurn>, maxOutputTokens: Int?): GeminiGenerateRequest {
        val systemParts = turns.filter { it.role == "system" }.mapNotNull { turn ->
            turn.text.takeIf { it.isNotBlank() }?.let { GeminiPart(text = it) }
        }
        val contents = turns
            .filter { it.role != "system" }
            .map { turn ->
                val parts = buildList {
                    if (turn.text.isNotBlank()) add(GeminiPart(text = turn.text))
                    turn.imageDataUrl?.let { dataUrl ->
                        splitDataUrl(dataUrl)?.let { (mime, data) ->
                            add(GeminiPart(inlineData = GeminiInlineData(mimeType = mime, data = data)))
                        }
                    }
                }
                GeminiContent(
                    role = if (turn.role == "assistant") "model" else "user",
                    parts = parts,
                )
            }
        return GeminiGenerateRequest(
            systemInstruction = systemParts.takeIf { it.isNotEmpty() }?.let { GeminiSystemInstruction(parts = it) },
            contents = contents,
            generationConfig = GeminiGenerationConfig(maxOutputTokens = maxOutputTokens),
        )
    }

    /** "data:image/jpeg;base64,AAAA" → ("image/jpeg" to "AAAA"); null when not a data URL. */
    fun splitDataUrl(dataUrl: String): Pair<String, String>? {
        if (!dataUrl.startsWith("data:") || !dataUrl.contains(",")) return null
        val mime = dataUrl.removePrefix("data:").substringBefore(';')
        val data = dataUrl.substringAfter(',')
        if (mime.isBlank() || data.isBlank()) return null
        return mime to data
    }

    /** First text of the first candidate; null when the reply carries no text. */
    fun replyText(body: String, json: Json): String? = runCatching {
        val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), body)
        parsed.candidates.firstOrNull()
            ?.content?.parts
            ?.firstNotNullOfOrNull { it.text }
    }.getOrNull()

    /** Model names from GET /models, filtered to text-capable generateContent models. */
    fun parseModelNames(body: String, json: Json): List<String> = runCatching {
        val parsed = json.decodeFromString(GeminiModelsResponse.serializer(), body)
        parsed.models
            .filter { dto -> dto.supportedGenerationMethods.isEmpty() || dto.supportedGenerationMethods.contains("generateContent") }
            .map { dto -> dto.name.removePrefix("models/") }
            .filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
