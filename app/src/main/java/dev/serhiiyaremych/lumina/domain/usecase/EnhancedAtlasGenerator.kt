package dev.serhiiyaremych.lumina.domain.usecase

import android.net.Uri
import android.util.Log
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.data.ScaleStrategy
import dev.serhiiyaremych.lumina.domain.model.AtlasOptimizationConfig
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.PhotoPriority
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        priorityMapping: Map<Uri, dev.serhiiyaremych.lumina.domain.model.PhotoPriority> = emptyMap(),
        onPhotoReady: (Uri, dev.serhiiyaremych.lumina.domain.model.AtlasRegion) -> Unit = { _, _ -> }
    ): EnhancedAtlasResult = trace(BenchmarkLabels.ATLAS_GENERATOR_GENERATE_ATLAS) {
        
        // Logging (side effect)
        logGenerationStart(photoUris, lodLevel, priorityMapping)

        // Input validation (pure function)
        if (!EnhancedAtlasComposer.validateInput(photoUris)) {
            return@trace EnhancedAtlasResult.empty()
        }

        // Memory management decisions (pure function + side effects)
        val memoryStatus = smartMemoryManager.getMemoryStatus()
        val context = EnhancedAtlasComposer.AtlasGenerationContext(
            photoUris = photoUris,
            lodLevel = lodLevel,
            currentZoom = currentZoom,
            scaleStrategy = scaleStrategy,
            priorityMapping = priorityMapping,
            memoryStatus = memoryStatus
        )
        
        val config = EnhancedAtlasComposer.determineGenerationConfig(context)
        if (config.shouldTriggerEmergencyCleanup) {
            Log.w(TAG, "Critical memory pressure detected, triggering emergency cleanup")
            smartMemoryManager.emergencyCleanup()
        }

        // Core atlas generation (delegated to DynamicAtlasPool)
        currentCoroutineContext().ensureActive()
        Log.d(TAG, "Generating enhanced multi-atlas with immediate loading for ${photoUris.size} photos at $lodLevel (zoom: $currentZoom)")

        val multiAtlasResult = dynamicAtlasPool.generateMultiAtlasImmediate(photoUris, lodLevel, currentZoom, scaleStrategy, priorityMapping, onPhotoReady)

        // Result transformation (pure function)
        val enhancedResult = EnhancedAtlasComposer.transformMultiAtlasResult(multiAtlasResult, photoUris.size)
        
        // Logging completion (side effect)
        val stats = EnhancedAtlasComposer.calculateGenerationStats(multiAtlasResult, photoUris.size)
        logGenerationComplete(stats)

        return@trace enhancedResult
    }

    /**
     * Extracted logging function for generation start
     */
    private fun logGenerationStart(
        photoUris: List<Uri>, 
        lodLevel: LODLevel, 
        priorityMapping: Map<Uri, dev.serhiiyaremych.lumina.domain.model.PhotoPriority>
    ) {
        Log.d(TAG, "EnhancedAtlasGenerator.generateAtlasEnhanced called:")
        Log.d(TAG, "  - Photo URIs: ${photoUris.size} total")
        Log.d(TAG, "  - LOD Level: $lodLevel")
        Log.d(TAG, "  - Priority mapping: ${priorityMapping.size} entries")
        val highPriorityCount = priorityMapping.values.count { it == dev.serhiiyaremych.lumina.domain.model.PhotoPriority.HIGH }
        val normalPriorityCount = priorityMapping.values.count { it == dev.serhiiyaremych.lumina.domain.model.PhotoPriority.NORMAL }
        Log.d(TAG, "  - High priority: $highPriorityCount, Normal priority: $normalPriorityCount")
        Log.d(TAG, "  - Photo URIs (first 5): ${photoUris.take(5)}")
    }

    /**
     * Extracted logging function for generation completion
     */
    private fun logGenerationComplete(stats: EnhancedAtlasComposer.GenerationStats) {
        Log.d(TAG, "Multi-atlas generation complete: ${stats.atlasCount} atlases, ${stats.totalPhotos} total photos, ${String.format("%.1f", stats.successRate * 100)}% success rate")
    }

    /**
     * Atlas generation strategy
     */
    enum class AtlasStrategy {
        SINGLE_ATLAS, // Used original single atlas generation
        MULTI_ATLAS // Used multi-atlas fallback system
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
            get() = allAtlases.sumOf { it.photoCount }

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
            fun empty(): EnhancedAtlasResult = EnhancedAtlasResult(
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

    /**
     * Get SmartMemoryManager for debug overlay and memory status
     */
    fun getSmartMemoryManager(): SmartMemoryManager = smartMemoryManager
}

/**
 * Pure functions for enhanced atlas generation - extracted from EnhancedAtlasGenerator
 */
object EnhancedAtlasComposer {

    /**
     * Context for atlas generation pipeline
     */
    data class AtlasGenerationContext(
        val photoUris: List<Uri>,
        val lodLevel: LODLevel,
        val currentZoom: Float,
        val scaleStrategy: ScaleStrategy,
        val priorityMapping: Map<Uri, PhotoPriority>,
        val memoryStatus: SmartMemoryManager.MemoryStatus
    )

    /**
     * Atlas generation pipeline configuration
     */
    data class AtlasGenerationConfig(
        val shouldTriggerEmergencyCleanup: Boolean
    )

    /**
     * Pure function to determine atlas generation configuration
     */
    fun determineGenerationConfig(context: AtlasGenerationContext): AtlasGenerationConfig =
        AtlasGenerationConfig(
            shouldTriggerEmergencyCleanup = context.memoryStatus.pressureLevel == SmartMemoryManager.MemoryPressure.CRITICAL
        )

    /**
     * Pure function to validate atlas generation input
     */
    fun validateInput(photoUris: List<Uri>): Boolean = photoUris.isNotEmpty()

    /**
     * Pure function to transform multi-atlas result to enhanced atlas result
     */
    fun transformMultiAtlasResult(
        multiAtlasResult: DynamicAtlasPool.MultiAtlasResult,
        originalPhotoCount: Int
    ): EnhancedAtlasGenerator.EnhancedAtlasResult {
        val atlasCount = multiAtlasResult.atlases.size
        return EnhancedAtlasGenerator.EnhancedAtlasResult(
            primaryAtlas = multiAtlasResult.atlases.firstOrNull(),
            additionalAtlases = multiAtlasResult.atlases.drop(1),
            failed = multiAtlasResult.failed,
            totalPhotos = multiAtlasResult.totalPhotos,
            processedPhotos = multiAtlasResult.processedPhotos,
            strategy = if (atlasCount == 1) EnhancedAtlasGenerator.AtlasStrategy.SINGLE_ATLAS else EnhancedAtlasGenerator.AtlasStrategy.MULTI_ATLAS,
            fallbackUsed = false, // Multi-atlas is now the primary system
            multiAtlasStrategy = multiAtlasResult.strategy
        )
    }

    /**
     * Pure function to calculate generation statistics
     */
    fun calculateGenerationStats(
        multiAtlasResult: DynamicAtlasPool.MultiAtlasResult,
        originalPhotoCount: Int
    ): GenerationStats {
        val atlasCount = multiAtlasResult.atlases.size
        val successRate = if (originalPhotoCount > 0) {
            multiAtlasResult.atlases.sumOf { it.photoCount }.toFloat() / originalPhotoCount
        } else 0f
        
        return GenerationStats(
            atlasCount = atlasCount,
            totalPhotos = multiAtlasResult.totalPhotos,
            successRate = successRate
        )
    }

    /**
     * Statistics for atlas generation
     */
    data class GenerationStats(
        val atlasCount: Int,
        val totalPhotos: Int,
        val successRate: Float
    )
}
