package com.promptsaz.app.di

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.data.settings.SecureKeyStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the encrypted implementation as the read-side key-store contract. */
@Module
@InstallIn(SingletonComponent::class)
abstract class KeyStoreModule {

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(impl: SecureKeyStore): ApiKeyStore
}
