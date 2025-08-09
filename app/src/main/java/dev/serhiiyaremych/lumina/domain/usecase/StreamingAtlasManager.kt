package dev.serhiiyaremych.lumina.domain.usecase

import android.net.Uri
import android.util.Log
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.domain.model.AtlasPriority
import dev.serhiiyaremych.lumina.domain.model.AtlasRegion
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.domain.model.TypeSafeLODPriority
import dev.serhiiyaremych.lumina.ui.SelectionMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Streaming Atlas Manager - Responsive Atlas System Implementation
 *
 * Revolutionary atlas generation system that provides immediate UI updates through independent
 * LOD coroutines. Each LOD level generates in parallel and emits results as soon as ready.
 *
 * Key Features:
 * - Independent LOD coroutines with immediate UI notification
 * - Smart LOD selection logic with priority system
 * - Progressive enhancement (lowest → visible → active → selected)
 * - Persistent cache for ALL canvas photos at LEVEL_0
 * - Proper cancellation and throttling for rapid interactions
 * - Bitmap pooling for texture reuse
 * - Zero-wait UI updates for responsive user experience
 */
@Singleton
class StreamingAtlasManager @Inject constructor(
    private val lodSpecificGenerator: LODSpecificGenerator,
    private val bitmapAtlasPool: BitmapAtlasPool,
    private val atlasBucketManager: dev.serhiiyaremych.lumina.domain.bucket.AtlasBucketManager
) {
    companion object {
        private const val TAG = "StreamingAtlasManager"
        private const val DEBOUNCE_DELAY_MS = 50L
        private const val THROTTLE_DELAY_MS = 16L // ~60fps
    }

    // Active atlas state management - now using bucket system
    private val atlasMutex = Mutex()
    private var currentFocusedCell: HexCellWithMedia? = null
    private var currentSelectedMedia: Media? = null

    /**
     * Update focused cell for bucket-based focus management.
     * Uses efficient bucket lookups to determine if regeneration is needed.
     */
    suspend fun updateFocusedCell(focusedCell: HexCellWithMedia?, currentZoom: Float) {
        withContext(Dispatchers.Default) {
            atlasMutex.withLock {
                val currentLOD = LODLevel.forZoom(currentZoom)
                val requiredLOD = getHigherLOD(currentLOD) // +1 LOD for focus

                if (focusedCell == null) {
                    if (currentFocusedCell != null) {
                        Log.d(TAG, "Clearing focused cell")
                        atlasBucketManager.clearFocus()
                        currentFocusedCell = null
                    }
                    return@withLock
                }

                // Extract photo URIs from focused cell
                val photoUris = focusedCell.mediaItems
                    .mapNotNull { it.media as? Media.Image }
                    .map { it.uri }

                if (photoUris.isEmpty()) {
                    Log.d(TAG, "No photos in focused cell, clearing focus bucket")
                    atlasBucketManager.clearFocus()
                    currentFocusedCell = focusedCell
                    return@withLock
                }

                // Check if we need to update using efficient bucket lookups
                val cellChanged = currentFocusedCell?.hexCell != focusedCell.hexCell
                val existingFocusLOD = atlasBucketManager.getFocusLODForPhotos(photoUris)
                val needsRegeneration = cellChanged || existingFocusLOD == null || existingFocusLOD.level < requiredLOD.level

                if (!needsRegeneration) {
                    Log.d(TAG, "Focus cell atlas already exists at adequate LOD (${existingFocusLOD?.name} >= ${requiredLOD.name}), skipping update")
                    currentFocusedCell = focusedCell // Update tracking even if no regeneration
                    return@withLock
                }

                currentFocusedCell = focusedCell
                Log.d(TAG, "Updating focused cell: ${focusedCell.hexCell.q},${focusedCell.hexCell.r} at zoom $currentZoom -> LOD $requiredLOD (existing: ${existingFocusLOD?.name ?: "none"})")

                val focusResult = lodSpecificGenerator.generateLODAtlas(
                    photos = photoUris,
                    lodLevel = requiredLOD,
                    currentZoom = currentZoom,
                    priority = TypeSafeLODPriority(
                        priority = AtlasPriority.ActiveCell,
                        photos = focusedCell.mediaItems.mapNotNull { it.media as? Media.Image },
                        reason = "Focus cell enhancement at zoom $currentZoom"
                    )
                )

                when (focusResult) {
                    is LODGenerationResult.Success -> {
                        atlasBucketManager.replaceFocus(focusResult.atlases)
                        Log.d(TAG, "Focus atlas generated: ${focusResult.atlases.size} atlases")
                    }
                    is LODGenerationResult.Failed -> {
                        Log.w(TAG, "Focus atlas generation failed: ${focusResult.error}")
                        atlasBucketManager.clearFocus()
                    }
                }
            }
        }
    }

    /**
     * Update selected media for bucket-based highlight management.
     * Uses efficient bucket lookups to determine if regeneration is needed.
     */
    suspend fun updateSelectedMedia(selectedMedia: Media?, currentZoom: Float, selectionMode: SelectionMode) {
        withContext(Dispatchers.Default) {
            atlasMutex.withLock {
                if (selectedMedia == null || selectionMode != SelectionMode.PHOTO_MODE) {
                    if (currentSelectedMedia != null) {
                        Log.d(TAG, "Clearing selected media")
                        atlasBucketManager.clearHighlight()
                        currentSelectedMedia = null
                    }
                    return@withLock
                }

                if (selectedMedia !is Media.Image) {
                    Log.d(TAG, "Selected media is not an image, clearing highlight bucket")
                    atlasBucketManager.clearHighlight()
                    currentSelectedMedia = null
                    return@withLock
                }

                // Check if we need to update using efficient bucket lookups
                val mediaChanged = currentSelectedMedia?.uri != selectedMedia.uri
                val requiredLOD = LODLevel.LEVEL_7 // Always max quality for selected photo
                val hasAdequateLOD = atlasBucketManager.hasHighlightPhotoAtLOD(selectedMedia.uri, requiredLOD)

                val needsRegeneration = mediaChanged || !hasAdequateLOD

                if (!needsRegeneration) {
                    val existingLOD = atlasBucketManager.getHighlightLODForPhoto(selectedMedia.uri)
                    Log.d(TAG, "Selected media atlas already exists at adequate LOD (${existingLOD?.name} >= ${requiredLOD.name}), skipping update")
                    currentSelectedMedia = selectedMedia // Update tracking even if no regeneration
                    return@withLock
                }

                currentSelectedMedia = selectedMedia
                val existingLOD = atlasBucketManager.getHighlightLODForPhoto(selectedMedia.uri)
                Log.d(TAG, "Updating selected media: ${selectedMedia.displayName} at zoom $currentZoom -> LOD $requiredLOD (existing: ${existingLOD?.name ?: "none"})")

                val highlightResult = lodSpecificGenerator.generateLODAtlas(
                    photos = listOf(selectedMedia.uri),
                    lodLevel = requiredLOD,
                    currentZoom = currentZoom,
                    priority = TypeSafeLODPriority(
                        priority = AtlasPriority.SelectedPhoto,
                        photos = listOf(selectedMedia),
                        reason = "Selected photo maximum quality at zoom $currentZoom"
                    )
                )

                when (highlightResult) {
                    is LODGenerationResult.Success -> {
                        atlasBucketManager.replaceHighlight(highlightResult.atlases)
                        Log.d(TAG, "Highlight atlas generated: ${highlightResult.atlases.size} atlases")
                    }
                    is LODGenerationResult.Failed -> {
                        Log.w(TAG, "Highlight atlas generation failed: ${highlightResult.error}")
                        atlasBucketManager.clearHighlight()
                    }
                }
            }
        }
    }

    // Generation job tracking for cancellation
    private val activeJobs = mutableMapOf<LODLevel, Job>()
    private val jobsMutex = Mutex()

    // Request sequence for race condition prevention
    private var requestSequence: Long = 0

    // Streaming results emission
    private val atlasResultFlow = MutableSharedFlow<AtlasStreamResult>(
        replay = 1,
        extraBufferCapacity = 10
    )

    /**
     * Stream of atlas generation results.
     * UI subscribes to this for immediate updates as each LOD becomes ready.
     */
    fun getAtlasStream(): Flow<AtlasStreamResult> = atlasResultFlow
        .distinctUntilChangedBy { "${it.requestSequence}_${it.javaClass.simpleName}_${it.lodLevel}" }
        .filter { it.requestSequence > 0 }
        .onEach { result ->
            Log.d(TAG, "Emitting atlas result: ${result.javaClass.simpleName} for ${result.lodLevel}, sequence=${result.requestSequence}")
        }
        .flowOn(Dispatchers.Default)

    /**
     * Initialize persistent cache with ALL canvas photos.
     * Call this during app launch to ensure immediate UI feedback.
     */
    suspend fun initializePersistentCache(allCanvasPhotos: List<Media.Image>) {
        if (atlasBucketManager.isBaseBucketInitialized()) {
            Log.d(TAG, "Persistent cache already initialized")
            return
        }

        Log.d(TAG, "Initializing persistent cache with ${allCanvasPhotos.size} photos at LEVEL_0")

        val (result, duration) = measureTimedValue {
            lodSpecificGenerator.generateLODAtlas(
                photos = allCanvasPhotos.map { it.uri },
                lodLevel = LODLevel.LEVEL_0,
                currentZoom = 1.0f,
                priority = TypeSafeLODPriority(
                    priority = AtlasPriority.PersistentCache,
                    photos = allCanvasPhotos,
                    reason = "ALL canvas photos"
                )
            )
        }

        when (result) {
            is LODGenerationResult.Success -> {
                atlasBucketManager.populateBase(result.atlases)

                Log.d(TAG, "Persistent cache initialized: ${result.atlases.size} atlases, ${result.atlases.sumOf { it.regions.size }} photos in ${duration.inWholeMilliseconds}ms")

                // Emit immediate availability for UI
                atlasResultFlow.tryEmit(
                    AtlasStreamResult.LODReady(
                        requestSequence = ++requestSequence,
                        lodLevel = LODLevel.LEVEL_0,
                        atlases = result.atlases,
                        generationTimeMs = duration.inWholeMilliseconds,
                        reason = "Persistent cache ready - immediate UI rendering"
                    )
                )
            }

            is LODGenerationResult.Failed -> {
                Log.e(TAG, "Failed to initialize persistent cache: ${result.error}")
            }
        }
    }

    /**
     * Immediately clean up L7 atlas when photo is deselected (synchronous version).
     * This prevents memory waste from high-fidelity atlases that are no longer needed.
     * Can be called from main thread as it only removes elements from collections.
     */
    fun cleanupL7AtlasSync() {
        runBlocking {
            atlasMutex.withLock {
                val existingHighlightAtlases = atlasBucketManager.getAtlasesByLOD(LODLevel.LEVEL_7)
                if (existingHighlightAtlases.isNotEmpty()) {
                    Log.d(TAG, "CleanupL7Sync: Removing ${existingHighlightAtlases.size} L7 atlases after deselection")

                    // Clear highlight bucket (this handles bitmap recycling)
                    atlasBucketManager.clearHighlight()

                    // Notify UI immediately
                    atlasResultFlow.tryEmit(
                        AtlasStreamResult.AtlasRemoved(
                            requestSequence = ++requestSequence,
                            lodLevel = LODLevel.LEVEL_7,
                            reason = "Photo deselected - L7 no longer needed",
                            removedAtlasCount = existingHighlightAtlases.size
                        )
                    )

                    Log.d(TAG, "CleanupL7Sync: Successfully removed L7 atlas and notified UI")
                } else {
                    Log.d(TAG, "CleanupL7Sync: No L7 atlas to clean up")
                }
            }
        }
    }

    /**
     * Main entry point: Update visible cells with streaming atlas generation.
     * Immediately starts independent LOD coroutines based on smart priority selection.
     */
    suspend fun updateVisibleCellsStreaming(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        selectedMedia: Media? = null,
        selectionMode: SelectionMode = SelectionMode.CELL_MODE,
        activeCell: HexCellWithMedia? = null
    ) = trace(BenchmarkLabels.ATLAS_MANAGER_UPDATE_VISIBLE_CELLS) {
        withContext(Dispatchers.Default) {
            val sequence = ++requestSequence
            Log.d(TAG, "updateVisibleCellsStreaming: ${visibleCells.size} cells, zoom=$currentZoom, sequence=$sequence")

            // Emit immediate loading state
            atlasResultFlow.tryEmit(AtlasStreamResult.Loading(sequence, null, "Analyzing requirements..."))

            // Step 1: Smart LOD selection with upfront deduplication
            val typeSafePriorities = createLODPrioritiesList(
                visibleCells = visibleCells,
                currentZoom = currentZoom,
                selectedMedia = selectedMedia,
                selectionMode = selectionMode,
                activeCell = activeCell
            ).let { priorities ->
                // Apply upfront deduplication to prevent unnecessary atlas generation
                applyUpfrontDeduplication(priorities, currentZoom)
            }

            Log.d(TAG, "Smart LOD selection result: ${typeSafePriorities.size} priority levels")
            typeSafePriorities.forEach { priority ->
                Log.d(TAG, "  ${priority.priority::class.simpleName}: ${priority.photos.size} photos - ${priority.reason}")
            }

            // Early exit: If no priorities need generation, skip expensive operations
            if (typeSafePriorities.isEmpty()) {
                Log.d(TAG, "No atlas generation needed - all priorities satisfied by existing atlases")
                return@withContext
            }

            // Step 2: Check if persistent cache covers current request
            if (atlasBucketManager.isBaseBucketInitialized()) {
                Log.d(TAG, "Persistent cache available - immediate fallback rendering")
                val persistentAtlases = atlasBucketManager.getBaseBucketAtlases()
                atlasResultFlow.tryEmit(
                    AtlasStreamResult.LODReady(
                        requestSequence = sequence,
                        lodLevel = LODLevel.LEVEL_0,
                        atlases = persistentAtlases,
                        generationTimeMs = 0,
                        reason = "Persistent cache - immediate rendering"
                    )
                )
            }

            // Step 3: Cancel any conflicting previous generations
            cancelPreviousGenerations(typeSafePriorities.map { calculateEffectiveLOD(it, currentZoom) })

            // Step 4: Launch independent LOD coroutines with immediate emission
            coroutineScope {
                typeSafePriorities.forEach { priority ->
                    val effectiveLOD = calculateEffectiveLOD(priority, currentZoom)

                    // Skip LEVEL_0 if we already have persistent cache
                    if (effectiveLOD == LODLevel.LEVEL_0 && atlasBucketManager.isBaseBucketInitialized()) {
                        Log.d(TAG, "Skipping LEVEL_0 generation - persistent cache available")
                        return@forEach
                    }

                    val job = async {
                        generateLODIndependently(
                            priority = priority,
                            effectiveLOD = effectiveLOD,
                            requestSequence = sequence,
                            currentZoom = currentZoom
                        )
                    }

                    // Track job for cancellation
                    jobsMutex.withLock {
                        activeJobs[effectiveLOD] = job
                    }
                }
            }
        }
    }

    /**
     * Generate specific LOD level independently and emit result immediately
     */
    private suspend fun generateLODIndependently(
        priority: TypeSafeLODPriority,
        effectiveLOD: LODLevel,
        requestSequence: Long,
        currentZoom: Float
    ) = trace("${BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS}_$effectiveLOD") {
        try {
            currentCoroutineContext().ensureActive()

            Log.d(TAG, "Starting independent generation: $effectiveLOD (${priority.photos.size} photos)")

            // Emit immediate progress update
            atlasResultFlow.tryEmit(
                AtlasStreamResult.Progress(
                    requestSequence = requestSequence,
                    lodLevel = effectiveLOD,
                    message = "Generating $effectiveLOD atlas...",
                    progress = 0f
                )
            )

            val (result, duration) = measureTimedValue {
                lodSpecificGenerator.generateLODAtlas(
                    photos = priority.photos.map { it.uri },
                    lodLevel = effectiveLOD,
                    currentZoom = currentZoom,
                    priority = priority
                )
            }

            currentCoroutineContext().ensureActive()

            when (result) {
                is LODGenerationResult.Success -> {
                    // Update bucket storage based on LOD level and context
                    atlasMutex.withLock {
                        when (effectiveLOD) {
                            LODLevel.LEVEL_0 -> {
                                if (!atlasBucketManager.isBaseBucketInitialized()) {
                                    atlasBucketManager.populateBase(result.atlases)
                                    Log.d(TAG, "Populated base bucket from LEVEL_0 generation")
                                }
                            }
                            LODLevel.LEVEL_7 -> {
                                // L7 goes to highlight bucket for selected photos
                                atlasBucketManager.replaceHighlight(result.atlases)
                                Log.d(TAG, "Updated highlight bucket with L7 atlases")
                            }
                            else -> {
                                // Other LODs go to rolling bucket
                                atlasBucketManager.addToRolling(result.atlases)
                                Log.d(TAG, "Added ${effectiveLOD.name} to rolling bucket")
                            }
                        }
                    }

                    Log.d(TAG, "LOD $effectiveLOD generation complete: ${result.atlases.size} atlases in ${duration.inWholeMilliseconds}ms")

                    // Note: Atlas cleanup removed to prevent LOD gaps and improve higher LOD retention

                    // Emit immediate success - UI can render this LOD now!
                    val lodReadyResult = AtlasStreamResult.LODReady(
                        requestSequence = requestSequence,
                        lodLevel = effectiveLOD,
                        atlases = result.atlases,
                        generationTimeMs = duration.inWholeMilliseconds,
                        reason = priority.reason
                    )
                    Log.d(TAG, "Attempting to emit LODReady for $effectiveLOD, sequence=$requestSequence")
                    val emitSuccess = atlasResultFlow.tryEmit(lodReadyResult)
                    Log.d(TAG, "LODReady emission result: $emitSuccess for $effectiveLOD")
                }

                is LODGenerationResult.Failed -> {
                    Log.w(TAG, "LOD $effectiveLOD generation failed: ${result.error}")

                    atlasResultFlow.tryEmit(
                        AtlasStreamResult.LODFailed(
                            requestSequence = requestSequence,
                            lodLevel = effectiveLOD,
                            error = result.error,
                            retryable = result.retryable
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in LOD $effectiveLOD generation", e)

            atlasResultFlow.tryEmit(
                AtlasStreamResult.LODFailed(
                    requestSequence = requestSequence,
                    lodLevel = effectiveLOD,
                    error = e.message ?: "Unknown error",
                    retryable = true
                )
            )
        } finally {
            // Clean up job tracking
            jobsMutex.withLock {
                activeJobs.remove(effectiveLOD)
            }
        }
    }

    /**
     * Cancel previous generations for conflicting LOD levels
     */
    private suspend fun cancelPreviousGenerations(newLODLevels: List<LODLevel>) {
        jobsMutex.withLock {
            newLODLevels.forEach { lodLevel ->
                activeJobs.remove(lodLevel)?.cancel()
            }
        }

        // Small delay to ensure cancellation completes
        delay(THROTTLE_DELAY_MS)
    }

    /**
     * Get current atlases for specific LOD level from bucket system
     */
    suspend fun getCurrentAtlases(lodLevel: LODLevel): List<TextureAtlas> = atlasMutex.withLock {
        atlasBucketManager.getAtlasesByLOD(lodLevel)
    }

    /**
     * Get all current atlases across all LOD levels from bucket system
     */
    suspend fun getAllCurrentAtlases(): Map<LODLevel, List<TextureAtlas>> = atlasMutex.withLock {
        atlasBucketManager.snapshotAll().groupBy { it.lodLevel }
    }

    /**
     * Get persistent cache atlases (LEVEL_0 with ALL photos) from base bucket
     */
    suspend fun getPersistentCache(): List<TextureAtlas>? = atlasMutex.withLock {
        return@withLock if (atlasBucketManager.isBaseBucketInitialized()) {
            atlasBucketManager.getBaseBucketAtlases()
        } else {
            null
        }
    }

    /**
     * Get best available atlas region for photo (highest LOD available) from bucket system
     */
    suspend fun getBestPhotoRegion(photoId: Uri): Pair<TextureAtlas, AtlasRegion>? = atlasMutex.withLock {
        atlasBucketManager.getBestRegion(photoId)?.let { region ->
            // Find the atlas containing this region
            atlasBucketManager.snapshotAll().forEach { atlas ->
                if (atlas.regions.containsKey(photoId)) {
                    return@withLock atlas to region
                }
            }
        }
        return@withLock null
    }

    /**
     * Get atlas region for specific photo at specific LOD (with fallback using bucket system)
     */
    suspend fun getPhotoRegion(photoId: Uri, preferredLOD: LODLevel? = null): AtlasRegion? = atlasMutex.withLock {
        // Use bucket system's built-in best region lookup
        atlasBucketManager.getBestRegion(photoId)
    }

    /**
     * Get SmartMemoryManager for debug overlay and memory status
     */
    fun getSmartMemoryManager(): SmartMemoryManager = lodSpecificGenerator.getSmartMemoryManager()

    /**
     * Get AtlasBucketManager for direct bucket access
     */
    fun getAtlasBucketManager(): dev.serhiiyaremych.lumina.domain.bucket.AtlasBucketManager = atlasBucketManager

    // Helper methods

    private fun getHigherLOD(currentLOD: LODLevel): LODLevel {
        val currentIndex = LODLevel.entries.indexOf(currentLOD)
        return if (currentIndex < LODLevel.entries.size - 1) {
            LODLevel.entries[currentIndex + 1]
        } else {
            currentLOD
        }
    }

    /**
     * Calculate effective LOD level for a type-safe priority
     */
    private fun calculateEffectiveLOD(priority: TypeSafeLODPriority, currentZoom: Float): LODLevel {
        val visibleLOD = LODLevel.forZoom(currentZoom)
        val result = when (priority.priority) {
            AtlasPriority.PersistentCache -> LODLevel.LEVEL_0
            AtlasPriority.VisibleCells -> visibleLOD
            AtlasPriority.ActiveCell -> getHigherLOD(visibleLOD)
            AtlasPriority.SelectedPhoto -> LODLevel.LEVEL_7
        }
        Log.d(TAG, "calculateEffectiveLOD: ${priority.priority::class.simpleName} at zoom $currentZoom -> visibleLOD=$visibleLOD, result=$result, photos=${priority.photos.size}")
        return result
    }

    // Type-safe priority system adapter methods

    /**
     * Create type-safe LOD priorities from context information.
     * Replaces the unsafe integer-based priority system with clear semantics.
     */
    private suspend fun createLODPrioritiesList(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        selectedMedia: Media?,
        selectionMode: SelectionMode,
        activeCell: HexCellWithMedia?
    ): List<TypeSafeLODPriority> {
        val priorities = mutableListOf<TypeSafeLODPriority>()
        val allPhotos = visibleCells.flatMap { cell ->
            cell.mediaItems.mapNotNull { it.media as? Media.Image }
        }

        // Persistent cache priority - only if not initialized
        if (!atlasBucketManager.isBaseBucketInitialized() && allPhotos.isNotEmpty()) {
            priorities.add(
                TypeSafeLODPriority(
                    priority = AtlasPriority.PersistentCache,
                    photos = allPhotos,
                    reason = "Initialize persistent cache"
                )
            )
        }

        // Visible cells priority
        val visiblePhotos = if (selectionMode == SelectionMode.PHOTO_MODE && selectedMedia != null) {
            allPhotos.filter { it.uri != selectedMedia.uri }
        } else {
            allPhotos
        }

        if (visiblePhotos.isNotEmpty()) {
            priorities.add(
                TypeSafeLODPriority(
                    priority = AtlasPriority.VisibleCells,
                    photos = visiblePhotos,
                    reason = "Visible cells at zoom $currentZoom"
                )
            )
        }

        // Active cell priority
        if (activeCell != null && selectionMode == SelectionMode.CELL_MODE) {
            val activePhotos = activeCell.mediaItems.mapNotNull { it.media as? Media.Image }
            if (activePhotos.isNotEmpty()) {
                Log.d(TAG, "Creating ActiveCell priority: activeCell=${activeCell.hexCell.q},${activeCell.hexCell.r}, selectionMode=$selectionMode, photos=${activePhotos.size}")
                priorities.add(
                    TypeSafeLODPriority(
                        priority = AtlasPriority.ActiveCell,
                        photos = activePhotos,
                        reason = "Active cell enhancement"
                    )
                )
            }
        }

        // Selected photo priority
        if (selectedMedia is Media.Image && selectionMode == SelectionMode.PHOTO_MODE) {
            Log.d(TAG, "Creating SelectedPhoto priority for: ${selectedMedia.uri.lastPathSegment}")
            priorities.add(
                TypeSafeLODPriority(
                    priority = AtlasPriority.SelectedPhoto,
                    photos = listOf(selectedMedia),
                    reason = "Selected photo maximum quality"
                )
            )
        }

        return priorities
    }

    /**
     * Apply upfront deduplication to prevent unnecessary atlas generation.
     * Filters out photos that already exist at higher LOD levels.
     */
    private suspend fun applyUpfrontDeduplication(
        priorities: List<TypeSafeLODPriority>,
        currentZoom: Float
    ): List<TypeSafeLODPriority> {
        if (priorities.isEmpty()) return priorities

        Log.d(TAG, "UpfrontDeduplication: Checking ${priorities.size} priorities against existing atlases")
        val bucketSummary = atlasBucketManager.snapshotAll().groupBy { it.lodLevel }.mapValues { it.value.size }
        Log.d(TAG, "UpfrontDeduplication: Current bucket state: ${bucketSummary.map { "${it.key.name}(${it.value})" }}")

        // Build map of existing photos at their highest LOD levels from bucket system
        val existingPhotoToHighestLOD = mutableMapOf<Uri, LODLevel>()
        atlasBucketManager.snapshotAll().forEach { atlas ->
            atlas.regions.keys.forEach { photoUri ->
                val current = existingPhotoToHighestLOD[photoUri]
                if (current == null || atlas.lodLevel.level > current.level) {
                    existingPhotoToHighestLOD[photoUri] = atlas.lodLevel
                }
            }
        }

        if (existingPhotoToHighestLOD.isEmpty()) {
            Log.d(TAG, "UpfrontDeduplication: No existing atlases, keeping all priorities")
            return priorities
        }

        Log.d(TAG, "UpfrontDeduplication: Found existing photos at LOD levels: ${existingPhotoToHighestLOD.entries.take(3).map { "${it.key.lastPathSegment}→${it.value.name}" }}")

        // Filter each priority to remove photos that already exist at higher LOD levels
        val deduplicatedPriorities = priorities.mapNotNull { priority ->
            val targetLOD = calculateEffectiveLOD(priority, currentZoom)
            Log.d(TAG, "UpfrontDeduplication: Processing ${priority.priority::class.simpleName} → target LOD: ${targetLOD.name}")

            val photosToGenerate = priority.photos.filter { photo ->
                val existingLOD = existingPhotoToHighestLOD[photo.uri]
                val shouldGenerate = existingLOD == null || targetLOD.level > existingLOD.level
                if (!shouldGenerate) {
                    Log.d(TAG, "UpfrontDeduplication: Skipping photo ${photo.uri.lastPathSegment} - exists at ${existingLOD?.name}, target is ${targetLOD.name}")
                }
                shouldGenerate
            }

            if (photosToGenerate.isEmpty()) {
                Log.d(TAG, "UpfrontDeduplication: SKIPPING ${priority.priority::class.simpleName} - all photos already exist at higher LOD")
                null // Skip this priority entirely
            } else if (photosToGenerate.size < priority.photos.size) {
                Log.d(TAG, "UpfrontDeduplication: Filtering ${priority.priority::class.simpleName} from ${priority.photos.size} to ${photosToGenerate.size} photos")
                TypeSafeLODPriority(
                    priority = priority.priority,
                    photos = photosToGenerate,
                    reason = "${priority.reason} (filtered: ${photosToGenerate.size}/${priority.photos.size} photos)"
                )
            } else {
                Log.d(TAG, "UpfrontDeduplication: Keeping ${priority.priority::class.simpleName} unchanged (${priority.photos.size} photos)")
                priority // Keep as-is
            }
        }

        Log.d(TAG, "UpfrontDeduplication: Reduced from ${priorities.size} to ${deduplicatedPriorities.size} priorities")
        return deduplicatedPriorities
    }
}

/**
 * Streaming atlas results - immediate UI updates
 */
sealed class AtlasStreamResult {
    abstract val requestSequence: Long
    abstract val lodLevel: LODLevel?

    /**
     * Initial loading state
     */
    data class Loading(
        override val requestSequence: Long,
        override val lodLevel: LODLevel?,
        val message: String
    ) : AtlasStreamResult()

    /**
     * Progress update during generation
     */
    data class Progress(
        override val requestSequence: Long,
        override val lodLevel: LODLevel,
        val message: String,
        val progress: Float
    ) : AtlasStreamResult()

    /**
     * LOD level ready - UI can immediately use these atlases
     */
    data class LODReady(
        override val requestSequence: Long,
        override val lodLevel: LODLevel,
        val atlases: List<TextureAtlas>,
        val generationTimeMs: Long,
        val reason: String
    ) : AtlasStreamResult()

    /**
     * LOD level generation failed
     */
    data class LODFailed(
        override val requestSequence: Long,
        override val lodLevel: LODLevel,
        val error: String,
        val retryable: Boolean
    ) : AtlasStreamResult()

    /**
     * All requested LODs complete
     */
    data class AllComplete(
        override val requestSequence: Long,
        val totalAtlases: Int,
        val totalGenerationTimeMs: Long
    ) : AtlasStreamResult() {
        override val lodLevel: LODLevel? = null
    }

    /**
     * Atlas removed due to cleanup - UI should remove from its state
     */
    data class AtlasRemoved(
        override val requestSequence: Long,
        override val lodLevel: LODLevel,
        val reason: String,
        val removedAtlasCount: Int
    ) : AtlasStreamResult()
}
