package com.promptsaz.app.data.db

import androidx.room.migration.Migration

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
    val ALL: Array<Migration> = emptyArray()
}
