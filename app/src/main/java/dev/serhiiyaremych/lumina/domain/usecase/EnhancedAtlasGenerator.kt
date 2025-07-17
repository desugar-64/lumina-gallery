package dev.serhiiyaremych.lumina.domain.usecase

import android.net.Uri
import android.util.Log
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.data.ScaleStrategy
import dev.serhiiyaremych.lumina.domain.model.AtlasOptimizationConfig
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Atlas Generator with emergency fallback system for oversized textures.
 *
 * This generator provides:
 * - Backward compatibility with existing AtlasGenerator interface
 * - Emergency fallback to multi-atlas system when photos don't fit
 * - Device-aware atlas size selection
 * - Memory pressure handling
 * - Graceful degradation under constraints
 */
@Singleton
class EnhancedAtlasGenerator @Inject constructor(
    private val dynamicAtlasPool: DynamicAtlasPool,
    private val smartMemoryManager: SmartMemoryManager,
    private val deviceCapabilities: DeviceCapabilities
) {
    
    // Configuration for atlas optimization based on zoom levels
    private val optimizationConfig = AtlasOptimizationConfig.default()

    companion object {
        private const val TAG = "EnhancedAtlasGenerator"
        private const val DEFAULT_ATLAS_SIZE = 2048
    }

    /**
     * Enhanced atlas generation using the multi-atlas system
     *
     * Process:
     * 1. Check memory pressure and apply emergency cleanup
     * 2. Use multi-atlas system directly for optimal photo distribution
     * 3. Handle memory pressure gracefully with LOD degradation
     * 4. Return comprehensive atlas result with analytics
     */
    suspend fun generateAtlasEnhanced(
        photoUris: List<Uri>,
        lodLevel: LODLevel,
        currentZoom: Float,
        scaleStrategy: ScaleStrategy = ScaleStrategy.FIT_CENTER,
        priorityMapping: Map<Uri, dev.serhiiyaremych.lumina.domain.model.PhotoPriority> = emptyMap()
    ): EnhancedAtlasResult = trace(BenchmarkLabels.ATLAS_GENERATOR_GENERATE_ATLAS) {

        if (photoUris.isEmpty()) {
            return@trace EnhancedAtlasResult.empty()
        }

        // Check memory pressure before starting
        val memoryStatus = smartMemoryManager.getMemoryStatus()
        if (memoryStatus.pressureLevel == SmartMemoryManager.MemoryPressure.CRITICAL) {
            Log.w(TAG, "Critical memory pressure detected, triggering emergency cleanup")
            smartMemoryManager.emergencyCleanup()
        }

        // Use multi-atlas system directly for optimal photo distribution
        currentCoroutineContext().ensureActive()
        Log.d(TAG, "Generating enhanced multi-atlas for ${photoUris.size} photos at $lodLevel (zoom: $currentZoom)")

        val multiAtlasResult = dynamicAtlasPool.generateMultiAtlas(photoUris, lodLevel, currentZoom, scaleStrategy, priorityMapping)

        val atlasCount = multiAtlasResult.atlases.size
        val successRate = if (photoUris.isNotEmpty()) multiAtlasResult.atlases.sumOf { it.regions.size }.toFloat() / photoUris.size else 0f

        Log.d(TAG, "Multi-atlas generation complete: $atlasCount atlases, ${multiAtlasResult.totalPhotos} total photos, ${String.format("%.1f", successRate * 100)}% success rate")

        return@trace EnhancedAtlasResult(
            primaryAtlas = multiAtlasResult.atlases.firstOrNull(),
            additionalAtlases = multiAtlasResult.atlases.drop(1),
            failed = multiAtlasResult.failed,
            totalPhotos = multiAtlasResult.totalPhotos,
            processedPhotos = multiAtlasResult.processedPhotos,
            strategy = if (atlasCount == 1) AtlasStrategy.SINGLE_ATLAS else AtlasStrategy.MULTI_ATLAS,
            fallbackUsed = false, // Multi-atlas is now the primary system
            multiAtlasStrategy = multiAtlasResult.strategy
        )
    }


    /**
     * Atlas generation strategy
     */
    enum class AtlasStrategy {
        SINGLE_ATLAS,   // Used original single atlas generation
        MULTI_ATLAS     // Used multi-atlas fallback system
    }

    /**
     * Enhanced atlas generation result
     */
    data class EnhancedAtlasResult(
        val primaryAtlas: TextureAtlas?,
        val additionalAtlases: List<TextureAtlas>,
        val failed: List<Uri>,
        val totalPhotos: Int,
        val processedPhotos: Int,
        val strategy: AtlasStrategy,
        val fallbackUsed: Boolean,
        val multiAtlasStrategy: DynamicAtlasPool.AtlasStrategy? = null
    ) {

        /**
         * All generated atlases (primary + additional)
         */
        val allAtlases: List<TextureAtlas>
            get() = listOfNotNull(primaryAtlas) + additionalAtlases

        /**
         * Total number of photos packed across all atlases
         */
        val packedPhotos: Int
            get() = allAtlases.sumOf { it.regions.size }

        /**
         * Average utilization across all atlases
         */
        val averageUtilization: Float
            get() = if (allAtlases.isNotEmpty()) allAtlases.map { it.utilization }.average().toFloat() else 0f

        /**
         * Whether any photos were successfully packed
         */
        val hasResults: Boolean
            get() = allAtlases.isNotEmpty()

        companion object {
            fun empty(): EnhancedAtlasResult {
                return EnhancedAtlasResult(
                    primaryAtlas = null,
                    additionalAtlases = emptyList(),
                    failed = emptyList(),
                    totalPhotos = 0,
                    processedPhotos = 0,
                    strategy = AtlasStrategy.SINGLE_ATLAS,
                    fallbackUsed = false
                )
            }
        }
    }
}
