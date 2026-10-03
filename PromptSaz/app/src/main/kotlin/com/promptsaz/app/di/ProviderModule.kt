package com.promptsaz.app.di

import com.promptsaz.app.data.remote.OpenAiCompatibleProvider
import com.promptsaz.app.domain.provider.PromptProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * PromptProvider registry wiring (Hilt multibinding).
 * Add a new provider with one more @IntoSet function here.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideOpenAiCompatibleProvider(
        impl: OpenAiCompatibleProvider,
    ): PromptProvider = impl
}
