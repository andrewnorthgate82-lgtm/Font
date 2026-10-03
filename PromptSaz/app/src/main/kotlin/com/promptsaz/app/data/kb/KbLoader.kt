package com.promptsaz.app.data.kb

import android.content.Context
import com.promptsaz.app.di.IoDispatcher
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.KbRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads knowledge-base JSON from app assets (assets/knowledge/).
 *
 * Files are read once and cached in memory for the process lifetime; they are
 * static assets, so there is no invalidation to worry about.
 */
@Singleton
class KbLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutex = Mutex()
    private var registryCache: KbRegistry? = null
    private val domainCache = mutableMapOf<String, DomainKnowledge?>()

    suspend fun loadRegistry(): KbRegistry = mutex.withLock {
        registryCache ?: withContext(ioDispatcher) {
            readJson(REGISTRY_FILE) { text ->
                json.decodeFromString<KbRegistry>(text)
            }.also { registryCache = it }
        }
    }

    suspend fun loadDomain(kbFile: String): DomainKnowledge? = mutex.withLock {
        if (domainCache.containsKey(kbFile)) return@withLock domainCache[kbFile]
        val loaded = withContext(ioDispatcher) {
            runCatching {
                readJson(kbFile) { text -> json.decodeFromString<DomainKnowledge>(text) }
            }.getOrNull()
        }
        domainCache[kbFile] = loaded
        loaded
    }

    private inline fun <T> readJson(
        fileName: String,
        decode: (String) -> T,
    ): T = context.assets.open("$KB_DIR/$fileName").use { stream ->
        decode(stream.readBytes().decodeToString())
    }

    private companion object {
        const val KB_DIR = "knowledge"
        const val REGISTRY_FILE = "_registry.json"
    }
}
