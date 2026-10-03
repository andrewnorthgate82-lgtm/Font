package com.promptsaz.app.di

import android.content.Context
import androidx.room.Room
import com.promptsaz.app.data.db.Migrations
import com.promptsaz.app.data.db.PromptDao
import com.promptsaz.app.data.db.PromptSazDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PromptSazDatabase =
        Room.databaseBuilder(
            context = context,
            klass = PromptSazDatabase::class.java,
            name = "promptsaz.db",
        )
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun providePromptDao(database: PromptSazDatabase): PromptDao = database.promptDao()
}
