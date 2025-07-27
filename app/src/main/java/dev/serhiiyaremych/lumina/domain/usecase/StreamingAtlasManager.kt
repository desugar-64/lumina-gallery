package dev.serhiiyaremych.lumina.domain.usecase

import android.net.Uri
import android.util.Log
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.domain.model.AtlasRegion
import dev.serhiiyaremych.lumina.domain.model.AtlasPriority
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.domain.model.TypeSafeLODPriority
import dev.serhiiyaremych.lumina.ui.SelectionMode
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.measureTimedValue

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
    private val bitmapAtlasPool: BitmapAtlasPool
) {
    companion object {
        private const val TAG = "StreamingAtlasManager"
        private const val DEBOUNCE_DELAY_MS = 50L
        private const val THROTTLE_DELAY_MS = 16L // ~60fps
    }

    // Persistent cache for ALL canvas photos at LEVEL_0 (never cleared)
    private var persistentCache: List<TextureAtlas>? = null

    // Active atlas state management
    private val currentAtlases = mutableMapOf<LODLevel, List<TextureAtlas>>()
    private val atlasMutex = Mutex()

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
        if (persistentCache != null) {
            Log.d(TAG, "Persistent cache already initialized with ${persistentCache!!.sumOf { it.regions.size }} photos")
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
                atlasMutex.withLock {
                    persistentCache = result.atlases
                    currentAtlases[LODLevel.LEVEL_0] = result.atlases
                }

                Log.d(TAG, "Persistent cache initialized: ${result.atlases.size} atlases, ${result.atlases.sumOf { it.regions.size }} photos in ${duration.inWholeMilliseconds}ms")

                // Emit immediate availability for UI
                atlasResultFlow.tryEmit(AtlasStreamResult.LODReady(
                    requestSequence = ++requestSequence,
                    lodLevel = LODLevel.LEVEL_0,
                    atlases = result.atlases,
                    generationTimeMs = duration.inWholeMilliseconds,
                    reason = "Persistent cache ready - immediate UI rendering"
                ))
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
                val l7Atlases = currentAtlases[LODLevel.LEVEL_7]
                if (l7Atlases != null && l7Atlases.isNotEmpty()) {
                    Log.d(TAG, "CleanupL7Sync: Removing ${l7Atlases.size} L7 atlases after deselection")

                    // Recycle bitmaps
                    l7Atlases.forEach { atlas ->
                        if (!atlas.bitmap.isRecycled) {
                            atlas.bitmap.recycle()
                        }
                    }

                    // Remove from state
                    currentAtlases.remove(LODLevel.LEVEL_7)

                    // Notify UI immediately
                    atlasResultFlow.tryEmit(AtlasStreamResult.AtlasRemoved(
                        requestSequence = ++requestSequence,
                        lodLevel = LODLevel.LEVEL_7,
                        reason = "Photo deselected - L7 no longer needed",
                        removedAtlasCount = l7Atlases.size
                    ))

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
            if (persistentCache != null) {
                Log.d(TAG, "Persistent cache available - immediate fallback rendering")
                atlasResultFlow.tryEmit(AtlasStreamResult.LODReady(
                    requestSequence = sequence,
                    lodLevel = LODLevel.LEVEL_0,
                    atlases = persistentCache!!,
                    generationTimeMs = 0,
                    reason = "Persistent cache - immediate rendering"
                ))
            }

            // Step 3: Cancel any conflicting previous generations
            cancelPreviousGenerations(typeSafePriorities.map { calculateEffectiveLOD(it, currentZoom) })

            // Step 4: Launch independent LOD coroutines with immediate emission
            coroutineScope {
                typeSafePriorities.forEach { priority ->
                    val effectiveLOD = calculateEffectiveLOD(priority, currentZoom)

                    // Skip LEVEL_0 if we already have persistent cache
                    if (effectiveLOD == LODLevel.LEVEL_0 && persistentCache != null) {
                        Log.d(TAG, "Skipping LEVEL_0 generation - persistent cache available")
                        return@forEach
                    }

                    val job = async {
                        generateLODIndependently(
                            priority = priority,
                            effectiveLOD = effectiveLOD,
                            requestSequence = sequence
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
        requestSequence: Long
    ) = trace("${BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS}_${effectiveLOD}") {
        try {
            currentCoroutineContext().ensureActive()

            Log.d(TAG, "Starting independent generation: $effectiveLOD (${priority.photos.size} photos)")

            // Emit immediate progress update
            atlasResultFlow.tryEmit(AtlasStreamResult.Progress(
                requestSequence = requestSequence,
                lodLevel = effectiveLOD,
                message = "Generating $effectiveLOD atlas...",
                progress = 0f
            ))

            val (result, duration) = measureTimedValue {
                lodSpecificGenerator.generateLODAtlas(
                    photos = priority.photos.map { it.uri },
                    lodLevel = effectiveLOD,
                    currentZoom = 1.0f, // Use base zoom for LOD-specific generation
                    priority = priority
                )
            }

            currentCoroutineContext().ensureActive()

            when (result) {
                is LODGenerationResult.Success -> {
                    // Update internal state and clean up redundant atlases
                    atlasMutex.withLock {
                        currentAtlases[effectiveLOD] = result.atlases

                        // If this is LEVEL_0 and we don't have persistent cache, set it
                        if (effectiveLOD == LODLevel.LEVEL_0 && persistentCache == null) {
                            persistentCache = result.atlases
                            Log.d(TAG, "Set persistent cache from LEVEL_0 generation")
                        }

                        // Note: Redundant atlas cleanup removed to prevent LOD gaps
                        // Previous cleanup was too aggressive and removed intermediate LOD levels
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
                    Log.d(TAG, "Attempting to emit LODReady for ${effectiveLOD}, sequence=$requestSequence")
                    val emitSuccess = atlasResultFlow.tryEmit(lodReadyResult)
                    Log.d(TAG, "LODReady emission result: $emitSuccess for $effectiveLOD")
                }

                is LODGenerationResult.Failed -> {
                    Log.w(TAG, "LOD $effectiveLOD generation failed: ${result.error}")

                    atlasResultFlow.tryEmit(AtlasStreamResult.LODFailed(
                        requestSequence = requestSequence,
                        lodLevel = effectiveLOD,
                        error = result.error,
                        retryable = result.retryable
                    ))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception in LOD $effectiveLOD generation", e)

            atlasResultFlow.tryEmit(AtlasStreamResult.LODFailed(
                requestSequence = requestSequence,
                lodLevel = effectiveLOD,
                error = e.message ?: "Unknown error",
                retryable = true
            ))
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
     * Get current atlases for specific LOD level
     */
    suspend fun getCurrentAtlases(lodLevel: LODLevel): List<TextureAtlas> = atlasMutex.withLock {
        currentAtlases[lodLevel] ?: emptyList()
    }

    /**
     * Get all current atlases across all LOD levels
     */
    suspend fun getAllCurrentAtlases(): Map<LODLevel, List<TextureAtlas>> = atlasMutex.withLock {
        currentAtlases.toMap()
    }

    /**
     * Get persistent cache atlases (LEVEL_0 with ALL photos)
     */
    suspend fun getPersistentCache(): List<TextureAtlas>? = atlasMutex.withLock {
        persistentCache
    }

    /**
     * Get best available atlas region for photo (highest LOD available)
     */
    suspend fun getBestPhotoRegion(photoId: Uri): Pair<TextureAtlas, AtlasRegion>? = atlasMutex.withLock {
        // Search from highest to lowest LOD for best quality
        LODLevel.entries.reversed().forEach { lodLevel ->
            currentAtlases[lodLevel]?.forEach { atlas ->
                atlas.regions[photoId]?.let { region ->
                    return@withLock atlas to region
                }
            }
        }

        // Fallback to persistent cache if available
        persistentCache?.forEach { atlas ->
            atlas.regions[photoId]?.let { region ->
                return@withLock atlas to region
            }
        }

        return@withLock null
    }

    /**
     * Get atlas region for specific photo at specific LOD (with fallback to persistent cache)
     */
    suspend fun getPhotoRegion(photoId: Uri, preferredLOD: LODLevel? = null): AtlasRegion? = atlasMutex.withLock {
        // First try preferred LOD
        preferredLOD?.let { lod ->
            currentAtlases[lod]?.firstNotNullOfOrNull { atlas ->
                atlas.regions[photoId]
            }?.let { return@withLock it }
        }

        // Then try any available LOD (highest first)
        LODLevel.entries.reversed().forEach { lodLevel ->
            currentAtlases[lodLevel]?.firstNotNullOfOrNull { atlas ->
                atlas.regions[photoId]
            }?.let { return@withLock it }
        }

        // Finally fallback to persistent cache
        persistentCache?.firstNotNullOfOrNull { atlas ->
            atlas.regions[photoId]
        }
    }

    /**
     * Get SmartMemoryManager for debug overlay and memory status
     */
    fun getSmartMemoryManager(): SmartMemoryManager {
        return lodSpecificGenerator.getSmartMemoryManager()
    }

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
        return when (priority.priority) {
            AtlasPriority.PersistentCache -> LODLevel.LEVEL_0
            AtlasPriority.VisibleCells -> visibleLOD
            AtlasPriority.ActiveCell -> getHigherLOD(visibleLOD)
            AtlasPriority.SelectedPhoto -> LODLevel.LEVEL_7
        }
    }



    // Type-safe priority system adapter methods

    /**
     * Create type-safe LOD priorities from context information.
     * Replaces the unsafe integer-based priority system with clear semantics.
     */
    private fun createLODPrioritiesList(
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
        if (persistentCache == null && allPhotos.isNotEmpty()) {
            priorities.add(TypeSafeLODPriority(
                priority = AtlasPriority.PersistentCache,
                photos = allPhotos,
                reason = "Initialize persistent cache"
            ))
        }

        // Visible cells priority
        val visiblePhotos = if (selectionMode == SelectionMode.PHOTO_MODE && selectedMedia != null) {
            allPhotos.filter { it.uri != selectedMedia.uri }
        } else {
            allPhotos
        }

        if (visiblePhotos.isNotEmpty()) {
            priorities.add(TypeSafeLODPriority(
                priority = AtlasPriority.VisibleCells,
                photos = visiblePhotos,
                reason = "Visible cells at zoom $currentZoom"
            ))
        }

        // Active cell priority
        if (activeCell != null && selectionMode == SelectionMode.CELL_MODE) {
            val activePhotos = activeCell.mediaItems.mapNotNull { it.media as? Media.Image }
            if (activePhotos.isNotEmpty()) {
                priorities.add(TypeSafeLODPriority(
                    priority = AtlasPriority.ActiveCell,
                    photos = activePhotos,
                    reason = "Active cell enhancement"
                ))
            }
        }

        // Selected photo priority
        if (selectedMedia is Media.Image && selectionMode == SelectionMode.PHOTO_MODE) {
            Log.d(TAG, "Creating SelectedPhoto priority for: ${selectedMedia.uri.lastPathSegment}")
            priorities.add(TypeSafeLODPriority(
                priority = AtlasPriority.SelectedPhoto,
                photos = listOf(selectedMedia),
                reason = "Selected photo maximum quality"
            ))
        }

        return priorities
    }

    /**
     * Apply upfront deduplication to prevent unnecessary atlas generation.
     * Filters out photos that already exist at higher LOD levels.
     */
    private fun applyUpfrontDeduplication(
        priorities: List<TypeSafeLODPriority>,
        currentZoom: Float
    ): List<TypeSafeLODPriority> {
        if (priorities.isEmpty()) return priorities

        Log.d(TAG, "UpfrontDeduplication: Checking ${priorities.size} priorities against existing atlases")
        Log.d(TAG, "UpfrontDeduplication: Current atlas state: ${currentAtlases.keys.map { "${it.name}(${currentAtlases[it]?.size ?: 0})" }}")

        // Build map of existing photos at their highest LOD levels
        val existingPhotoToHighestLOD = mutableMapOf<Uri, LODLevel>()
        currentAtlases.forEach { (lodLevel, atlases) ->
            atlases.forEach { atlas ->
                atlas.regions.keys.forEach { photoUri ->
                    val current = existingPhotoToHighestLOD[photoUri]
                    if (current == null || lodLevel.level > current.level) {
                        existingPhotoToHighestLOD[photoUri] = lodLevel
                    }
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
