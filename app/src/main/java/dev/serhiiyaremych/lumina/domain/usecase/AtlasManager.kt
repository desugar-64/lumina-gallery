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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val smartMemoryManager: SmartMemoryManager,
    private val atlasBucketManager: dev.serhiiyaremych.lumina.domain.bucket.AtlasBucketManager
) {
    companion object {
        private const val TAG = "AtlasManager"
        private const val DEFAULT_MARGIN_RINGS = 1
    }

    // Multi-atlas state management - now using bucket system
    private var currentCellIds: Set<String> = emptySet()
    private var currentLODLevel: LODLevel = LODLevel.LEVEL_2
    private var lastReportedZoom: Float = 1.0f
    private var currentSelectedMedia: Media? = null
    private var currentFocusedCell: HexCellWithMedia? = null

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
                val bucketAtlases = atlasBucketManager.snapshotAll()
                Log.d(TAG, "Current state: currentCellIds=${currentCellIds.take(3)}...(${currentCellIds.size}), currentLODLevel=$currentLODLevel, atlases=${bucketAtlases.size}")

                val regenerationDecision = isRegenerationNeeded(cellSetKey, lodLevel, currentZoom, selectedMedia)

                when (regenerationDecision) {
                    RegenerationDecision.NO_REGENERATION -> {
                        Log.d(TAG, "Atlas regeneration not needed - returning existing atlases")
                        val currentAtlases = atlasBucketManager.snapshotAll()
                        return@withContext if (currentAtlases.isNotEmpty()) {
                            val totalRegions = currentAtlases.sumOf { it.photoCount }
                            Log.d(TAG, "Returning ${currentAtlases.size} existing atlases with $totalRegions total regions")
                            MultiAtlasUpdateResult.Success(currentAtlases, requestSequence, currentLODLevel)
                        } else {
                            Log.w(TAG, "No current atlases available but regeneration not needed - this shouldn't happen")
                            MultiAtlasUpdateResult.GenerationFailed("No atlases available", requestSequence, null)
                        }
                    }

                    RegenerationDecision.SELECTIVE_REGENERATION -> {
                        Log.d(TAG, "Selective regeneration: generating high-priority atlas for selected photo only")
                        val (selectiveResult, _) = trace(BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS) {
                            measureTimedValue { generateSelectiveAtlas(selectedMedia, currentZoom, selectionMode) }
                        }
                        return@withContext handleSelectiveAtlasResult(selectiveResult, requestSequence, selectedMedia)
                    }

                    RegenerationDecision.FULL_REGENERATION -> {
                        Log.d(TAG, "Full regeneration: generating atlases for ${expandedCells.size} cells at $lodLevel")
                        val (enhancedResult, _) = trace(BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS) {
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
     * Returns combined view of all buckets with proper priority ordering.
     */
    suspend fun getCurrentAtlases(): List<TextureAtlas> = atlasBucketManager.snapshotAll()

    /**
     * Get atlas region for a specific photo.
     * Searches across all current atlases to find the photo.
     */
    suspend fun getPhotoRegion(photoId: Uri): AtlasRegion? = atlasBucketManager.getBestRegion(photoId)

    /**
     * Get atlas and region for a specific photo.
     * Returns both the atlas containing the photo and the region within that atlas.
     */
    suspend fun getPhotoAtlasAndRegion(photoId: Uri): Pair<TextureAtlas, AtlasRegion>? {
        val region = atlasBucketManager.getBestRegion(photoId) ?: return null
        val allAtlases = atlasBucketManager.snapshotAll()
        val atlas = allAtlases.find { it.containsPhoto(photoId) }
        return atlas?.let { it to region }
    }

    /**
     * Check if atlases are available and ready for rendering.
     */
    suspend fun hasAtlas(): Boolean {
        val allAtlases = atlasBucketManager.snapshotAll()
        return allAtlases.isNotEmpty()
    }

    /**
     * Get current memory status from the smart memory manager.
     */
    fun getMemoryStatus(): SmartMemoryManager.MemoryStatus = smartMemoryManager.getMemoryStatus()

    /**
     * Get smart memory manager instance for advanced debugging.
     */
    fun getSmartMemoryManager(): SmartMemoryManager = smartMemoryManager

    /**
     * Check if atlas regeneration is needed (suspend version).
     */
    suspend fun shouldRegenerateAtlas(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        marginRings: Int = DEFAULT_MARGIN_RINGS
    ): Boolean = shouldRegenerateAtlas(visibleCells, currentZoom, marginRings, null)

    /**
     * Check if atlas regeneration is needed, considering selected media (suspend version).
     */
    suspend fun shouldRegenerateAtlas(
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
     * Update focused cell for the focus bucket.
     * This generates a +1 LOD level atlas for the focused cell photos.
     * Should be called from ViewModel's coroutine scope.
     */
    suspend fun updateFocusedCell(focusedCell: HexCellWithMedia?, currentZoom: Float) {
        withContext(Dispatchers.Default) {
            atlasMutex.withLock {
                if (currentFocusedCell == focusedCell) {
                    Log.d(TAG, "Focused cell unchanged, skipping update")
                    return@withLock
                }

                currentFocusedCell = focusedCell

                if (focusedCell == null) {
                    Log.d(TAG, "Clearing focused cell")
                    atlasBucketManager.clearFocus()
                    return@withLock
                }

                Log.d(TAG, "Updating focused cell: ${focusedCell.hexCell.q},${focusedCell.hexCell.r}")

                // Generate atlas for focused cell at +1 LOD level
                val currentLOD = selectOptimalLODLevel(listOf(focusedCell), currentZoom)
                val higherLOD = getHigherLOD(currentLOD)

                // Extract photos from focused cell
                val photoUris = focusedCell.mediaItems
                    .mapNotNull { it.media as? Media.Image }
                    .map { it.uri }

                if (photoUris.isNotEmpty()) {
                    Log.d(TAG, "Generating focus atlas: ${photoUris.size} photos at $higherLOD (+1 from $currentLOD)")

                    val focusResult = enhancedAtlasGenerator.generateAtlasEnhanced(
                        photoUris = photoUris,
                        lodLevel = higherLOD,
                        currentZoom = currentZoom,
                        priorityMapping = emptyMap()
                    )

                    if (focusResult.hasResults) {
                        atlasBucketManager.replaceFocus(focusResult.allAtlases)
                        Log.d(TAG, "Focus atlas generated: ${focusResult.allAtlases.size} atlases")
                    } else {
                        Log.w(TAG, "Focus atlas generation failed")
                        atlasBucketManager.clearFocus()
                    }
                } else {
                    Log.d(TAG, "No photos in focused cell, clearing focus bucket")
                    atlasBucketManager.clearFocus()
                }
            }
        }
    }

    /**
     * Clear current atlases and free memory.
     */
    suspend fun clearAtlases() = atlasMutex.withLock {
        val currentAtlases = atlasBucketManager.snapshotAll()
        Log.d(TAG, "Clearing ${currentAtlases.size} current atlases via bucket manager")

        // Clear all transient buckets (keeps L0 base)
        atlasBucketManager.clearTransient()

        currentCellIds = emptySet()
        currentSelectedMedia = null
        currentFocusedCell = null
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
    ): Set<String> = cells.map { "${it.hexCell.q},${it.hexCell.r}-${lodLevel.name}" }.toSet()

    /**
     * Get higher LOD level for focus bucket (+1 from current)
     */
    private fun getHigherLOD(currentLOD: LODLevel): LODLevel {
        val currentIndex = LODLevel.entries.indexOf(currentLOD)
        val higherIndex = (currentIndex + 1).coerceAtMost(LODLevel.entries.lastIndex)
        return LODLevel.entries[higherIndex]
    }

    private suspend fun isRegenerationNeeded(
        cellSetKey: Set<String>,
        lodLevel: LODLevel,
        currentZoom: Float,
        selectedMedia: Media?
    ): RegenerationDecision {
        val currentAtlases = atlasBucketManager.snapshotAll()
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
        NO_REGENERATION, // Use existing atlases
        SELECTIVE_REGENERATION, // Only regenerate high-priority photo, preserve others
        FULL_REGENERATION // Regenerate all atlases
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
                selectionMode == dev.serhiiyaremych.lumina.ui.SelectionMode.PHOTO_MODE
            ) {
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
    private suspend fun protectCurrentAtlasesFromCleanup() {
        val currentAtlases = atlasBucketManager.snapshotAll()
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
        if (atlas.totalPhotoSlots == 0) return null

        // Generate photo hash from all photos in this atlas (using reactive regions)
        val photoUris = atlas.reactiveRegions.keys.sorted() // Sort for consistent hashing
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
     * Handle result of selective atlas generation by using highlight bucket
     */
    private suspend fun handleSelectiveAtlasResult(
        selectiveResult: EnhancedAtlasGenerator.EnhancedAtlasResult,
        requestSequence: Long,
        selectedMedia: Media?
    ): MultiAtlasUpdateResult = atlasMutex.withLock {
        if (!selectiveResult.hasResults) {
            Log.w(TAG, "Selective atlas generation failed, keeping existing atlases")
            val currentAtlases = atlasBucketManager.snapshotAll()
            return@withLock MultiAtlasUpdateResult.Success(currentAtlases, requestSequence, currentLODLevel)
        }

        // Protect new atlases
        val newAtlasKeys = selectiveResult.allAtlases.mapNotNull { atlas ->
            generateAtlasKey(atlas)
        }.toSet()
        Log.d(TAG, "Protecting ${newAtlasKeys.size} new selective atlases")
        smartMemoryManager.protectAtlases(newAtlasKeys)

        // Replace highlight bucket with new high-priority atlases
        atlasBucketManager.replaceHighlight(selectiveResult.allAtlases)

        // Update state
        currentSelectedMedia = selectedMedia

        val mergedAtlases = atlasBucketManager.snapshotAll()
        val totalRegions = mergedAtlases.sumOf { it.photoCount }
        Log.d(TAG, "Selective atlas merge complete: ${mergedAtlases.size} total atlases, $totalRegions regions")

        MultiAtlasUpdateResult.Success(mergedAtlases, requestSequence, currentLODLevel)
    }

    /**
     * Handle result of full atlas generation using bucket system
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

            // Unregister old atlases from memory manager
            val currentAtlases = atlasBucketManager.snapshotAll()
            currentAtlases.forEach { atlas ->
                smartMemoryManager.unregisterAtlas(generateAtlasKey(atlas))
            }

            // Send atlases to appropriate bucket based on LOD level
            if (lodLevel == LODLevel.LEVEL_0) {
                atlasBucketManager.populateBase(enhancedResult.allAtlases)
            } else {
                atlasBucketManager.addToRolling(enhancedResult.allAtlases)
            }

            // Update state
            currentCellIds = cellSetKey
            currentLODLevel = lodLevel
            lastReportedZoom = currentZoom
            currentSelectedMedia = selectedMedia

            val totalRegions = enhancedResult.allAtlases.sumOf { it.photoCount }
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
