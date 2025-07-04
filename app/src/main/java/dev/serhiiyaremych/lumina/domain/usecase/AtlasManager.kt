package dev.serhiiyaremych.lumina.domain.usecase

import android.content.Context
import android.util.Log
import androidx.tracing.trace
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.domain.model.AtlasRegion
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.measureTimedValue

/**
 * Atlas Manager - Phase 1: Simple Implementation
 *
 * Manages texture atlas lifecycle for efficient photo rendering.
 * This Phase 1 implementation focuses on getting visual results quickly
 * with a single atlas generation approach.
 *
 * Features:
 * - Generates atlas for currently visible cells
 * - Ring-based margin system for smooth scrolling
 * - LOD level selection based on zoom
 * - Simple memory management (single atlas at a time)
 *
 * Future Phase 2 enhancements:
 * - Viewport-based caching
 * - Multiple atlas coordination
 * - Memory pressure handling
 * - Predictive loading
 */
@Singleton
class AtlasManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val atlasGenerator: AtlasGenerator,
    private val photoLODProcessor: PhotoLODProcessor
) {
    companion object {
        private const val TAG = "AtlasManager"
        private const val DEFAULT_MARGIN_RINGS = 1
    }

    // Simple state management for Phase 1
    private var currentAtlas: TextureAtlas? = null
    private var currentCellIds: Set<String> = emptySet()
    private var currentLODLevel: LODLevel = LODLevel.LEVEL_2
    private var lastReportedZoom: Float = 1.0f
    
    /**
     * Atlas Request Sequence Counter - Race Condition Protection
     * 
     * This monotonically increasing sequence number ensures "latest wins" semantics
     * when multiple atlas generation requests happen in rapid succession.
     * 
     * Problem: When users zoom/pan quickly, multiple coroutines can be launched.
     * Even with job.cancel(), some may complete due to:
     * - Timing edge cases where cancellation happens too late
     * - CPU-intensive atlas generation between suspension points  
     * - Non-cooperative code that doesn't check for cancellation
     * 
     * Solution: Each request gets a unique, increasing sequence number.
     * The UI only accepts results with sequence numbers higher than the current one,
     * automatically ignoring any stale/out-of-order completions.
     * 
     * Example:
     * - Request A (seq=1) starts, user zooms again
     * - Request B (seq=2) starts, A gets cancelled  
     * - If A somehow completes first: A ignored (1 < 2), B updates UI
     * - Normal case: B completes, updates UI, A is already cancelled
     */
    private var atlasRequestSequence: Long = 0
    
    // Mutex for atomic atlas state updates
    private val atlasMutex = Mutex()

    /**
     * Main entry point: Update visible cells and generate atlas if needed.
     * Called from UI thread via onVisibleCellsChanged callback.
     */
    suspend fun updateVisibleCells(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS
    ): AtlasUpdateResult = trace(BenchmarkLabels.ATLAS_MANAGER_UPDATE_VISIBLE_CELLS) {
        withContext(Dispatchers.Default) {
            // Generate unique sequence number for this atlas request
            val requestSequence = ++atlasRequestSequence
            
            try {
                Log.d(TAG, "updateVisibleCells: ${visibleCells.size} cells, zoom=$currentZoom, requestSequence=$requestSequence")

                val lodLevel = trace(BenchmarkLabels.ATLAS_MANAGER_SELECT_LOD_LEVEL) {
                    selectLODLevel(currentZoom)
                }

                val expandedCells = expandCellsByRings(visibleCells, marginRings)

                val cellSetKey = generateCellSetKey(expandedCells, lodLevel)

                // Caller already verified regeneration is needed, proceed directly
                Log.d(TAG, "Regenerating atlas for ${expandedCells.size} cells at $lodLevel")

                val (atlasResult, duration) = trace(BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS) {
                    measureTimedValue { generateAtlasForCells(expandedCells, lodLevel) }
                }

                if (atlasResult.atlas != null) {
                    // Atomically replace atlas state to prevent race conditions
                    atlasMutex.withLock {
                        // Recycle old atlas before replacing it
                        val oldAtlas = currentAtlas
                        
                        // Update all state atomically
                        currentAtlas = atlasResult.atlas
                        currentCellIds = cellSetKey
                        currentLODLevel = lodLevel
                        lastReportedZoom = currentZoom
                        
                        // Recycle old atlas after state update
                        oldAtlas?.bitmap?.recycle()
                    }

                    val message = if (atlasResult.failed.isEmpty()) {
                        "Atlas generated successfully ${duration.inWholeMilliseconds}ms: ${atlasResult.atlas.regions.size} regions"
                    } else {
                        "Atlas partial success ${duration.inWholeMilliseconds}ms: ${atlasResult.atlas.regions.size} regions, ${atlasResult.failed.size} failed"
                    }
                    Log.d(TAG, message)
                    AtlasUpdateResult.Success(atlasResult.atlas, requestSequence)
                } else {
                    Log.w(TAG, "Atlas generation failed completely: ${atlasResult.failed.size} failed photos")
                    AtlasUpdateResult.GenerationFailed("Atlas generation failed completely", requestSequence)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating visible cells", e)
                AtlasUpdateResult.Error(e, requestSequence)
            }
        }
    }

    /**
     * Get current atlas for rendering.
     * Returns null if no atlas is available.
     */
    suspend fun getCurrentAtlas(): TextureAtlas? = atlasMutex.withLock { currentAtlas }

    /**
     * Get atlas region for a specific photo.
     * Used by UI to map photos to texture coordinates.
     */
    suspend fun getPhotoRegion(photoId: String): AtlasRegion? = atlasMutex.withLock {
        currentAtlas?.regions?.get(photoId)
    }

    /**
     * Check if atlas is available and ready for rendering.
     */
    suspend fun hasAtlas(): Boolean = atlasMutex.withLock { currentAtlas != null }

    /**
     * Check if atlas regeneration is needed without launching coroutine.
     * This is a synchronous check that can be called from UI thread to avoid
     * unnecessary coroutine launches.
     */
    fun shouldRegenerateAtlas(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS
    ): Boolean {
        val lodLevel = selectLODLevel(currentZoom)
        val expandedCells = expandCellsByRings(visibleCells, marginRings)
        val cellSetKey = generateCellSetKey(expandedCells, lodLevel)
        
        return isRegenerationNeeded(cellSetKey, lodLevel, currentZoom)
    }

    /**
     * Clear current atlas and free memory.
     * Called when viewport changes significantly or on memory pressure.
     */
    suspend fun clearAtlas() = atlasMutex.withLock {
        Log.d(TAG, "Clearing current atlas")
        currentAtlas?.bitmap?.recycle()
        currentAtlas = null
        currentCellIds = emptySet()
    }

    // Private helper methods

    private fun selectLODLevel(zoom: Float): LODLevel {
        return LODLevel.values().first { zoom in it.zoomRange }
    }

    private fun expandCellsByRings(
        visibleCells: List<HexCellWithMedia>,
        marginRings: Int
    ): List<HexCellWithMedia> {
        if (marginRings <= 0) return visibleCells

        // For Phase 1, return visible cells as-is
        // TODO Phase 2: Implement actual hex ring expansion
        // This would require access to the complete hex grid to find adjacent cells
        return visibleCells
    }

    private fun generateCellSetKey(
        cells: List<HexCellWithMedia>,
        lodLevel: LODLevel
    ): Set<String> {
        return cells.map { "${it.hexCell.q},${it.hexCell.r}-${lodLevel.name}" }.toSet()
    }

    private fun isRegenerationNeeded(
        cellSetKey: Set<String>,
        lodLevel: LODLevel,
        currentZoom: Float
    ): Boolean {
        return when {
            // First time or no atlas available
            currentAtlas == null -> {
                Log.d(TAG, "shouldRegenerateAtlas: No atlas available, need to generate")
                true
            }
            
            // Cell set has changed (different visible cells)
            currentCellIds != cellSetKey -> {
                Log.d(TAG, "shouldRegenerateAtlas: Cell set changed, need to regenerate")
                true
            }
            
            // LOD level boundary crossing detection
            currentLODLevel != lodLevel -> {
                Log.d(TAG, "shouldRegenerateAtlas: LOD level changed from $currentLODLevel to $lodLevel (zoom: $lastReportedZoom -> $currentZoom)")
                true
            }
            
            // Same LOD level, same cells - no regeneration needed
            else -> false
        }
    }

    private suspend fun generateAtlasForCells(
        cells: List<HexCellWithMedia>,
        lodLevel: LODLevel
    ): AtlasGenerationResult {
        // Extract all photo URIs from cells
        val photoUris = cells.flatMap { cell ->
            cell.mediaItems.map { mediaWithPosition ->
                mediaWithPosition.media.uri
            }
        }

        Log.d(TAG, "Generating atlas for ${photoUris.size} photos at $lodLevel")

        // Generate atlas using existing pipeline
        return atlasGenerator.generateAtlas(
            photoUris = photoUris,
            lodLevel = lodLevel
        )
    }
}

/**
 * Result of atlas update operation.
 */
sealed class AtlasUpdateResult {
    abstract val requestSequence: Long
    
    data class Success(val atlas: TextureAtlas, override val requestSequence: Long) : AtlasUpdateResult()
    data class GenerationFailed(val error: String, override val requestSequence: Long) : AtlasUpdateResult()
    data class Error(val exception: Exception, override val requestSequence: Long) : AtlasUpdateResult()
}
