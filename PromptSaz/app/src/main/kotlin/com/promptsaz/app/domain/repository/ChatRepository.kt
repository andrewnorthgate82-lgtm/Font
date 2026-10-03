package com.promptsaz.app.domain.repository

import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ChatMessage
import java.io.InputStream

/**
 * Chat conversations + messages (Room-backed) and the send flow:
 * persist user message → call the model → persist the reply.
 */
interface ChatRepository {

    /** All conversations, newest first. */
    fun conversations(): kotlinx.coroutines.flow.Flow<List<ChatConversation>>

    /** Messages of one conversation, oldest first. */
    fun messages(conversationId: Long): kotlinx.coroutines.flow.Flow<List<ChatMessage>>

    /** Creates an untitled conversation and returns its id. */
    suspend fun createConversation(): Long

    /** Renames (auto-titles) a conversation from its first user message. */
    suspend fun autoTitle(conversationId: Long)

    suspend fun deleteConversation(conversationId: Long)

    /** Stores the user's like/dislike rating of one message (0 clears it). */
    suspend fun setFeedback(messageId: Long, feedback: Int)

    /**
     * Sends [text] (optionally with an attached image stream) in the given
     * conversation and returns the assistant's reply. Persists both messages.
     * When [conversationId] is 0 a new conversation is created.
     */
    suspend fun sendMessage(
        conversationId: Long,
        text: String,
        image: InputStream?,
        imageExtension: String?,
    ): Result<Long>

    /** Reads a stored chat image as raw bytes (for the UI). */
    fun readImage(fileName: String): ByteArray?
}
