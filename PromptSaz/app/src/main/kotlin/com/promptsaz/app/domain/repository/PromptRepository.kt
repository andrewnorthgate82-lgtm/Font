package com.promptsaz.app.domain.repository

import com.promptsaz.app.domain.model.ArchivedPrompt
import kotlinx.coroutines.flow.Flow

/**
 * Archive access. Room is the single source of truth; every generated prompt is
 * auto-saved here (product spec).
 */
interface PromptRepository {

    /** Archive stream with search text, optional domain filter and favorites-only switch. */
    fun observePrompts(
        query: String = "",
        domainId: String? = null,
        favoritesOnly: Boolean = false,
    ): Flow<List<ArchivedPrompt>>

    fun observePrompt(id: Long): Flow<ArchivedPrompt?>

    fun observeCount(): Flow<Int>

    suspend fun getPrompt(id: Long): ArchivedPrompt?

    /** All rows, newest first — used by archive export. */
    suspend fun getAll(): List<ArchivedPrompt>

    /** Saves a new prompt (id = 0) and returns the new row id. */
    suspend fun save(prompt: ArchivedPrompt): Long

    /** Bulk insert for archive import. */
    suspend fun insertAll(prompts: List<ArchivedPrompt>)

    /** Updates an existing prompt (edit title/text/tags/notes/favorite). */
    suspend fun update(prompt: ArchivedPrompt)

    suspend fun delete(id: Long)

    suspend fun toggleFavorite(id: Long)

    /** Duplicates a prompt («… (کپی)») and returns the new row id. */
    suspend fun duplicate(id: Long): Long?

    /** Distinct tags across the archive, for tag autocomplete. */
    suspend fun allTags(): List<String>
}
