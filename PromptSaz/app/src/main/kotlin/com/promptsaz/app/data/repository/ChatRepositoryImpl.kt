package com.promptsaz.app.data.repository

import com.promptsaz.app.data.db.ChatDao
import com.promptsaz.app.data.db.ChatConversationEntity
import com.promptsaz.app.data.db.ChatMessageEntity
import com.promptsaz.app.data.db.decodeAttachments
import com.promptsaz.app.data.db.encodeAttachments
import com.promptsaz.app.data.db.toDomain
import com.promptsaz.app.data.files.ImageFileStore
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ChatAttachmentMeta
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ChatMessage
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.UserAttachment
import com.promptsaz.app.domain.provider.AiModePrompts
import com.promptsaz.app.domain.provider.ChatAiProvider
import com.promptsaz.app.domain.repository.ChatRepository
import com.promptsaz.app.domain.repository.SettingsRepository
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

    override suspend fun setFeedback(messageId: Long, feedback: Int) {
        chatDao.setMessageFeedback(messageId, feedback)
    }

    override suspend fun sendMessage(
        conversationId: Long,
        text: String,
        attachments: List<UserAttachment>,
    ): Result<Long> {
        val now = System.currentTimeMillis()
        val id = if (conversationId == 0L) createConversation() else conversationId

        if (attachments.sumOf { it.bytes.size } > UserAttachment.MAX_TOTAL_BYTES) {
            return Result.failure(IllegalStateException(ATTACHMENT_BUDGET_FA))
        }

        // 1) store every attachment locally; images travel compressed
        data class Stored(val upload: UserAttachment, val meta: ChatAttachmentMeta)
        val stored = attachments.mapNotNull { attachment ->
            val upload = if (attachment.isImage) {
                attachment.copy(bytes = imageStore.compressForUpload(attachment.bytes))
            } else {
                attachment
            }
            if (upload.bytes.isEmpty()) return@mapNotNull null
            val extension = if (attachment.isImage) {
                attachment.mimeType.substringAfterLast('/').ifBlank { "jpg" }
            } else {
                UserAttachment.extensionOf(attachment.displayName).ifBlank { "bin" }
            }
            val fileName = imageStore.saveChatImage(upload.bytes, extension)
            Stored(upload, ChatAttachmentMeta(attachment.displayName, fileName, attachment.mimeType))
        }
        val firstImageName = stored.firstOrNull()?.takeIf { it.upload.isImage }?.meta?.fileName
        val userMessageId = chatDao.insertMessage(
            ChatMessageEntity(
                conversationId = id,
                role = ChatMessage.ROLE_USER,
                text = text.trim(),
                imageFileName = firstImageName,
                attachmentsJson = encodeAttachments(stored.map { it.meta }),
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
        //    The گفتگو tab runs on ITS OWN bound service + model.
        val chatService = settingsRepository.settings.first().serviceFor(AppSettings.MODE_CHAT)
        if (chatService == null) {
            return Result.failure(IllegalStateException(NO_CHAT_SERVICE_FA))
        }
        val model = chatService.model
        val history = chatDao.messages(id).takeLast(HISTORY_LIMIT)
        val turns = buildList {
            add(ChatTurn(role = "system", text = AiModePrompts.CHAT_SYSTEM_PROMPT_FA))
            history.forEach { message ->
                val isLatestUserMessage = message.id == userMessageId
                val turnText = when {
                    isLatestUserMessage -> message.text
                    message.attachmentsJson != null -> {
                        val names = decodeAttachments(message.attachmentsJson)
                            .joinToString("، ") { it.displayName }
                        message.text.ifBlank { IMAGE_PLACEHOLDER_FA } +
                            " $ATTACHMENT_PLACEHOLDER_FA$names]"
                    }
                    message.imageFileName != null ->
                        message.text.ifBlank { IMAGE_PLACEHOLDER_FA } + " $IMAGE_PLACEHOLDER_FA"
                    else -> message.text
                }
                add(
                    ChatTurn(
                        role = message.role,
                        text = turnText,
                        attachments = if (isLatestUserMessage) stored.map { it.upload } else emptyList(),
                    ),
                )
                // The user rated this reply — feed the rating back into the
                // request so the model adapts its style within the conversation.
                if (message.role == ChatMessage.ROLE_ASSISTANT && message.feedback != ChatMessage.FEEDBACK_NONE) {
                    add(
                        ChatTurn(
                            role = "system",
                            text = if (message.feedback == ChatMessage.FEEDBACK_LIKE) {
                                FEEDBACK_LIKE_NOTE_FA
                            } else {
                                FEEDBACK_DISLIKE_NOTE_FA
                            },
                        ),
                    )
                }
            }
        }

        // 3) call the model and persist the reply
        return provider.chat(model, turns, chatService?.id).mapCatching { reply ->
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
        const val NO_CHAT_SERVICE_FA = "اول سرویس گفتگو را در تنظیمات انتخاب کن."
        const val IMAGE_PLACEHOLDER_FA = "[تصویر پیوست‌شده]"
        const val ATTACHMENT_PLACEHOLDER_FA = "[پیوست‌ها: "
        const val ATTACHMENT_BUDGET_FA =
            "حجم کل پیوست‌ها بیشتر از حد مجاز API (۲۰ مگابایت) است؛ تعدادی از فایل‌ها را حذف کن یا سبک‌ترشان کن."
        const val FEEDBACK_LIKE_NOTE_FA =
            "(بازخورد کاربر به پاسخ بالا: این پاسخ را پسندید؛ پاسخ‌های بعدی به همین سبک و کیفیت باشند.)"
        const val FEEDBACK_DISLIKE_NOTE_FA =
            "(بازخورد کاربر به پاسخ بالا: این پاسخ را نپسندید؛ از ایرادهای همین پاسخ پرهیز کن و دقیق‌تر، مرتب‌تر و مفیدتر پاسخ بده.)"
    }
}
