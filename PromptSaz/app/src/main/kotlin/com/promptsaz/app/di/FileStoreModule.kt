package com.promptsaz.app.di

import com.promptsaz.app.data.files.AndroidImageFileStore
import com.promptsaz.app.data.files.ImageFileStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the Android file-based image store as the storage contract. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FileStoreModule {

    @Binds
    @Singleton
    abstract fun bindImageFileStore(impl: AndroidImageFileStore): ImageFileStore
}
