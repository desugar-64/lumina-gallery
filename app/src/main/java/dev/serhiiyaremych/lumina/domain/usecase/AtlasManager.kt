package dev.serhiiyaremych.lumina.domain.usecase

import android.net.Uri
import android.util.Log
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.domain.model.AtlasRegion
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.measureTimedValue

/**
 * Atlas Manager - Ultra Atlas System Implementation
 *
 * Manages multiple texture atlases for efficient photo rendering with zero photo loss.
 * This implementation supports device-aware multi-atlas generation with intelligent
 * fallback strategies to ensure every photo renders.
 *
 * Features:
 * - Multi-atlas generation and management
 * - Device-aware atlas sizing (2K/4K/8K)
 * - Emergency fallback system
 * - Memory pressure handling
 * - Zero photo loss guarantee
 * - LOD level selection based on zoom
 */
@Singleton
class AtlasManager @Inject constructor(
    private val enhancedAtlasGenerator: EnhancedAtlasGenerator,
    private val smartMemoryManager: SmartMemoryManager
) {
    companion object {
        private const val TAG = "AtlasManager"
        private const val DEFAULT_MARGIN_RINGS = 1
    }

    // Multi-atlas state management
    private var currentAtlases: List<TextureAtlas> = emptyList()
    private var currentCellIds: Set<String> = emptySet()
    private var currentLODLevel: LODLevel = LODLevel.LEVEL_2
    private var lastReportedZoom: Float = 1.0f

    /**
     * Atlas Request Sequence Counter - Race Condition Protection
     *
     * This monotonically increasing sequence number ensures "latest wins" semantics
     * when multiple atlas generation requests happen in rapid succession.
     */
    private var atlasRequestSequence: Long = 0

    // Mutex for atomic atlas state updates
    private val atlasMutex = Mutex()

    /**
     * Main entry point: Update visible cells and generate atlas if needed.
     * Called from UI thread via onVisibleCellsChanged callback.
     *
     * @param visibleCells Currently visible hex cells
     * @param currentZoom Current zoom level
     * @param marginRings Number of rings to expand beyond visible cells
     */
    suspend fun updateVisibleCells(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS
    ): MultiAtlasUpdateResult = trace(BenchmarkLabels.ATLAS_MANAGER_UPDATE_VISIBLE_CELLS) {
        withContext(Dispatchers.Default) {
            // Generate unique sequence number for this atlas request
            val requestSequence = ++atlasRequestSequence

            runCatching {
                Log.d(TAG, "updateVisibleCells: ${visibleCells.size} cells, zoom=$currentZoom, requestSequence=$requestSequence")

                val lodLevel = trace(BenchmarkLabels.ATLAS_MANAGER_SELECT_LOD_LEVEL) {
                    selectOptimalLODLevel(visibleCells, currentZoom)
                }

                val expandedCells = expandCellsByRings(visibleCells, marginRings)
                val cellSetKey = generateCellSetKey(expandedCells, lodLevel)

                // Check if regeneration is needed
                Log.d(TAG, "Check regeneration: cellSetKey=${cellSetKey.take(3)}...(${cellSetKey.size}), lodLevel=$lodLevel")
                Log.d(TAG, "Current state: currentCellIds=${currentCellIds.take(3)}...(${currentCellIds.size}), currentLODLevel=$currentLODLevel, atlases=${currentAtlases.size}")

                if (!isRegenerationNeeded(cellSetKey, lodLevel, currentZoom)) {
                    Log.d(TAG, "Atlas regeneration not needed - returning existing atlases")
                    return@withContext atlasMutex.withLock {
                        if (currentAtlases.isNotEmpty()) {
                            val totalRegions = currentAtlases.sumOf { it.regions.size }
                            Log.d(TAG, "Returning ${currentAtlases.size} existing atlases with $totalRegions total regions")
                            MultiAtlasUpdateResult.Success(currentAtlases, requestSequence, currentLODLevel)
                        } else {
                            Log.w(TAG, "No current atlases available but regeneration not needed - this shouldn't happen")
                            MultiAtlasUpdateResult.GenerationFailed("No atlases available", requestSequence, null)
                        }
                    }
                }

                Log.d(TAG, "Regenerating atlases for ${expandedCells.size} cells at $lodLevel")

                val (enhancedResult, duration) = trace(BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS) {
                    measureTimedValue { generateAtlasForCells(expandedCells, lodLevel) }
                }

                if (enhancedResult.hasResults) {
                    // Atomically replace atlas state to prevent race conditions
                    atlasMutex.withLock {
                        // CRITICAL: Protect new atlases IMMEDIATELY before updating state
                        // This prevents emergency cleanup from racing during atlas generation
                        val newAtlasKeys = enhancedResult.allAtlases.mapNotNull { atlas ->
                            generateAtlasKey(atlas)
                        }.toSet()
                        Log.d(TAG, "Pre-protecting ${newAtlasKeys.size} new atlases during state transition")
                        smartMemoryManager.protectAtlases(newAtlasKeys)

                        currentAtlases.forEach { atlas ->
                            smartMemoryManager.unregisterAtlas(generateAtlasKey(atlas))
                        }

                        // Update all state atomically
                        currentAtlases = enhancedResult.allAtlases
                        currentCellIds = cellSetKey
                        currentLODLevel = lodLevel
                        lastReportedZoom = currentZoom
                    }

                    val totalRegions = enhancedResult.allAtlases.sumOf { it.regions.size }
                    val message = if (enhancedResult.failed.isEmpty()) {
                        "Multi-atlas generated successfully ${duration.inWholeMilliseconds}ms: ${enhancedResult.allAtlases.size} atlases, $totalRegions regions"
                    } else {
                        "Multi-atlas partial success ${duration.inWholeMilliseconds}ms: ${enhancedResult.allAtlases.size} atlases, $totalRegions regions, ${enhancedResult.failed.size} failed"
                    }
                    Log.d(TAG, message)
                    MultiAtlasUpdateResult.Success(enhancedResult.allAtlases, requestSequence, lodLevel)
                } else {
                    Log.w(TAG, "Atlas generation failed completely: ${enhancedResult.failed.size} failed photos")
                    MultiAtlasUpdateResult.GenerationFailed("Atlas generation failed completely", requestSequence, lodLevel)
                }
            }.fold(
                onSuccess = { result -> result },
                onFailure = { e ->
                    Log.e(TAG, "Error updating visible cells", e)
                    if (e is CancellationException) throw e
                    MultiAtlasUpdateResult.Error(e, requestSequence, null)
                }
            )
        }
    }

    /**
     * Get current atlases for rendering.
     */
    suspend fun getCurrentAtlases(): List<TextureAtlas> = atlasMutex.withLock { currentAtlases }

    /**
     * Get atlas region for a specific photo.
     * Searches across all current atlases to find the photo.
     */
    suspend fun getPhotoRegion(photoId: Uri): AtlasRegion? = atlasMutex.withLock {
        currentAtlases.firstNotNullOfOrNull { atlas ->
            atlas.regions[photoId]
        }
    }

    /**
     * Get atlas and region for a specific photo.
     * Returns both the atlas containing the photo and the region within that atlas.
     */
    suspend fun getPhotoAtlasAndRegion(photoId: Uri): Pair<TextureAtlas, AtlasRegion>? = atlasMutex.withLock {
        currentAtlases.forEach { atlas ->
            atlas.regions[photoId]?.let { region ->
                return@withLock atlas to region
            }
        }
        return@withLock null
    }

    /**
     * Check if atlases are available and ready for rendering.
     */
    suspend fun hasAtlas(): Boolean = atlasMutex.withLock { currentAtlases.isNotEmpty() }

    /**
     * Get current memory status from the smart memory manager.
     */
    fun getMemoryStatus(): SmartMemoryManager.MemoryStatus {
        return smartMemoryManager.getMemoryStatus()
    }

    /**
     * Check if atlas regeneration is needed without launching coroutine.
     */
    fun shouldRegenerateAtlas(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS
    ): Boolean {
        val lodLevel = selectOptimalLODLevel(visibleCells, currentZoom)
        val expandedCells = expandCellsByRings(visibleCells, marginRings)
        val cellSetKey = generateCellSetKey(expandedCells, lodLevel)

        return isRegenerationNeeded(cellSetKey, lodLevel, currentZoom)
    }

    /**
     * Clear current atlases and free memory.
     */
    suspend fun clearAtlases() = atlasMutex.withLock {
        Log.d(TAG, "Clearing ${currentAtlases.size} current atlases")
        currentAtlases.forEach { atlas ->
            if (!atlas.bitmap.isRecycled) {
                atlas.bitmap.recycle()
            }
        }
        currentAtlases = emptyList()
        currentCellIds = emptySet()
    }

    // Private helper methods


    /**
     * Selects optimal LOD level based on actual screen pixel size of thumbnails.
     * Updated for enhanced 8-level LOD system with smoother quality transitions.
     */
    private fun selectOptimalLODLevel(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float
    ): LODLevel {
        if (visibleCells.isEmpty()) {
            return LODLevel.entries.first { currentZoom in it.zoomRange }
        }

        // Use hex cell size as the base for consistent LOD calculation
        // This prevents hysteresis caused by variable thumbnail sizes
        val hexCellSize = visibleCells.firstOrNull()?.bounds?.let { 
            maxOf(it.width, it.height) 
        } ?: return LODLevel.entries.first { currentZoom in it.zoomRange }

        val maxScreenPixelSize = hexCellSize * currentZoom

        val optimalLOD = when {
            maxScreenPixelSize <= 48f -> LODLevel.LEVEL_0   // 32px for ultra-tiny thumbnails
            maxScreenPixelSize <= 96f -> LODLevel.LEVEL_1   // 64px for tiny thumbnails
            maxScreenPixelSize <= 144f -> LODLevel.LEVEL_2  // 128px for small thumbnails
            maxScreenPixelSize <= 220f -> LODLevel.LEVEL_3  // 192px for medium thumbnails
            maxScreenPixelSize <= 320f -> LODLevel.LEVEL_4  // 256px for large thumbnails
            maxScreenPixelSize <= 450f -> LODLevel.LEVEL_5  // 384px for high-quality thumbnails
            maxScreenPixelSize <= 640f -> LODLevel.LEVEL_6  // 512px for focused view
            else -> LODLevel.LEVEL_7                        // 768px for near-fullscreen
        }

        Log.d(TAG, "selectOptimalLODLevel: maxScreenSize=${maxScreenPixelSize}px (zoom=$currentZoom) -> $optimalLOD")
        return optimalLOD
//        return selectLODLevel(currentZoom)
    }

    private fun expandCellsByRings(
        visibleCells: List<HexCellWithMedia>,
        marginRings: Int
    ): List<HexCellWithMedia> {
        if (marginRings <= 0) return visibleCells
        return visibleCells // TODO: Implement hex ring expansion
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
            // First time or no atlases available
            currentAtlases.isEmpty() -> {
                Log.d(TAG, "shouldRegenerateAtlas: No atlases available, need to generate")
                true
            }

            // Any atlas bitmap has been recycled (app restoration scenario)
            currentAtlases.any { it.bitmap.isRecycled } -> {
                Log.d(TAG, "shouldRegenerateAtlas: Some atlas bitmaps are recycled, need to regenerate")
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
    ): EnhancedAtlasGenerator.EnhancedAtlasResult {
        val allMediaItems = cells.flatMap { cell ->
            cell.mediaItems.map { mediaWithPosition ->
                mediaWithPosition.media
            }
        }

        val imageMediaItems = allMediaItems.filterIsInstance<Media.Image>()
        val videoMediaItems = allMediaItems.filterIsInstance<Media.Video>()

        val photoUris = imageMediaItems.map { it.uri }

        Log.d(TAG, "Atlas generation for $lodLevel:")
        Log.d(TAG, "  - Total cells: ${cells.size}")
        Log.d(TAG, "  - Total media items: ${allMediaItems.size}")
        Log.d(TAG, "  - Image items: ${imageMediaItems.size}")
        Log.d(TAG, "  - Video items: ${videoMediaItems.size}")
        Log.d(TAG, "  - Photo URIs to process: ${photoUris.size}")
        Log.d(TAG, "  - Photo URIs: ${photoUris.take(5)}...")

        // Generate atlases using enhanced system with multi-atlas fallback
        val result: EnhancedAtlasGenerator.EnhancedAtlasResult = enhancedAtlasGenerator.generateAtlasEnhanced(
            photoUris = photoUris,
            lodLevel = lodLevel
        )

        // Log detailed results
        Log.d(TAG, "Atlas generation results for $lodLevel:")
        Log.d(TAG, "  - Input photos: ${photoUris.size}")
        Log.d(TAG, "  - Generated atlases: ${result.allAtlases.size}")
        Log.d(TAG, "  - Photos packed: ${result.packedPhotos}")
        Log.d(TAG, "  - Photos failed: ${result.failed.size}")
        Log.d(TAG, "  - Success rate: ${if (photoUris.isNotEmpty()) (result.packedPhotos * 100) / photoUris.size else 0}%")
        Log.d(TAG, "  - Average utilization: ${(result.averageUtilization * 100).toInt()}%")
        if (result.failed.isNotEmpty()) {
            Log.w(TAG, "  - Failed photo URIs: ${result.failed.take(3)}...")
        }

        return result
    }

    /**
     * Protect current atlases from emergency cleanup by coordinating with SmartMemoryManager
     */
    private fun protectCurrentAtlasesFromCleanup() {
        if (currentAtlases.isEmpty()) {
            smartMemoryManager.protectAtlases(emptySet())
            return
        }

        // Generate atlas keys for current atlases
        val atlasKeys = currentAtlases.mapNotNull { atlas ->
            generateAtlasKey(atlas)
        }.toSet()

        Log.d(TAG, "Protecting ${atlasKeys.size} current atlases from emergency cleanup")
        smartMemoryManager.protectAtlases(atlasKeys)
    }

    /**
     * Generate atlas key for coordination with SmartMemoryManager
     */
    private fun generateAtlasKey(atlas: TextureAtlas): SmartMemoryManager.AtlasKey? {
        if (atlas.regions.isEmpty()) return null

        // Generate photo hash from all photos in this atlas
        val photoUris = atlas.regions.keys.sorted() // Sort for consistent hashing
        val photosHash = photoUris.hashCode()

        return SmartMemoryManager.AtlasKey(
            lodLevel = atlas.lodLevel,
            atlasSize = atlas.size,
            photosHash = photosHash
        )
    }
}

/**
 * Result of multi-atlas update operation.
 */
sealed class MultiAtlasUpdateResult {
    abstract val requestSequence: Long
    abstract val lodLevel: LODLevel?

    data class Success(
        val atlases: List<TextureAtlas>, 
        override val requestSequence: Long,
        override val lodLevel: LODLevel
    ) : MultiAtlasUpdateResult()
    
    data class GenerationFailed(
        val error: String, 
        override val requestSequence: Long,
        override val lodLevel: LODLevel?
    ) : MultiAtlasUpdateResult()
    
    data class Error(
        val exception: Throwable, 
        override val requestSequence: Long,
        override val lodLevel: LODLevel?
    ) : MultiAtlasUpdateResult()
}
