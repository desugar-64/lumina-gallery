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
        .distinctUntilChangedBy { it.requestSequence }
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
                priority = LODPriority(
                    level = 0,
                    lodLevel = LODLevel.LEVEL_0,
                    photos = allCanvasPhotos,
                    reason = "Persistent cache - ALL canvas photos"
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

            // Step 1: Smart LOD selection - decide which LODs to generate
            val lodPriorities = smartLODSelection(
                visibleCells = visibleCells,
                currentZoom = currentZoom,
                selectedMedia = selectedMedia,
                selectionMode = selectionMode,
                activeCell = activeCell
            )

            Log.d(TAG, "Smart LOD selection result: ${lodPriorities.size} priority levels")
            lodPriorities.forEach { priority ->
                Log.d(TAG, "  ${priority.level}: ${priority.lodLevel} (${priority.photos.size} photos) - ${priority.reason}")
            }

            // Step 2: Check if persistent cache covers current request
            if (persistentCache != null && lodPriorities.isNotEmpty()) {
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
            cancelPreviousGenerations(lodPriorities.map { it.lodLevel })

            // Step 4: Launch independent LOD coroutines with immediate emission
            coroutineScope {
                lodPriorities.forEach { priority ->
                    // Skip LEVEL_0 if we already have persistent cache
                    if (priority.lodLevel == LODLevel.LEVEL_0 && persistentCache != null) {
                        Log.d(TAG, "Skipping LEVEL_0 generation - persistent cache available")
                        return@forEach
                    }
                    
                    val job = async {
                        generateLODIndependently(
                            priority = priority,
                            requestSequence = sequence
                        )
                    }
                    
                    // Track job for cancellation
                    jobsMutex.withLock {
                        activeJobs[priority.lodLevel] = job
                    }
                }
            }
        }
    }

    /**
     * Smart LOD selection logic - determines which LOD levels to generate based on context
     */
    private suspend fun smartLODSelection(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        selectedMedia: Media?,
        selectionMode: SelectionMode,
        activeCell: HexCellWithMedia?
    ): List<LODPriority> {
        val priorities = mutableListOf<LODPriority>()
        val allPhotos = visibleCells.flatMap { cell ->
            cell.mediaItems.mapNotNull { it.media as? Media.Image }
        }

        // Priority 1: Persistent cache (LEVEL_0) - only if not initialized
        if (persistentCache == null && allPhotos.isNotEmpty()) {
            priorities.add(LODPriority(
                level = 1,
                lodLevel = LODLevel.LEVEL_0,
                photos = allPhotos,
                reason = "Initialize persistent cache"
            ))
        }

        // Priority 2: Visible cells - Current zoom-appropriate LOD
        val visibleLOD = LODLevel.forZoom(currentZoom)
        val visiblePhotos = if (selectionMode == SelectionMode.PHOTO_MODE && selectedMedia != null) {
            // In photo mode, exclude selected photo from visible set (it gets higher priority)
            allPhotos.filter { it.uri != selectedMedia.uri }
        } else {
            allPhotos
        }
        
        if (visiblePhotos.isNotEmpty() && visibleLOD != LODLevel.LEVEL_0) {
            priorities.add(LODPriority(
                level = 2,
                lodLevel = visibleLOD,
                photos = visiblePhotos,
                reason = "Visible cells at zoom $currentZoom"
            ))
        }

        // Priority 3: Active cell - +1 higher LOD than visible
        if (activeCell != null && selectionMode == SelectionMode.CELL_MODE) {
            val activeLOD = getHigherLOD(visibleLOD)
            val activePhotos = activeCell.mediaItems.mapNotNull { it.media as? Media.Image }
            
            if (activePhotos.isNotEmpty() && activeLOD != visibleLOD) {
                priorities.add(LODPriority(
                    level = 3,
                    lodLevel = activeLOD,
                    photos = activePhotos,
                    reason = "Active cell enhancement"
                ))
            }
        }

        // Priority 4: Selected photo - Highest LOD for immediate quality
        if (selectedMedia is Media.Image && selectionMode == SelectionMode.PHOTO_MODE) {
            priorities.add(LODPriority(
                level = 4,
                lodLevel = LODLevel.LEVEL_7, // Always highest for selected
                photos = listOf(selectedMedia),
                reason = "Selected photo maximum quality"
            ))
        }

        return priorities.sortedBy { it.level }
    }

    /**
     * Generate specific LOD level independently and emit result immediately
     */
    private suspend fun generateLODIndependently(
        priority: LODPriority,
        requestSequence: Long
    ) = trace("${BenchmarkLabels.ATLAS_MANAGER_GENERATE_ATLAS}_${priority.lodLevel}") {
        try {
            currentCoroutineContext().ensureActive()
            
            Log.d(TAG, "Starting independent generation: ${priority.lodLevel} (${priority.photos.size} photos)")
            
            // Emit immediate progress update
            atlasResultFlow.tryEmit(AtlasStreamResult.Progress(
                requestSequence = requestSequence,
                lodLevel = priority.lodLevel,
                message = "Generating ${priority.lodLevel} atlas...",
                progress = 0f
            ))

            val (result, duration) = measureTimedValue {
                lodSpecificGenerator.generateLODAtlas(
                    photos = priority.photos.map { it.uri },
                    lodLevel = priority.lodLevel,
                    currentZoom = 1.0f, // Use base zoom for LOD-specific generation
                    priority = priority
                )
            }

            currentCoroutineContext().ensureActive()

            when (result) {
                is LODGenerationResult.Success -> {
                    // Update internal state
                    atlasMutex.withLock {
                        currentAtlases[priority.lodLevel] = result.atlases
                        
                        // If this is LEVEL_0 and we don't have persistent cache, set it
                        if (priority.lodLevel == LODLevel.LEVEL_0 && persistentCache == null) {
                            persistentCache = result.atlases
                            Log.d(TAG, "Set persistent cache from LEVEL_0 generation")
                        }
                    }

                    Log.d(TAG, "LOD ${priority.lodLevel} generation complete: ${result.atlases.size} atlases in ${duration.inWholeMilliseconds}ms")
                    
                    // Emit immediate success - UI can render this LOD now!
                    atlasResultFlow.tryEmit(AtlasStreamResult.LODReady(
                        requestSequence = requestSequence,
                        lodLevel = priority.lodLevel,
                        atlases = result.atlases,
                        generationTimeMs = duration.inWholeMilliseconds,
                        reason = priority.reason
                    ))
                }
                
                is LODGenerationResult.Failed -> {
                    Log.w(TAG, "LOD ${priority.lodLevel} generation failed: ${result.error}")
                    
                    atlasResultFlow.tryEmit(AtlasStreamResult.LODFailed(
                        requestSequence = requestSequence,
                        lodLevel = priority.lodLevel,
                        error = result.error,
                        retryable = result.retryable
                    ))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception in LOD ${priority.lodLevel} generation", e)
            
            atlasResultFlow.tryEmit(AtlasStreamResult.LODFailed(
                requestSequence = requestSequence,
                lodLevel = priority.lodLevel,
                error = e.message ?: "Unknown error",
                retryable = true
            ))
        } finally {
            // Clean up job tracking
            jobsMutex.withLock {
                activeJobs.remove(priority.lodLevel)
            }
        }
    }

    /**
     * Cancel previous generations for conflicting LOD levels
     */
    private suspend fun cancelPreviousGenerations(newLODLevels: List<LODLevel>) {
        jobsMutex.withLock {
            newLODLevels.forEach { lodLevel ->
                activeJobs[lodLevel]?.cancel()
                activeJobs.remove(lodLevel)
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

    // Helper methods

    private fun getHigherLOD(currentLOD: LODLevel): LODLevel {
        val currentIndex = LODLevel.entries.indexOf(currentLOD)
        return if (currentIndex < LODLevel.entries.size - 1) {
            LODLevel.entries[currentIndex + 1]
        } else {
            currentLOD
        }
    }
}

/**
 * LOD generation priority with context
 */
data class LODPriority(
    val level: Int,
    val lodLevel: LODLevel,
    val photos: List<Media.Image>,
    val reason: String
)

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
}