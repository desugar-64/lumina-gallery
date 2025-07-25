package dev.serhiiyaremych.lumina.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.serhiiyaremych.lumina.domain.usecase.BitmapAtlasPool
import dev.serhiiyaremych.lumina.domain.usecase.DisabledMemoryManager
import dev.serhiiyaremych.lumina.domain.usecase.EnhancedAtlasGenerator
import dev.serhiiyaremych.lumina.domain.usecase.LODSpecificGenerator
import dev.serhiiyaremych.lumina.domain.usecase.StreamingAtlasManager
import javax.inject.Singleton

/**
 * Dependency Injection Module for Streaming Atlas System
 *
 * Provides all components needed for the new responsive atlas generation system:
 * - StreamingAtlasManager (main coordinator)
 * - LODSpecificGenerator (individual LOD generation)
 * - BitmapAtlasPool (texture reuse)
 * - DisabledMemoryManager (temporary - no memory constraints)
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamingAtlasModule {

    @Singleton
    @Provides
    fun provideBitmapAtlasPool(): BitmapAtlasPool {
        return BitmapAtlasPool()
    }

    @Singleton
    @Provides
    fun provideLODSpecificGenerator(
        enhancedAtlasGenerator: EnhancedAtlasGenerator
    ): LODSpecificGenerator {
        return LODSpecificGenerator(enhancedAtlasGenerator)
    }

    @Singleton
    @Provides
    fun provideStreamingAtlasManager(
        lodSpecificGenerator: LODSpecificGenerator,
        bitmapAtlasPool: BitmapAtlasPool
    ): StreamingAtlasManager {
        return StreamingAtlasManager(lodSpecificGenerator, bitmapAtlasPool)
    }

    // TEMPORARY: Commented out disabled memory manager - causes type conflicts
    // TODO: Re-enable when type system is properly handled
    // @Singleton
    // @Provides
    // fun provideDisabledMemoryManager(
    //     disabledMemoryManager: DisabledMemoryManager
    // ): dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager {
    //     return disabledMemoryManager
    // }
}