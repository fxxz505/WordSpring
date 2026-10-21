package com.wordspring.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.content.Context

@TypeConverters(Converters::class)
@Database(
    entities = [
        WordBookEntity::class,
        WordEntity::class,
        DefinitionEntity::class,
        BookEntryEntity::class,
        ReviewCardEntity::class,
        ReviewEventEntity::class,
        UserWordFlagEntity::class,
        DailySummaryEntity::class,
        ImportJobEntity::class,
        StudySessionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class WordSpringDatabase : RoomDatabase() {
    abstract fun wordBookDao(): WordBookDao
    abstract fun wordDao(): WordDao
    abstract fun definitionDao(): DefinitionDao
    abstract fun bookEntryDao(): BookEntryDao
    abstract fun reviewCardDao(): ReviewCardDao
    abstract fun reviewEventDao(): ReviewEventDao
    abstract fun userWordFlagDao(): UserWordFlagDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun studySessionDao(): StudySessionDao

    companion object {
        const val DB_NAME = "wordspring.db"

        fun build(context: Context): WordSpringDatabase =
            Room.databaseBuilder(context, WordSpringDatabase::class.java, DB_NAME)
                .addMigrations(*WordSpringMigrations.ALL)
                // Spec: never use fallbackToDestructiveMigration()
                .build()
    }
}

class Converters {
    @TypeConverter fun fromBooleanList(value: List<Boolean>?): String =
        value?.joinToString(",") { it.toString() } ?: ""
    @TypeConverter fun toBooleanList(value: String?): List<Boolean> =
        if (value.isNullOrEmpty()) emptyList()
        else value.split(",").map { it.toBooleanStrictOrNull() ?: false }
}
