package dev.serhiiyaremych.lumina.domain.usecase

import android.net.Uri
import android.util.Log
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.data.ScaleStrategy
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.PhotoPriority
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LOD-Specific Generator - Generates atlas for individual LOD levels
 *
 * This component handles generation of a single LOD level independently,
 * optimized for streaming atlas system where each LOD generates in parallel.
 *
 * Features:
 * - Single LOD level focus for optimal performance
 * - Reuses existing atlas generation pipeline
 * - Memory-efficient bitmap handling
 * - Error resilience with detailed reporting
 */
@Singleton
class LODSpecificGenerator @Inject constructor(
    private val enhancedAtlasGenerator: EnhancedAtlasGenerator
) {
    companion object {
        private const val TAG = "LODSpecificGenerator"
    }

    /**
     * Generate atlas for specific LOD level with given priority context
     */
    suspend fun generateLODAtlas(
        photos: List<Uri>,
        lodLevel: LODLevel,
        currentZoom: Float,
        priority: LODPriority
    ): LODGenerationResult = trace("${BenchmarkLabels.ATLAS_GENERATOR_GENERATE_ATLAS}_${lodLevel}") {
        
        if (photos.isEmpty()) {
            Log.w(TAG, "No photos provided for LOD $lodLevel generation")
            return@trace LODGenerationResult.Failed(
                error = "No photos provided",
                retryable = false
            )
        }

        try {
            currentCoroutineContext().ensureActive()
            
            Log.d(TAG, "Generating LOD $lodLevel: ${photos.size} photos, priority=${priority.level} (${priority.reason})")

            // Create priority mapping based on LOD priority context
            val priorityMapping = createPriorityMapping(photos, priority)
            
            // Use existing enhanced atlas generator with specific LOD
            val result = enhancedAtlasGenerator.generateAtlasEnhanced(
                photoUris = photos,
                lodLevel = lodLevel,
                currentZoom = currentZoom,
                scaleStrategy = ScaleStrategy.FIT_CENTER,
                priorityMapping = priorityMapping
            )

            currentCoroutineContext().ensureActive()

            when {
                result.hasResults -> {
                    Log.d(TAG, "LOD $lodLevel generation successful: ${result.allAtlases.size} atlases, ${result.packedPhotos} photos packed")
                    
                    LODGenerationResult.Success(
                        atlases = result.allAtlases,
                        packedPhotos = result.packedPhotos,
                        failedPhotos = result.failed,
                        utilization = result.averageUtilization
                    )
                }
                
                else -> {
                    Log.w(TAG, "LOD $lodLevel generation failed: no atlases generated from ${photos.size} photos")
                    
                    LODGenerationResult.Failed(
                        error = "No atlases generated from ${photos.size} photos",
                        retryable = true
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during LOD $lodLevel generation", e)
            
            LODGenerationResult.Failed(
                error = e.message ?: "Unknown generation error",
                retryable = true
            )
        }
    }

    /**
     * Create priority mapping based on LOD priority context
     */
    private fun createPriorityMapping(
        photos: List<Uri>,
        priority: LODPriority
    ): Map<Uri, PhotoPriority> {
        return when (priority.level) {
            // Priority 1: Persistent cache (LEVEL_0) - all photos normal priority
            1 -> photos.associateWith { PhotoPriority.NORMAL }
            
            // Priority 2: Visible cells - all photos normal priority
            2 -> photos.associateWith { PhotoPriority.NORMAL }
            
            // Priority 3: Active cell - slightly higher priority for better quality
            3 -> photos.associateWith { PhotoPriority.NORMAL }
            
            // Priority 4: Selected photo - maximum priority for best quality
            4 -> photos.associateWith { PhotoPriority.HIGH }
            
            // Default: normal priority
            else -> photos.associateWith { PhotoPriority.NORMAL }
        }
    }
}

/**
 * LOD generation result
 */
sealed class LODGenerationResult {
    
    /**
     * Successful LOD generation
     */
    data class Success(
        val atlases: List<TextureAtlas>,
        val packedPhotos: Int,
        val failedPhotos: List<Uri>,
        val utilization: Float
    ) : LODGenerationResult()
    
    /**
     * Failed LOD generation
     */
    data class Failed(
        val error: String,
        val retryable: Boolean
    ) : LODGenerationResult()
}