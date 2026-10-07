package com.promptsaz.app.domain.provider

import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.GeneratedImage
import com.promptsaz.app.domain.model.UserAttachment

/**
 * Conversational + image-generation capabilities on top of the
 * OpenAI-compatible API. Implemented by [com.promptsaz.app.data.remote.OpenAiCompatibleProvider].
 */
interface ChatAiProvider {

    /**
     * One chat round-trip: sends the (already built) turn history to the
     * selected model and returns the assistant's reply text.
     * Multimodal models receive image turns as image_url content parts.
     */
    suspend fun chat(model: String, turns: List<ChatTurn>, serviceId: String? = null): Result<String>

    /**
     * Generates one image from [prompt]. [attachments] may carry reference
     * images (Gemini image editing) and text files (appended context).
     */
    suspend fun generateImage(
        model: String,
        prompt: String,
        size: String,
        serviceId: String? = null,
        attachments: List<UserAttachment> = emptyList(),
    ): Result<GeneratedImage>

    /** Downloads raw bytes of a generated-image URL (pre-signed, no auth). */
    suspend fun fetchImageBytes(url: String): Result<ByteArray>
}

/**
 * Thrown when the active service has no images endpoint (HTTP 404 on
 * {base}/images/generations) — e.g. text-only OpenAI-compatible proxies.
 * The تصویر tab uses it to offer the image-prompt fallback instead of a raw error.
 */
class ImageGenerationUnsupportedException(message: String) : IllegalStateException(message)
