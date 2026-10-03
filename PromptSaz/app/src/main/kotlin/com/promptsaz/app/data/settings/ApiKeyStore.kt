package com.promptsaz.app.data.settings

/**
 * Read-side contract for the stored AI API key. SecureKeyStore implements it
 * with EncryptedSharedPreferences; tests provide a plain fake so the whole
 * request chain (settings → key → HTTP body) can be verified on the JVM.
 */
interface ApiKeyStore {
    /** Returns true when a non-blank key is stored. */
    fun hasApiKey(): Boolean

    /** Raw key — used only by the network layer. Never log or export this value. */
    fun getApiKey(): String?
}
