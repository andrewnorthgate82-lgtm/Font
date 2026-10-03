package com.promptsaz.app.data.repository

import com.promptsaz.app.data.db.PromptDao
import com.promptsaz.app.data.db.toDomain
import com.promptsaz.app.data.db.toEntity
import com.promptsaz.app.di.IoDispatcher
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.repository.PromptRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class PromptRepositoryImpl @Inject constructor(
    private val dao: PromptDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PromptRepository {

    override fun observePrompts(
        query: String,
        domainId: String?,
        favoritesOnly: Boolean,
    ): Flow<List<ArchivedPrompt>> =
        dao.observePrompts(query.trim(), domainId?.takeIf { it.isNotBlank() }, favoritesOnly)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observePrompt(id: Long): Flow<ArchivedPrompt?> =
        dao.observePrompt(id).map { it?.toDomain() }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun getPrompt(id: Long): ArchivedPrompt? = withContext(ioDispatcher) {
        dao.getPrompt(id)?.toDomain()
    }

    override suspend fun getAll(): List<ArchivedPrompt> = withContext(ioDispatcher) {
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun save(prompt: ArchivedPrompt): Long = withContext(ioDispatcher) {
        dao.upsert(prompt.toEntity())
    }

    override suspend fun insertAll(prompts: List<ArchivedPrompt>) = withContext(ioDispatcher) {
        dao.upsertAll(prompts.map { it.toEntity().copy(id = 0L) })
    }

    override suspend fun update(prompt: ArchivedPrompt) = withContext(ioDispatcher) {
        require(prompt.id != 0L) { "Cannot update a prompt without an id" }
        dao.upsert(prompt.toEntity())
    }

    override suspend fun delete(id: Long) = withContext(ioDispatcher) {
        dao.deleteById(id)
    }

    override suspend fun toggleFavorite(id: Long) = withContext(ioDispatcher) {
        dao.getPrompt(id)?.let { row ->
            dao.setFavorite(id, !row.isFavorite, System.currentTimeMillis())
        }
    }

    override suspend fun duplicate(id: Long): Long? = withContext(ioDispatcher) {
        val original = dao.getPrompt(id) ?: return@withContext null
        val now = System.currentTimeMillis()
        val copy = original.toDomain().copy(
            id = 0L,
            title = "${original.title} (کپی)",
            isFavorite = false,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(copy.toEntity())
    }

    override suspend fun allTags(): List<String> = withContext(ioDispatcher) {
        dao.getAll().flatMap { it.tags }.distinct().sorted()
    }
}
