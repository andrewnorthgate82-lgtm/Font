package com.promptsaz.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "prompt_saz_settings")

/**
 * Persists app settings via Preferences DataStore.
 *
 * The AI API key is deliberately NOT stored here — DataStore is a plain file;
 * the key goes to EncryptedSharedPreferences (SecureKeyStore) instead.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DEFAULT_DOMAIN = stringPreferencesKey("default_domain")
        val DEFAULT_TARGET = stringPreferencesKey("default_target_ai")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_output_language")
        val DEFAULT_DETAIL = stringPreferencesKey("default_detail_level")
        val AI_ENABLED = stringPreferencesKey("ai_enabled")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
    }

    /** Settings stream; falls back to defaults if the store is unreadable. */
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                themeMode = ThemeMode.fromId(prefs[Keys.THEME] ?: ThemeMode.SYSTEM.id),
                defaultDomainId = prefs[Keys.DEFAULT_DOMAIN] ?: "general",
                defaultTargetAi = TargetAi.fromId(prefs[Keys.DEFAULT_TARGET] ?: TargetAi.ANY.id),
                defaultOutputLanguage = OutputLanguage.fromId(
                    prefs[Keys.DEFAULT_LANGUAGE] ?: OutputLanguage.SAME_AS_INPUT.id,
                ),
                defaultDetailLevel = DetailLevel.fromId(
                    prefs[Keys.DEFAULT_DETAIL] ?: DetailLevel.STANDARD.id,
                ),
                aiEnabled = (prefs[Keys.AI_ENABLED] ?: "false") == "true",
                aiBaseUrl = prefs[Keys.AI_BASE_URL] ?: AppSettings.DEFAULT_AI_BASE_URL,
                aiModel = prefs[Keys.AI_MODEL] ?: "",
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = set(Keys.THEME, mode.id)

    suspend fun setDefaultDomain(domainId: String) = set(Keys.DEFAULT_DOMAIN, domainId)

    suspend fun setDefaultTarget(target: TargetAi) = set(Keys.DEFAULT_TARGET, target.id)

    suspend fun setDefaultLanguage(language: OutputLanguage) = set(Keys.DEFAULT_LANGUAGE, language.id)

    suspend fun setDefaultDetail(level: DetailLevel) = set(Keys.DEFAULT_DETAIL, level.id)

    suspend fun setAiEnabled(enabled: Boolean) = set(Keys.AI_ENABLED, enabled.toString())

    suspend fun setAiBaseUrl(url: String) = set(Keys.AI_BASE_URL, url.trim())

    suspend fun setAiModel(model: String) = set(Keys.AI_MODEL, model.trim())

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        context.settingsDataStore.edit { prefs -> prefs[key] = value }
    }
}
