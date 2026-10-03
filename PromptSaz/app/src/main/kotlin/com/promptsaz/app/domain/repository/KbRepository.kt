package com.promptsaz.app.domain.repository

import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.KbDomainEntry
import com.promptsaz.app.domain.model.KbRegistry
import kotlinx.coroutines.flow.Flow

/**
 * Access to the domain knowledge bases (editable JSON assets).
 */
interface KbRepository {

    /** The full registry (ready + coming_soon domains). */
    suspend fun getRegistry(): KbRegistry

    /** Registry as a cold Flow (emits once from cache). */
    fun registryFlow(): Flow<KbRegistry>

    /** Ready domains only — these are selectable in the main flow. */
    suspend fun getReadyDomains(): List<KbDomainEntry>

    /**
     * Loads the knowledge base of a domain, or null when the domain is unknown
     * or marked coming_soon. Never throws for missing files; failures degrade
     * to the general knowledge base at the call site.
     */
    suspend fun getDomain(domainId: String): DomainKnowledge?
}
