package com.promptsaz.app.data.settings

/**
 * Read/write contract for the per-service AI API keys. SecureKeyStore
 * implements it with EncryptedSharedPreferences; tests provide a plain fake
 * so the whole request chain (settings → key → HTTP body) can be verified on
 * the JVM.
 */
interface ApiKeyStore {
    /** Returns true when a non-blank key is stored for [serviceId]. */
    fun hasApiKey(serviceId: String): Boolean

    /** Raw key — used only by the network layer. Never log or export this value. */
    fun getApiKey(serviceId: String): String?

    fun saveApiKey(serviceId: String, value: String)

    fun clearApiKey(serviceId: String)
}
