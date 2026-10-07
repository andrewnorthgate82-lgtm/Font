package com.promptsaz.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PromptEntity::class,
        ChatConversationEntity::class,
        ChatMessageEntity::class,
        ImageGenerationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PromptSazDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun chatDao(): ChatDao
    abstract fun imageGenerationDao(): ImageGenerationDao
}
