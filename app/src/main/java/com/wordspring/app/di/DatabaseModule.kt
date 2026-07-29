package com.wordspring.app.di

import android.content.Context
import androidx.room.Room
import com.wordspring.app.data.db.BookEntryDao
import com.wordspring.app.data.db.DailySummaryDao
import com.wordspring.app.data.db.DefinitionDao
import com.wordspring.app.data.db.ImportJobDao
import com.wordspring.app.data.db.ReviewCardDao
import com.wordspring.app.data.db.ReviewEventDao
import com.wordspring.app.data.db.StudySessionDao
import com.wordspring.app.data.db.UserWordFlagDao
import com.wordspring.app.data.db.WordBookDao
import com.wordspring.app.data.db.WordDao
import com.wordspring.app.data.db.WordSpringDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WordSpringDatabase =
        WordSpringDatabase.build(context)

    @Provides fun provideWordBookDao(db: WordSpringDatabase): WordBookDao = db.wordBookDao()
    @Provides fun provideWordDao(db: WordSpringDatabase): WordDao = db.wordDao()
    @Provides fun provideDefinitionDao(db: WordSpringDatabase): DefinitionDao = db.definitionDao()
    @Provides fun provideBookEntryDao(db: WordSpringDatabase): BookEntryDao = db.bookEntryDao()
    @Provides fun provideReviewCardDao(db: WordSpringDatabase): ReviewCardDao = db.reviewCardDao()
    @Provides fun provideReviewEventDao(db: WordSpringDatabase): ReviewEventDao = db.reviewEventDao()
    @Provides fun provideUserWordFlagDao(db: WordSpringDatabase): UserWordFlagDao = db.userWordFlagDao()
    @Provides fun provideDailySummaryDao(db: WordSpringDatabase): DailySummaryDao = db.dailySummaryDao()
    @Provides fun provideImportJobDao(db: WordSpringDatabase): ImportJobDao = db.importJobDao()
    @Provides fun provideStudySessionDao(db: WordSpringDatabase): StudySessionDao = db.studySessionDao()
}
