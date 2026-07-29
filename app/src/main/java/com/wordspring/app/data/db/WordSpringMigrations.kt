package com.wordspring.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations (M08). Spec rule: never use fallbackToDestructiveMigration().
 *
 * V1 is the initial schema. Each future schema change MUST add a Migration here and a migration
 * test under androidTest.
 */
object WordSpringMigrations {
    val ALL: Array<Migration> = arrayOf(
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE word_books ADD COLUMN targetEpochDay INTEGER")
            }
        }
    )
}
