package com.promptsaz.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ChatMessage
import com.promptsaz.app.domain.model.ImageGeneration

/** One chat conversation (the گفتگوی جدید tab). */
@Entity(
    tableName = "chat_conversations",
    indices = [Index("updatedAt")],
)
data class ChatConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One chat message; [imageFileName] points into filesDir/chat_images/. */
@Entity(
    tableName = "chat_messages",
    indices = [Index("conversationId"), Index("createdAt")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val conversationId: Long,
    val role: String,
    val text: String,
    val imageFileName: String? = null,
    val createdAt: Long,
)

/** One generated image (the تصویر tab history); [fileName] points into filesDir/generated_images/. */
@Entity(
    tableName = "image_generations",
    indices = [Index("createdAt")],
)
data class ImageGenerationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val prompt: String,
    val model: String,
    val fileName: String,
    val size: String,
    val createdAt: Long,
)

fun ChatConversationEntity.toDomain(): ChatConversation =
    ChatConversation(id = id, title = title, createdAt = createdAt, updatedAt = updatedAt)

fun ChatMessageEntity.toDomain(): ChatMessage =
    ChatMessage(
        id = id,
        conversationId = conversationId,
        role = role,
        text = text,
        imageFileName = imageFileName,
        createdAt = createdAt,
    )

fun ImageGenerationEntity.toDomain(): ImageGeneration =
    ImageGeneration(
        id = id,
        prompt = prompt,
        model = model,
        fileName = fileName,
        size = size,
        createdAt = createdAt,
    )
