package com.promptsaz.app.domain.provider

import javax.inject.Inject
import javax.inject.Singleton

/**
 * All installed PromptProviders (Hilt multibinding). AI mode uses the active
 * provider; Settings can list everything registered here.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards PromptProvider>,
) {

    val all: List<PromptProvider> = providers.sortedBy { it.id }

    /** The single provider wired into AI mode in v1.0. */
    fun active(): PromptProvider? = providers.firstOrNull { it.id == ACTIVE_PROVIDER_ID }

    companion object {
        const val ACTIVE_PROVIDER_ID = "openai_compatible"
    }
}
