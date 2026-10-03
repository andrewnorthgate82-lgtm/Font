package com.promptsaz.app.domain.repository

import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDefaultDomain(domainId: String)
    suspend fun setDefaultTarget(target: TargetAi)
    suspend fun setDefaultLanguage(language: OutputLanguage)
    suspend fun setDefaultDetail(level: DetailLevel)
    suspend fun setAiEnabled(enabled: Boolean)

    // v1 single-service setters — they operate on the ACTIVE service
    suspend fun setAiBaseUrl(url: String)
    suspend fun setAiModel(model: String)
    suspend fun setAiImageModel(model: String)

    // multi-service management
    /** Adds a service (becomes active) and returns its id. [type] is an AiService.TYPE_*. */
    suspend fun addService(name: String, baseUrl: String, type: String = AiService.TYPE_OPENAI_COMPATIBLE): String
    suspend fun updateService(
        id: String,
        name: String,
        baseUrl: String,
        type: String = AiService.TYPE_OPENAI_COMPATIBLE,
    )
    suspend fun removeService(id: String)
    suspend fun setActiveService(id: String)
}
