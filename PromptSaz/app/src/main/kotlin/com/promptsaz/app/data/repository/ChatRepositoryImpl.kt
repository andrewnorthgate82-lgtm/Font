package com.promptsaz.app.data.repository

import com.promptsaz.app.data.db.ChatDao
import com.promptsaz.app.data.db.ChatConversationEntity
import com.promptsaz.app.data.db.ChatMessageEntity
import com.promptsaz.app.data.db.toDomain
import com.promptsaz.app.data.files.ImageFileStore
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ChatMessage
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.provider.AiModePrompts
import com.promptsaz.app.domain.provider.ChatAiProvider
import com.promptsaz.app.domain.repository.ChatRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Chat send flow: persist the user's message, build the multimodal turn
 * history (only the newest message carries its image, so payloads stay
 * small), call the selected model and persist the reply.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val provider: ChatAiProvider,
    private val imageStore: ImageFileStore,
    private val settingsRepository: SettingsRepository,
) : ChatRepository {

    override fun conversations(): Flow<List<ChatConversation>> =
        chatDao.observeConversations().map { list -> list.map { it.toDomain() } }

    override fun messages(conversationId: Long): Flow<List<ChatMessage>> =
        chatDao.observeMessages(conversationId).map { list -> list.map { it.toDomain() } }

    override suspend fun createConversation(): Long {
        val now = System.currentTimeMillis()
        return chatDao.insertConversation(
            ChatConversationEntity(title = UNTITLED_FA, createdAt = now, updatedAt = now),
        )
    }

    override suspend fun autoTitle(conversationId: Long) {
        val firstUser = chatDao.messages(conversationId).firstOrNull { it.role == ChatMessage.ROLE_USER }
        if (firstUser != null) {
            val title = firstUser.text.replace("\n", " ").trim()
                .take(TITLE_MAX_CHARS).ifBlank { UNTITLED_FA }
            chatDao.renameConversation(conversationId, title, System.currentTimeMillis())
        }
    }

    override suspend fun deleteConversation(conversationId: Long) {
        chatDao.deleteMessagesOf(conversationId)
        chatDao.deleteConversation(conversationId)
    }

    override suspend fun sendMessage(
        conversationId: Long,
        text: String,
        image: InputStream?,
        imageExtension: String?,
    ): Result<Long> {
        val now = System.currentTimeMillis()
        val id = if (conversationId == 0L) createConversation() else conversationId

        // 1) persist the user's message; the image is compressed and stored locally
        var uploadDataUrl: String? = null
        val savedImageName = image?.use { stream ->
            val raw = stream.readBytes()
            if (raw.isEmpty()) {
                null
            } else {
                val compressed = imageStore.compressForUpload(raw)
                uploadDataUrl = imageStore.toDataUrl(compressed)
                imageStore.saveChatImage(compressed, imageExtension ?: "jpg")
            }
        }
        val userMessageId = chatDao.insertMessage(
            ChatMessageEntity(
                conversationId = id,
                role = ChatMessage.ROLE_USER,
                text = text.trim(),
                imageFileName = savedImageName,
                createdAt = now,
            ),
        )
        chatDao.touchConversation(id, now)

        // first user message of the conversation → use it as the title
        val firstUserMessage = chatDao.messages(id).firstOrNull { it.role == ChatMessage.ROLE_USER }
        if (firstUserMessage?.id == userMessageId) {
            autoTitle(id)
        }

        // 2) build the turn history: system prompt + last N messages.
        //    Only the message just sent carries its image as a data URL;
        //    older attachments are referenced with a placeholder.
        val model = settingsRepository.settings.first().aiModel
        val history = chatDao.messages(id).takeLast(HISTORY_LIMIT)
        val turns = buildList {
            add(ChatTurn(role = "system", text = AiModePrompts.CHAT_SYSTEM_PROMPT_FA))
            history.forEach { message ->
                val isLatestUserImage = message.id == userMessageId
                val turnText = when {
                    isLatestUserImage -> message.text
                    message.imageFileName != null ->
                        message.text.ifBlank { IMAGE_PLACEHOLDER_FA } + " $IMAGE_PLACEHOLDER_FA"
                    else -> message.text
                }
                add(
                    ChatTurn(
                        role = message.role,
                        text = turnText,
                        imageDataUrl = if (isLatestUserImage) uploadDataUrl else null,
                    ),
                )
            }
        }

        // 3) call the model and persist the reply
        return provider.chat(model, turns).mapCatching { reply ->
            val replyId = chatDao.insertMessage(
                ChatMessageEntity(
                    conversationId = id,
                    role = ChatMessage.ROLE_ASSISTANT,
                    text = reply.trim(),
                    imageFileName = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            chatDao.touchConversation(id, System.currentTimeMillis())
            replyId
        }
    }

    override fun readImage(fileName: String): ByteArray? = imageStore.readChatImage(fileName)

    companion object {
        const val HISTORY_LIMIT = 24
        const val TITLE_MAX_CHARS = 48
        const val UNTITLED_FA = "گفتگوی جدید"
        const val IMAGE_PLACEHOLDER_FA = "[تصویر پیوست‌شده]"
    }
}
