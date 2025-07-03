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
     * Main entry point: Update visible cells and generate atlas if needed.
     * Called from UI thread via onVisibleCellsChanged callback.
     */
    suspend fun updateVisibleCells(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS
    ): AtlasUpdateResult = trace(BenchmarkLabels.ATLAS_MANAGER_UPDATE_VISIBLE_CELLS) {
        withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "updateVisibleCells: ${visibleCells.size} cells, zoom=$currentZoom")

                val lodLevel = trace(BenchmarkLabels.ATLAS_MANAGER_SELECT_LOD_LEVEL) {
                    selectLODLevel(currentZoom)
                }

                val expandedCells = expandCellsByRings(visibleCells, marginRings)

                val cellSetKey = generateCellSetKey(expandedCells, lodLevel)

                if (shouldRegenerateAtlas(cellSetKey, lodLevel, currentZoom)) {
                    Log.d(TAG, "Regenerating atlas for ${expandedCells.size} cells at $lodLevel")

                    val (atlasResult, duration) = trace(BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS) {
                        measureTimedValue { generateAtlasForCells(expandedCells, lodLevel) }
                    }

                if (atlasResult.atlas != null) {
                    // Recycle old atlas before replacing it
                    currentAtlas?.bitmap?.recycle()
                    
                    currentAtlas = atlasResult.atlas
                    currentCellIds = cellSetKey
                    currentLODLevel = lodLevel
                    lastReportedZoom = currentZoom

                    val message = if (atlasResult.failed.isEmpty()) {
                        "Atlas generated successfully ${duration.inWholeMilliseconds}ms: ${atlasResult.atlas.regions.size} regions"
                    } else {
                        "Atlas partial success ${duration.inWholeMilliseconds}ms: ${atlasResult.atlas.regions.size} regions, ${atlasResult.failed.size} failed"
                    }
                    Log.d(TAG, message)
                    AtlasUpdateResult.Success(atlasResult.atlas)
                    } else {
                        Log.w(TAG, "Atlas generation failed completely: ${atlasResult.failed.size} failed photos")
                        AtlasUpdateResult.GenerationFailed("Atlas generation failed completely")
                    }
                } else {
                    Log.d(TAG, "Using existing atlas (no regeneration needed)")
                    AtlasUpdateResult.Success(currentAtlas!!)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating visible cells", e)
                AtlasUpdateResult.Error(e)
            }
        }
    }

    /**
     * Get current atlas for rendering.
     * Returns null if no atlas is available.
     */
    fun getCurrentAtlas(): TextureAtlas? = currentAtlas

    /**
     * Get atlas region for a specific photo.
     * Used by UI to map photos to texture coordinates.
     */
    fun getPhotoRegion(photoId: String): AtlasRegion? {
        return currentAtlas?.regions?.get(photoId)
    }

    /**
     * Check if atlas is available and ready for rendering.
     */
    fun hasAtlas(): Boolean = currentAtlas != null

    /**
     * Clear current atlas and free memory.
     * Called when viewport changes significantly or on memory pressure.
     */
    fun clearAtlas() {
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

    private fun shouldRegenerateAtlas(
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
    data class Success(val atlas: TextureAtlas) : AtlasUpdateResult()
    data class GenerationFailed(val error: String) : AtlasUpdateResult()
    data class Error(val exception: Exception) : AtlasUpdateResult()
}
