package com.promptsaz.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {

    /**
     * Archive list with search + filters. The empty-string/NULL guards keep a
     * single query shape for every filter combination the UI needs.
     */
    @Query(
        """
        SELECT * FROM prompts
        WHERE (:query = '' OR title LIKE '%' || :query || '%'
                        OR promptText LIKE '%' || :query || '%'
                        OR originalIdea LIKE '%' || :query || '%'
                        OR tags LIKE '%' || :query || '%')
          AND (:domainId IS NULL OR domainId = :domainId)
          AND (:favoritesOnly = 0 OR isFavorite = 1)
        ORDER BY isFavorite DESC, updatedAt DESC
        """,
    )
    fun observePrompts(
        query: String,
        domainId: String?,
        favoritesOnly: Boolean,
    ): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE id = :id")
    fun observePrompt(id: Long): Flow<PromptEntity?>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getPrompt(id: Long): PromptEntity?

    @Query("SELECT * FROM prompts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PromptEntity>

    /** Raw tags JSON per row; parsed in the repository (deduplicated in memory). */
    @Query("SELECT tags FROM prompts")
    suspend fun getAllTagsRaw(): List<String>

    @Query("SELECT COUNT(*) FROM prompts")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(prompt: PromptEntity): Long

    @Upsert
    suspend fun upsertAll(prompts: List<PromptEntity>)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM prompts")
    suspend fun deleteAll()

    @Query(
        "UPDATE prompts SET isFavorite = :favorite, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)
}
