package com.promptsaz.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the AI-mode API key with EncryptedSharedPreferences
 * (AES-256 key in the Android Keystore, AES-256-GCM values, AES-256-SIV keys).
 *
 * The key NEVER leaves this class except as a masked string for the UI, and is
 * never included in exports, backups or logs. If the Android Keystore becomes
 * corrupted (a known OEM failure mode), the store deletes itself and starts
 * empty rather than crashing — the user simply re-enters the key.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApiKeyStore {

    private val prefs: SharedPreferences? = createPrefsSafely()

    val isAvailable: Boolean get() = prefs != null

    /** Returns true when a non-blank key is stored. */
    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    /** Raw key — used only by the network layer. Never log or export this value. */
    fun getApiKey(): String? = prefs?.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    fun saveApiKey(value: String) {
        prefs?.edit()?.putString(KEY_API, value.trim())?.apply()
    }

    fun clearApiKey() {
        prefs?.edit()?.remove(KEY_API)?.apply()
    }

    /**
     * Masked form for the UI, e.g. «cc_nNl8••••••••t3» — first 6 and last 4
     * characters only. Returns null when no key is stored.
     */
    fun maskApiKey(): String? {
        val key = getApiKey() ?: return null
        if (key.length <= 10) return "•".repeat(key.length)
        return key.take(6) + "•".repeat(8) + key.takeLast(4)
    }

    private fun createPrefsSafely(): SharedPreferences? =
        runCatching { createPrefs() }
            .recoverCatching {
                // Keystore/_prefs corrupted: wipe and retry once with fresh keys.
                context.deleteSharedPreferences(FILE_NAME)
                resetMasterKey()
                createPrefs()
            }
            .getOrNull()

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun resetMasterKey() {
        runCatching {
            val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        }
    }

    private companion object {
        const val FILE_NAME = "prompt_saz_secure"
        const val KEY_API = "ai_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    }
}
