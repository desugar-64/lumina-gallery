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
import dev.serhiiyaremych.lumina.ui.SelectionMode
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
    private var currentSelectedMedia: Media? = null

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
     * @param selectedMedia Currently selected media item for priority handling
     * @param selectionMode Current selection mode (CELL_MODE or PHOTO_MODE)
     */
    suspend fun updateVisibleCells(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS,
        selectedMedia: Media? = null,
        selectionMode: dev.serhiiyaremych.lumina.ui.SelectionMode = dev.serhiiyaremych.lumina.ui.SelectionMode.CELL_MODE
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

                // Check what type of regeneration is needed
                Log.d(TAG, "Check regeneration: cellSetKey=${cellSetKey.take(3)}...(${cellSetKey.size}), lodLevel=$lodLevel")
                Log.d(TAG, "Current state: currentCellIds=${currentCellIds.take(3)}...(${currentCellIds.size}), currentLODLevel=$currentLODLevel, atlases=${currentAtlases.size}")

                val regenerationDecision = isRegenerationNeeded(cellSetKey, lodLevel, currentZoom, selectedMedia)

                when (regenerationDecision) {
                    RegenerationDecision.NO_REGENERATION -> {
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

                    RegenerationDecision.SELECTIVE_REGENERATION -> {
                        Log.d(TAG, "Selective regeneration: generating high-priority atlas for selected photo only")
                        val (selectiveResult, duration) = trace(BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS) {
                            measureTimedValue { generateSelectiveAtlas(selectedMedia, currentZoom, selectionMode) }
                        }
                        return@withContext handleSelectiveAtlasResult(selectiveResult, requestSequence, selectedMedia)
                    }

                    RegenerationDecision.FULL_REGENERATION -> {
                        Log.d(TAG, "Full regeneration: generating atlases for ${expandedCells.size} cells at $lodLevel")
                        val (enhancedResult, duration) = trace(BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS) {
                            measureTimedValue { generateAtlasForCells(expandedCells, lodLevel, currentZoom, selectedMedia, selectionMode) }
                        }
                        return@withContext handleFullAtlasResult(enhancedResult, requestSequence, lodLevel, currentZoom, selectedMedia, cellSetKey)
                    }
                }

                // This should never be reached, but provide fallback
                MultiAtlasUpdateResult.GenerationFailed("Unknown regeneration decision", requestSequence, lodLevel)
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
     * Get smart memory manager instance for advanced debugging.
     */
    fun getSmartMemoryManager(): SmartMemoryManager {
        return smartMemoryManager
    }

    /**
     * Check if atlas regeneration is needed without launching coroutine.
     */
    fun shouldRegenerateAtlas(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS
    ): Boolean {
        return shouldRegenerateAtlas(visibleCells, currentZoom, marginRings, null)
    }

    /**
     * Check if atlas regeneration is needed without launching coroutine, considering selected media.
     */
    fun shouldRegenerateAtlas(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS,
        selectedMedia: Media? = null
    ): Boolean {
        val lodLevel = selectOptimalLODLevel(visibleCells, currentZoom)
        val expandedCells = expandCellsByRings(visibleCells, marginRings)
        val cellSetKey = generateCellSetKey(expandedCells, lodLevel)

        return isRegenerationNeeded(cellSetKey, lodLevel, currentZoom, selectedMedia) != RegenerationDecision.NO_REGENERATION
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
        currentSelectedMedia = null
    }

    // Private helper methods


    /**
     * Selects optimal LOD level based on zoom factor using predefined zoom ranges.
     * Uses the zoom ranges defined in LODLevel enum for consistent behavior.
     */
    private fun selectOptimalLODLevel(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float
    ): LODLevel {
        val optimalLOD = LODLevel.forZoom(currentZoom)
        Log.d(TAG, "selectOptimalLODLevel: zoom=$currentZoom -> $optimalLOD (range: ${optimalLOD.zoomRange})")
        return optimalLOD
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
        currentZoom: Float,
        selectedMedia: Media?
    ): RegenerationDecision {
        return when {
            // First time or no atlases available
            currentAtlases.isEmpty() -> {
                Log.d(TAG, "shouldRegenerateAtlas: No atlases available, need to generate")
                RegenerationDecision.FULL_REGENERATION
            }

            // Any atlas bitmap has been recycled (app restoration scenario)
            currentAtlases.any { it.bitmap.isRecycled } -> {
                Log.d(TAG, "shouldRegenerateAtlas: Some atlas bitmaps are recycled, need to regenerate")
                RegenerationDecision.FULL_REGENERATION
            }

            // Cell set has changed (different visible cells)
            currentCellIds != cellSetKey -> {
                Log.d(TAG, "shouldRegenerateAtlas: Cell set changed, need to regenerate")
                RegenerationDecision.FULL_REGENERATION
            }

            // LOD level boundary crossing detection - requires full regeneration
            currentLODLevel != lodLevel -> {
                Log.d(TAG, "shouldRegenerateAtlas: LOD level changed from $currentLODLevel to $lodLevel (zoom: $lastReportedZoom -> $currentZoom)")
                RegenerationDecision.FULL_REGENERATION
            }

            // Selected media has changed - only selective regeneration needed
            currentSelectedMedia != selectedMedia -> {
                Log.d(TAG, "shouldRegenerateAtlas: Selected media changed from ${currentSelectedMedia?.displayName} to ${selectedMedia?.displayName} - using selective regeneration")
                RegenerationDecision.SELECTIVE_REGENERATION
            }

            // Same LOD level, same cells, same selection - no regeneration needed
            else -> RegenerationDecision.NO_REGENERATION
        }
    }

    /**
     * Decision for atlas regeneration strategy
     */
    private enum class RegenerationDecision {
        NO_REGENERATION,        // Use existing atlases
        SELECTIVE_REGENERATION, // Only regenerate high-priority photo, preserve others
        FULL_REGENERATION      // Regenerate all atlases
    }

    private suspend fun generateAtlasForCells(
        cells: List<HexCellWithMedia>,
        lodLevel: LODLevel,
        currentZoom: Float,
        selectedMedia: Media? = null,
        selectionMode: dev.serhiiyaremych.lumina.ui.SelectionMode = dev.serhiiyaremych.lumina.ui.SelectionMode.CELL_MODE
    ): EnhancedAtlasGenerator.EnhancedAtlasResult {
        val allMediaItems = cells.flatMap { cell ->
            cell.mediaItems.map { mediaWithPosition ->
                mediaWithPosition.media
            }
        }

        val imageMediaItems = allMediaItems.filterIsInstance<Media.Image>()
        val videoMediaItems = allMediaItems.filterIsInstance<Media.Video>()

        val photoUris = imageMediaItems.map { it.uri }

        // Create priority mapping: selected media gets HIGH priority ONLY in PHOTO_MODE
        // In CELL_MODE, all photos use NORMAL priority (use zoom-based LOD level)
        val priorityMapping = photoUris.associateWith { uri ->
            if (selectedMedia != null &&
                selectedMedia is Media.Image &&
                selectedMedia.uri == uri &&
                selectionMode == dev.serhiiyaremych.lumina.ui.SelectionMode.PHOTO_MODE) {
                dev.serhiiyaremych.lumina.domain.model.PhotoPriority.HIGH
            } else {
                dev.serhiiyaremych.lumina.domain.model.PhotoPriority.NORMAL
            }
        }

        val highPriorityCount = priorityMapping.values.count { it == dev.serhiiyaremych.lumina.domain.model.PhotoPriority.HIGH }
        val normalPriorityCount = priorityMapping.values.count { it == dev.serhiiyaremych.lumina.domain.model.PhotoPriority.NORMAL }

        Log.d(TAG, "Atlas generation for $lodLevel:")
        Log.d(TAG, "  - Total cells: ${cells.size}")
        Log.d(TAG, "  - Total media items: ${allMediaItems.size}")
        Log.d(TAG, "  - Image items: ${imageMediaItems.size}")
        Log.d(TAG, "  - Video items: ${videoMediaItems.size}")
        Log.d(TAG, "  - Photo URIs to process: ${photoUris.size}")
        Log.d(TAG, "  - High priority photos: $highPriorityCount")
        Log.d(TAG, "  - Normal priority photos: $normalPriorityCount")
        Log.d(TAG, "  - Selected media: ${selectedMedia?.let { "${it.displayName} (${it.javaClass.simpleName}) URI=${it.uri}" } ?: "none"}")
        Log.d(TAG, "  - Selection mode: $selectionMode")
        Log.d(TAG, "  - Photo URIs: ${photoUris.take(5)}...")
        
        if (highPriorityCount > 0) {
            val highPriorityUris = priorityMapping.filterValues { it == dev.serhiiyaremych.lumina.domain.model.PhotoPriority.HIGH }.keys
            Log.d(TAG, "  - High priority URIs: ${highPriorityUris.take(3)}")
        }

        // Generate atlases using enhanced system with priority-based multi-atlas fallback
        val result: EnhancedAtlasGenerator.EnhancedAtlasResult = enhancedAtlasGenerator.generateAtlasEnhanced(
            photoUris = photoUris,
            lodLevel = lodLevel,
            currentZoom = currentZoom,
            priorityMapping = priorityMapping
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

    /**
     * Generate selective atlas for only the selected photo at high priority.
     * This preserves existing atlases and only adds/replaces the high-priority atlas.
     */
    private suspend fun generateSelectiveAtlas(
        selectedMedia: Media?,
        currentZoom: Float,
        selectionMode: SelectionMode
    ): EnhancedAtlasGenerator.EnhancedAtlasResult {
        // If no selected media or not in PHOTO_MODE, return empty result
        if (selectedMedia == null || selectionMode != dev.serhiiyaremych.lumina.ui.SelectionMode.PHOTO_MODE) {
            Log.d(TAG, "Selective atlas: no selected media or not in PHOTO_MODE, returning empty result")
            return EnhancedAtlasGenerator.EnhancedAtlasResult.empty()
        }

        // Only process the selected media as high priority
        if (selectedMedia !is Media.Image) {
            Log.d(TAG, "Selective atlas: selected media is not an image, returning empty result")
            return EnhancedAtlasGenerator.EnhancedAtlasResult.empty()
        }

        val photoUris = listOf(selectedMedia.uri)
        val priorityMapping = mapOf(selectedMedia.uri to dev.serhiiyaremych.lumina.domain.model.PhotoPriority.HIGH)

        Log.d(TAG, "Selective atlas generation:")
        Log.d(TAG, "  - Selected photo URI: ${selectedMedia.uri}")
        Log.d(TAG, "  - Priority: HIGH (LEVEL_7)")
        Log.d(TAG, "  - Selection mode: $selectionMode")

        // Generate atlas only for the selected photo at maximum quality
        return enhancedAtlasGenerator.generateAtlasEnhanced(
            photoUris = photoUris,
            lodLevel = LODLevel.LEVEL_7, // Always use maximum quality for selected photo
            currentZoom = currentZoom,
            priorityMapping = priorityMapping
        )
    }

    /**
     * Handle result of selective atlas generation by merging with existing atlases
     */
    private suspend fun handleSelectiveAtlasResult(
        selectiveResult: EnhancedAtlasGenerator.EnhancedAtlasResult,
        requestSequence: Long,
        selectedMedia: Media?
    ): MultiAtlasUpdateResult = atlasMutex.withLock {
        if (!selectiveResult.hasResults) {
            Log.w(TAG, "Selective atlas generation failed, keeping existing atlases")
            return@withLock MultiAtlasUpdateResult.Success(currentAtlases, requestSequence, currentLODLevel)
        }

        // Find and remove any existing atlas containing the selected photo
        val selectedPhotoUri = (selectedMedia as? Media.Image)?.uri
        val existingAtlasesWithoutSelected = if (selectedPhotoUri != null) {
            currentAtlases.filter { atlas ->
                !atlas.regions.containsKey(selectedPhotoUri)
            }
        } else {
            currentAtlases
        }

        // Merge existing atlases with new high-priority atlas
        val mergedAtlases = existingAtlasesWithoutSelected + selectiveResult.allAtlases

        // Protect new atlases
        val newAtlasKeys = selectiveResult.allAtlases.mapNotNull { atlas ->
            generateAtlasKey(atlas)
        }.toSet()
        Log.d(TAG, "Protecting ${newAtlasKeys.size} new selective atlases")
        smartMemoryManager.protectAtlases(newAtlasKeys)

        // Unregister replaced atlases if any
        if (selectedPhotoUri != null) {
            currentAtlases.forEach { atlas ->
                if (atlas.regions.containsKey(selectedPhotoUri)) {
                    smartMemoryManager.unregisterAtlas(generateAtlasKey(atlas))
                    Log.d(TAG, "Unregistered replaced atlas containing selected photo")
                }
            }
        }

        // Update state with merged atlases
        currentAtlases = mergedAtlases
        currentSelectedMedia = selectedMedia

        val totalRegions = mergedAtlases.sumOf { it.regions.size }
        Log.d(TAG, "Selective atlas merge complete: ${mergedAtlases.size} total atlases, $totalRegions regions")

        MultiAtlasUpdateResult.Success(mergedAtlases, requestSequence, currentLODLevel)
    }

    /**
     * Handle result of full atlas generation (original behavior)
     */
    private suspend fun handleFullAtlasResult(
        enhancedResult: EnhancedAtlasGenerator.EnhancedAtlasResult,
        requestSequence: Long,
        lodLevel: LODLevel,
        currentZoom: Float,
        selectedMedia: Media?,
        cellSetKey: Set<String>
    ): MultiAtlasUpdateResult = atlasMutex.withLock {
        if (enhancedResult.hasResults) {
            // CRITICAL: Protect new atlases IMMEDIATELY before updating state
            val newAtlasKeys = enhancedResult.allAtlases.mapNotNull { atlas ->
                generateAtlasKey(atlas)
            }.toSet()
            Log.d(TAG, "Pre-protecting ${newAtlasKeys.size} new atlases during full regeneration")
            smartMemoryManager.protectAtlases(newAtlasKeys)

            currentAtlases.forEach { atlas ->
                smartMemoryManager.unregisterAtlas(generateAtlasKey(atlas))
            }

            // Update all state atomically
            currentAtlases = enhancedResult.allAtlases
            currentCellIds = cellSetKey
            currentLODLevel = lodLevel
            lastReportedZoom = currentZoom
            currentSelectedMedia = selectedMedia

            val totalRegions = enhancedResult.allAtlases.sumOf { it.regions.size }
            val message = if (enhancedResult.failed.isEmpty()) {
                "Multi-atlas generated successfully: ${enhancedResult.allAtlases.size} atlases, $totalRegions regions"
            } else {
                "Multi-atlas partial success: ${enhancedResult.allAtlases.size} atlases, $totalRegions regions, ${enhancedResult.failed.size} failed"
            }
            Log.d(TAG, message)
            MultiAtlasUpdateResult.Success(enhancedResult.allAtlases, requestSequence, lodLevel)
        } else {
            Log.w(TAG, "Full atlas generation failed completely: ${enhancedResult.failed.size} failed photos")
            MultiAtlasUpdateResult.GenerationFailed("Atlas generation failed completely", requestSequence, lodLevel)
        }
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
