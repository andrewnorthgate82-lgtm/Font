package com.promptsaz.app.data.repository

import com.promptsaz.app.data.kb.KbLoader
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.model.KbRegistry
import com.promptsaz.app.domain.repository.KbRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class KbRepositoryImpl @Inject constructor(
    private val loader: KbLoader,
) : KbRepository {

    override suspend fun getRegistry(): KbRegistry = loader.loadRegistry()

    override fun registryFlow(): Flow<KbRegistry> = flow { emit(loader.loadRegistry()) }

    override suspend fun getReadyDomains(): List<KbDomainEntry> =
        loader.loadRegistry().domains.filter { it.isReady }

    override suspend fun getDomain(domainId: String): DomainKnowledge? {
        val entry = loader.loadRegistry().domains
            .firstOrNull { it.id == domainId && it.isReady } ?: return null
        val file = entry.kbFile ?: return null
        return loader.loadDomain(file)
    }
}
