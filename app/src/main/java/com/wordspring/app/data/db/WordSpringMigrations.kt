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
    /**
     * v3: word forms + definition ownership.
     *  - words.forms stores inflections for the card back.
     *  - definitions.source marks rows owned by built-in books, so book upgrades can replace them
     *    without touching user-imported meanings.
     *  - Earlier builds inserted the same built-in meaning once per book; collapse those duplicates.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE words ADD COLUMN forms TEXT")
            db.execSQL("ALTER TABLE definitions ADD COLUMN source TEXT")
            db.execSQL(
                """
                DELETE FROM definitions WHERE id NOT IN (
                    SELECT MIN(id) FROM definitions
                    GROUP BY wordId, IFNULL(partOfSpeech, ''), meaningZh
                )
                """.trimIndent()
            )
            // Old built-in rows always stored translation == meaningZh; user CSV rows rarely do.
            db.execSQL(
                """
                UPDATE definitions SET source = 'BUILT_IN'
                WHERE translation IS NOT NULL AND translation = meaningZh
                  AND wordId IN (
                      SELECT be.wordId FROM book_entries be
                      INNER JOIN word_books b ON b.id = be.bookId
                      WHERE b.sourceType = 'BUILT_IN'
                  )
                """.trimIndent()
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE word_books ADD COLUMN targetEpochDay INTEGER")
            }
        },
        MIGRATION_2_3
    )
}
