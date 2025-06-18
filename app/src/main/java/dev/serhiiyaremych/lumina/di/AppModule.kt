package dev.serhiiyaremych.lumina.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.serhiiyaremych.lumina.data.datasource.FakeMediaDataSource
import dev.serhiiyaremych.lumina.data.datasource.MediaDataSource
import dev.serhiiyaremych.lumina.data.repository.MediaRepositoryImpl
import dev.serhiiyaremych.lumina.domain.repository.MediaRepository
import dev.serhiiyaremych.lumina.domain.usecase.GetMediaUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideMediaDataSource(): MediaDataSource = FakeMediaDataSource()

    @Singleton
    @Provides
    fun provideMediaRepository(dataSource: MediaDataSource): MediaRepository = MediaRepositoryImpl(dataSource)

    @Singleton
    @Provides
    fun provideGetMediaUseCase(repository: MediaRepository): GetMediaUseCase = GetMediaUseCase(repository)
}
