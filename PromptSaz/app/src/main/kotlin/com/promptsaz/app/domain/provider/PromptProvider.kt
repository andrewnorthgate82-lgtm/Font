package com.promptsaz.app.domain.provider

import com.promptsaz.app.domain.model.ClarifyingQuestion
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.PromptSpec

/**
 * Plug point for AI mode. Implementations live in data/remote and register
 * themselves via Hilt multibinding (@IntoSet in di/ProviderModule).
 *
 * To add a new provider (e.g. a native Gemini client):
 *  1. implement this interface in data/remote,
 *  2. add an @Provides @IntoSet function in di/ProviderModule,
 *  3. (optional) surface it in Settings — the registry lists everything installed.
 */
interface PromptProvider {

    /** Stable id, e.g. "openai_compatible". */
    val id: String

    /** Persian label shown in Settings. */
    val displayNameFa: String

    /** True when the user has entered everything needed to call the API. */
    suspend fun isConfigured(serviceId: String? = null): Boolean

    /** Hits the provider's health/models endpoint; failures carry the exact
     *  HTTP status and error body so the Settings UI can show them in Persian. */
    suspend fun testConnection(serviceId: String? = null): ProviderHealth

    /** Model ids for the picker; falls back to a manual entry on failure. */
    suspend fun listModels(serviceId: String? = null): Result<List<String>>

    /** Generates one prompt for the spec, following AiModePrompts.SYSTEM_PROMPT. */
    suspend fun generatePrompt(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        serviceId: String? = null,
    ): Result<ProviderGeneration>
}

/** Successful generation from a provider. */
data class ProviderGeneration(
    val title: String,
    val prompt: String,
)

sealed interface ProviderHealth {
    /** Connection healthy; models is the fetched model list when available. */
    data class Ok(val models: List<String> = emptyList()) : ProviderHealth

    /** Persian message including the exact HTTP status and error body when present. */
    data class Failed(val messageFa: String) : ProviderHealth
}
