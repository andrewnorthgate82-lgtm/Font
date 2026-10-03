package com.promptsaz.app.data.repository

import com.promptsaz.app.data.settings.SettingsStore
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val store: SettingsStore,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = store.settings

    override suspend fun setThemeMode(mode: ThemeMode) = store.setThemeMode(mode)

    override suspend fun setDefaultDomain(domainId: String) = store.setDefaultDomain(domainId)

    override suspend fun setDefaultTarget(target: TargetAi) = store.setDefaultTarget(target)

    override suspend fun setDefaultLanguage(language: OutputLanguage) =
        store.setDefaultLanguage(language)

    override suspend fun setDefaultDetail(level: DetailLevel) = store.setDefaultDetail(level)

    override suspend fun setAiEnabled(enabled: Boolean) = store.setAiEnabled(enabled)

    override suspend fun setAiBaseUrl(url: String) = store.setAiBaseUrl(url)

    override suspend fun setAiModel(model: String) = store.setAiModel(model)
}
