package com.promptsaz.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun messages(conversationId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insertConversation(conversation: ChatConversationEntity): Long

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun renameConversation(conversationId: Long, title: String, updatedAt: Long)

    @Query("UPDATE chat_conversations SET updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun touchConversation(conversationId: Long, updatedAt: Long)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesOf(conversationId: Long)

    @Query("DELETE FROM chat_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)
}

@Dao
interface ImageGenerationDao {

    @Query("SELECT * FROM image_generations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImageGenerationEntity>>

    @Insert
    suspend fun insert(generation: ImageGenerationEntity): Long

    @Query("DELETE FROM image_generations WHERE id = :generationId")
    suspend fun delete(generationId: Long)
}
