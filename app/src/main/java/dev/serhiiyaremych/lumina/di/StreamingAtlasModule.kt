package dev.serhiiyaremych.lumina.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.serhiiyaremych.lumina.data.BitmapPool
import dev.serhiiyaremych.lumina.domain.usecase.BitmapAtlasPool
import dev.serhiiyaremych.lumina.domain.usecase.DeviceCapabilities
import dev.serhiiyaremych.lumina.domain.usecase.DynamicAtlasPool
import dev.serhiiyaremych.lumina.domain.usecase.EnhancedAtlasGenerator
import dev.serhiiyaremych.lumina.domain.usecase.LODSpecificGenerator
import dev.serhiiyaremych.lumina.domain.usecase.PhotoLODProcessor
import dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager
import dev.serhiiyaremych.lumina.domain.usecase.StreamingAtlasManager
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * Dependency Injection Module for Streaming Atlas System
 *
 * Provides all components needed for the new responsive atlas generation system:
 * - StreamingAtlasManager (main coordinator)
 * - LODSpecificGenerator (individual LOD generation)
 * - BitmapAtlasPool (texture reuse)
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamingAtlasModule {

    @Singleton
    @Provides
    fun provideBitmapAtlasPool(): BitmapAtlasPool = BitmapAtlasPool()

    @Singleton
    @Provides
    fun provideDynamicAtlasPool(
        deviceCapabilities: DeviceCapabilities,
        smartMemoryManager: SmartMemoryManager,
        photoLODProcessor: PhotoLODProcessor,
        bitmapPool: BitmapPool,
        externalScope: CoroutineScope
    ): DynamicAtlasPool = DynamicAtlasPool(
        deviceCapabilities,
        smartMemoryManager,
        photoLODProcessor,
        bitmapPool,
        externalScope
    )

    @Singleton
    @Provides
    fun provideLODSpecificGenerator(
        enhancedAtlasGenerator: EnhancedAtlasGenerator
    ): LODSpecificGenerator = LODSpecificGenerator(enhancedAtlasGenerator)

    @Singleton
    @Provides
    fun provideStreamingAtlasManager(
        lodSpecificGenerator: LODSpecificGenerator,
        bitmapAtlasPool: BitmapAtlasPool,
        atlasBucketManager: dev.serhiiyaremych.lumina.domain.bucket.AtlasBucketManager
    ): StreamingAtlasManager = StreamingAtlasManager(lodSpecificGenerator, bitmapAtlasPool, atlasBucketManager)
}
