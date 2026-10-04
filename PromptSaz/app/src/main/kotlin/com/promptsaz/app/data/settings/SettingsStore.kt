package com.promptsaz.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.promptsaz.app.domain.model.AiService
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
        val AI_IMAGE_MODEL = stringPreferencesKey("ai_image_model")
        val AI_SERVICES = stringPreferencesKey("ai_services")
        val ACTIVE_SERVICE_ID = stringPreferencesKey("active_service_id")
        val CHAT_SERVICE_ID = stringPreferencesKey("chat_service_id")
        val PROMPT_SERVICE_ID = stringPreferencesKey("prompt_service_id")
        val IMAGE_SERVICE_ID = stringPreferencesKey("image_service_id")
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
                aiServices = servicesOf(prefs),
                activeServiceId = prefs[Keys.ACTIVE_SERVICE_ID]
                    ?.takeIf { id -> servicesOf(prefs).any { it.id == id } }
                    ?: servicesOf(prefs).firstOrNull()?.id.orEmpty(),
                chatServiceId = prefs[Keys.CHAT_SERVICE_ID].orEmpty(),
                promptServiceId = prefs[Keys.PROMPT_SERVICE_ID].orEmpty(),
                imageServiceId = prefs[Keys.IMAGE_SERVICE_ID].orEmpty(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = set(Keys.THEME, mode.id)

    suspend fun setDefaultDomain(domainId: String) = set(Keys.DEFAULT_DOMAIN, domainId)

    suspend fun setDefaultTarget(target: TargetAi) = set(Keys.DEFAULT_TARGET, target.id)

    suspend fun setDefaultLanguage(language: OutputLanguage) = set(Keys.DEFAULT_LANGUAGE, language.id)

    suspend fun setDefaultDetail(level: DetailLevel) = set(Keys.DEFAULT_DETAIL, level.id)

    suspend fun setAiEnabled(enabled: Boolean) = set(Keys.AI_ENABLED, enabled.toString())

    // --- v1 setters: they now write into the ACTIVE service ------------------------

    suspend fun setAiBaseUrl(url: String) = editActiveService { it.copy(baseUrl = url.trim().trimEnd('/')) }

    suspend fun setAiModel(model: String) = editActiveService { it.copy(model = model.trim()) }

    suspend fun setAiImageModel(model: String) = editActiveService { it.copy(imageModel = model.trim()) }

    // --- multi-service CRUD ----------------------------------------------------------

    /** Adds a new service, makes it the active one and returns its id. */
    suspend fun addService(name: String, baseUrl: String, type: String): String {
        var newId = ""
        context.settingsDataStore.edit { prefs ->
            val services = servicesOf(prefs).toMutableList()
            val normalizedUrl = baseUrl.trim().trimEnd('/')
            val id = "svc-" + java.util.UUID.randomUUID().toString().take(8)
            services += AiService(
                id = id,
                name = name.trim().ifBlank { AiServiceCodec.hostOf(normalizedUrl) ?: "سرویس جدید" },
                baseUrl = normalizedUrl,
                type = type,
            )
            newId = id
            prefs[Keys.AI_SERVICES] = AiServiceCodec.encode(services)
            prefs[Keys.ACTIVE_SERVICE_ID] = id
        }
        return newId
    }

    suspend fun updateService(id: String, name: String, baseUrl: String, type: String) {
        context.settingsDataStore.edit { prefs ->
            val services = servicesOf(prefs).toMutableList()
            val index = services.indexOfFirst { it.id == id }
            if (index >= 0) {
                services[index] = services[index].copy(
                    name = name.trim().ifBlank { services[index].name },
                    baseUrl = baseUrl.trim().trimEnd('/'),
                    type = type,
                )
                prefs[Keys.AI_SERVICES] = AiServiceCodec.encode(services)
            }
        }
    }

    suspend fun removeService(id: String) {
        context.settingsDataStore.edit { prefs ->
            val services = servicesOf(prefs).toMutableList()
            val removedActive = prefs[Keys.ACTIVE_SERVICE_ID] == id || services.firstOrNull()?.id == id
            services.removeAll { it.id == id }
            prefs[Keys.AI_SERVICES] = AiServiceCodec.encode(services)
            if (removedActive) {
                prefs[Keys.ACTIVE_SERVICE_ID] = services.firstOrNull()?.id.orEmpty()
            }
        }
    }

    suspend fun setActiveService(id: String) {
        context.settingsDataStore.edit { prefs ->
            if (servicesOf(prefs).any { it.id == id }) {
                prefs[Keys.ACTIVE_SERVICE_ID] = id
            }
        }
    }

    /** Binds one section (chat / prompt / image) to a specific service. */
    suspend fun setModeService(modeId: String, serviceId: String) {
        context.settingsDataStore.edit { prefs ->
            val valid = serviceId.isBlank() || servicesOf(prefs).any { it.id == serviceId }
            if (!valid) return@edit
            val key = when (modeId) {
                AppSettings.MODE_CHAT -> Keys.CHAT_SERVICE_ID
                AppSettings.MODE_PROMPT -> Keys.PROMPT_SERVICE_ID
                AppSettings.MODE_IMAGE -> Keys.IMAGE_SERVICE_ID
                else -> return@edit
            }
            prefs[key] = serviceId
        }
    }

    /** Sets the chat/prompt (or image) model of ONE service. */
    suspend fun setServiceModel(serviceId: String, model: String, imageModel: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val services = servicesOf(prefs).toMutableList()
            val index = services.indexOfFirst { it.id == serviceId }
            if (index >= 0) {
                services[index] = if (imageModel) {
                    services[index].copy(imageModel = model.trim())
                } else {
                    services[index].copy(model = model.trim())
                }
                prefs[Keys.AI_SERVICES] = AiServiceCodec.encode(services)
            }
        }
    }

    /** Applies [block] to the active service (materializing the v1 default when needed). */
    private suspend fun editActiveService(block: (AiService) -> AiService) {
        context.settingsDataStore.edit { prefs ->
            val services = servicesOf(prefs).toMutableList()
            val activeId = prefs[Keys.ACTIVE_SERVICE_ID]
                ?.takeIf { id -> services.any { it.id == id } }
                ?: services.firstOrNull()?.id
            val index = services.indexOfFirst { it.id == activeId }
            if (index >= 0) {
                services[index] = block(services[index])
                prefs[Keys.AI_SERVICES] = AiServiceCodec.encode(services)
            }
        }
    }

    /** Current list from the store; falls back to the v1-derived single service. */
    private fun servicesOf(prefs: Preferences): List<AiService> =
        prefs[Keys.AI_SERVICES]?.let { AiServiceCodec.decode(it) }
            ?: listOf(
                AiServiceCodec.defaultFromLegacy(
                    baseUrl = prefs[Keys.AI_BASE_URL] ?: AppSettings.DEFAULT_AI_BASE_URL,
                    model = prefs[Keys.AI_MODEL] ?: "",
                    imageModel = prefs[Keys.AI_IMAGE_MODEL] ?: "",
                ),
            )

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        context.settingsDataStore.edit { prefs -> prefs[key] = value }
    }
}
