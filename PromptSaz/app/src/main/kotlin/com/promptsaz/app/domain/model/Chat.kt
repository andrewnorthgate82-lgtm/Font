package com.promptsaz.app.domain.model

/**
 * Chat-mode domain models (the ChatGPT-like "گفتگوی جدید" tab).
 */

/**
 * Persisted metadata of one chat attachment; the bytes live in the chat
 * file store under [fileName].
 */
data class ChatAttachmentMeta(
    val displayName: String,
    val fileName: String,
    val mimeType: String,
)

/** One conversation in the chat list. */
data class ChatConversation(
    val id: Long = 0L,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One message inside a conversation. [imageFileName] points to a local file. */
data class ChatMessage(
    val id: Long = 0L,
    val conversationId: Long,
    val role: String, // "user" | "assistant"
    val text: String,
    val imageFileName: String? = null,
    val createdAt: Long,
    /** User rating of this message: [FEEDBACK_NONE], [FEEDBACK_LIKE] or [FEEDBACK_DISLIKE]. */
    val feedback: Int = FEEDBACK_NONE,
    /** Metadata of every attached file (any type) stored for this message. */
    val attachments: List<ChatAttachmentMeta> = emptyList(),
) {
    val isFromUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val FEEDBACK_NONE = 0
        const val FEEDBACK_LIKE = 1
        const val FEEDBACK_DISLIKE = -1
    }
}

/**
 * One turn sent to the AI. The image travels as a ready data URL
 * (data:image/jpeg;base64,…); the repository decides which messages carry
 * their image so the request payload stays small.
 */
data class ChatTurn(
    val role: String,
    val text: String,
    val imageDataUrl: String? = null,
    /** Any-type attachments of this turn; the provider maps them per protocol. */
    val attachments: List<UserAttachment> = emptyList(),
)

/** Result of an image-generation call: either a remote URL or inline base64. */
sealed interface GeneratedImage {
    data class FromUrl(val url: String) : GeneratedImage
    data class FromBase64(val base64: String) : GeneratedImage
}

/** One saved image generation (history of the تصویر tab). */
data class ImageGeneration(
    val id: Long = 0L,
    val prompt: String,
    val model: String,
    val fileName: String,
    val size: String,
    val createdAt: Long,
)
