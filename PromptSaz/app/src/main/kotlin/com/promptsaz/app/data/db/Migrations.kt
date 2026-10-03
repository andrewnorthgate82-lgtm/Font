package com.promptsaz.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for promptsaz.db.
 *
 * Version 1 is the initial schema (Phase 2). The database exports its schema to
 * app/schemas/ (see the `room { schemaDirectory(...) }` block in build.gradle.kts),
 * so future versions can use auto-migrations:
 *
 *     @Database(version = 2, autoMigrations = [AutoMigration(from = 1, to = 2)])
 *
 * and only genuinely incompatible changes need a manual entry in [ALL] below.
 */
object Migrations {

    /**
     * v1 → v2: adds the chat-mode tables (conversations, messages) and the
     * image-generation history table. The SQL must match Room's expected v2
     * schema exactly (types, NOT NULL, indices).
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chat_conversations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_conversations_updatedAt` " +
                    "ON `chat_conversations` (`updatedAt`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`conversationId` INTEGER NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`imageFileName` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId` " +
                    "ON `chat_messages` (`conversationId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAt` " +
                    "ON `chat_messages` (`createdAt`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `image_generations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`prompt` TEXT NOT NULL, " +
                    "`model` TEXT NOT NULL, " +
                    "`fileName` TEXT NOT NULL, " +
                    "`size` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_image_generations_createdAt` " +
                    "ON `image_generations` (`createdAt`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
