package com.promptsaz.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PromptEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PromptSazDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
}
