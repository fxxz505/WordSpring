package com.wordspring.app.di

import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.ContentRepositoryImpl
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.data.repository.ReviewRepositoryImpl
import com.wordspring.app.data.repository.SessionRepository
import com.wordspring.app.data.repository.SessionRepositoryImpl
import com.wordspring.app.data.repository.StatsRepository
import com.wordspring.app.data.repository.StatsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository
    @Binds @Singleton abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository
    @Binds @Singleton abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
    @Binds @Singleton abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository
}
