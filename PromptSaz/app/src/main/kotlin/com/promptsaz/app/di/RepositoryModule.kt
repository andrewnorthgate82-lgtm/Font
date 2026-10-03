package com.promptsaz.app.di

import com.promptsaz.app.data.repository.ChatRepositoryImpl
import com.promptsaz.app.data.repository.ImageRepositoryImpl
import com.promptsaz.app.data.repository.KbRepositoryImpl
import com.promptsaz.app.data.repository.PromptRepositoryImpl
import com.promptsaz.app.data.repository.SettingsRepositoryImpl
import com.promptsaz.app.domain.repository.ChatRepository
import com.promptsaz.app.domain.repository.ImageRepository
import com.promptsaz.app.domain.repository.KbRepository
import com.promptsaz.app.domain.repository.PromptRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds repository implementations to their domain interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPromptRepository(impl: PromptRepositoryImpl): PromptRepository

    @Binds
    @Singleton
    abstract fun bindKbRepository(impl: KbRepositoryImpl): KbRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository
}
