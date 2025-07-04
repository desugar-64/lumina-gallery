package dev.serhiiyaremych.lumina.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.serhiiyaremych.lumina.data.PhotoScaler
import dev.serhiiyaremych.lumina.data.datasource.MediaDataSource
import dev.serhiiyaremych.lumina.data.datasource.MediaStoreDataSource
import dev.serhiiyaremych.lumina.data.repository.MediaRepositoryImpl
import dev.serhiiyaremych.lumina.domain.repository.MediaRepository
import dev.serhiiyaremych.lumina.domain.usecase.AtlasGenerator
import dev.serhiiyaremych.lumina.domain.usecase.AtlasManager
import dev.serhiiyaremych.lumina.domain.usecase.GetMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.PhotoLODProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Singleton
    @Provides
    fun provideMediaDataSource(
        @ApplicationContext context: Context,
        dispatcher: CoroutineDispatcher
    ): MediaDataSource =
        MediaStoreDataSource(context.contentResolver, dispatcher)

    @Singleton
    @Provides
    fun provideMediaRepository(dataSource: MediaDataSource): MediaRepository = MediaRepositoryImpl(dataSource)

    @Singleton
    @Provides
    fun provideGetMediaUseCase(repository: MediaRepository): GetMediaUseCase = GetMediaUseCase(repository)

    @Singleton
    @Provides
    fun providePhotoScaler(): PhotoScaler = PhotoScaler()

    @Singleton
    @Provides
    fun providePhotoLODProcessor(
        @ApplicationContext context: Context,
        photoScaler: PhotoScaler
    ): PhotoLODProcessor = PhotoLODProcessor(context.contentResolver, photoScaler)

    @Singleton
    @Provides
    fun provideAtlasGenerator(photoLODProcessor: PhotoLODProcessor): AtlasGenerator = AtlasGenerator(photoLODProcessor)

    @Singleton
    @Provides
    fun provideAtlasManager(
        atlasGenerator: AtlasGenerator
    ): AtlasManager = AtlasManager(atlasGenerator)
}
