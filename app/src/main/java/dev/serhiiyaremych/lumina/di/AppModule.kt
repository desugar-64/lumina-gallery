package dev.serhiiyaremych.lumina.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.serhiiyaremych.lumina.data.BitmapPool
import dev.serhiiyaremych.lumina.data.PhotoScaler
import dev.serhiiyaremych.lumina.data.datasource.MediaDataSource
import dev.serhiiyaremych.lumina.data.datasource.MediaStoreDataSource
import dev.serhiiyaremych.lumina.data.repository.MediaRepositoryImpl
import dev.serhiiyaremych.lumina.domain.repository.MediaRepository
import dev.serhiiyaremych.lumina.domain.usecase.AtlasManager
import dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities
import dev.serhiiyaremych.lumina.domain.usecase.DynamicAtlasPool
import dev.serhiiyaremych.lumina.domain.usecase.EnhancedAtlasGenerator
import dev.serhiiyaremych.lumina.domain.usecase.GetMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.PhotoLODProcessor
import dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager
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
    fun provideBitmapPool(smartMemoryManager: SmartMemoryManager): BitmapPool = 
        BitmapPool(smartMemoryManager)

    @Singleton
    @Provides
    fun providePhotoScaler(bitmapPool: BitmapPool): PhotoScaler = PhotoScaler(bitmapPool)

    @Singleton
    @Provides
    fun providePhotoLODProcessor(
        @ApplicationContext context: Context,
        photoScaler: PhotoScaler,
        bitmapPool: BitmapPool
    ): PhotoLODProcessor = PhotoLODProcessor(context.contentResolver, photoScaler, bitmapPool)

    @Singleton
    @Provides
    fun provideAtlasManager(
        enhancedAtlasGenerator: EnhancedAtlasGenerator,
        smartMemoryManager: SmartMemoryManager
    ): AtlasManager = AtlasManager(enhancedAtlasGenerator, smartMemoryManager)
}
