package com.promptsaz.app.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the OpenAI-compatible chat-completions API
 * (POST {base}/chat/completions, GET {base}/models, Authorization: Bearer).
 */
@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.7,
)

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto> = emptyList(),
) {
    @Serializable
    data class ChoiceDto(val message: ChatMessageDto? = null)
}

@Serializable
data class ModelsResponseDto(
    val data: List<ModelDto> = emptyList(),
) {
    @Serializable
    data class ModelDto(val id: String = "")
}

// --- Chat mode (multimodal) ------------------------------------------------

/**
 * A chat message whose content is either a plain string or an array of
 * typed parts (text / image_url) — serialized as raw JSON.
 */
@Serializable
data class ChatContentMessageDto(val role: String, val content: kotlinx.serialization.json.JsonElement)

@Serializable
data class ChatCompletionChatRequestDto(
    val model: String,
    val messages: List<ChatContentMessageDto>,
    val temperature: Double = 0.7,
)

@Serializable
data class ContentPartDto(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrlDto? = null,
)

@Serializable
data class ImageUrlDto(val url: String)

// --- Image generation --------------------------------------------------------

@Serializable
data class ImageGenerationRequestDto(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
)

@Serializable
data class ImageGenerationResponseDto(
    val data: List<GeneratedImageDto> = emptyList(),
) {
    @Serializable
    data class GeneratedImageDto(
        val url: String? = null,
        @kotlinx.serialization.SerialName("b64_json") val b64Json: String? = null,
    )
}

/** Expected strict-JSON reply from the model: {"title": "...", "prompt": "..."} */
@Serializable
data class GenerationReplyDto(
    val title: String? = null,
    val prompt: String? = null,
    val error: String? = null,
)
