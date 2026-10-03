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

/** Expected strict-JSON reply from the model: {"title": "...", "prompt": "..."} */
@Serializable
data class GenerationReplyDto(
    val title: String? = null,
    val prompt: String? = null,
    val error: String? = null,
)
