package dev.serhiiyaremych.lumina.ui.gallery

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.HexGridLayout
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.domain.usecase.AtlasStreamResult
import dev.serhiiyaremych.lumina.domain.usecase.GenerateHexGridLayoutUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GetMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GroupMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GroupingPeriod
import dev.serhiiyaremych.lumina.domain.usecase.StreamingAtlasManager
import dev.serhiiyaremych.lumina.ui.SelectionMode
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Streaming Gallery ViewModel - UDF Architecture Implementation
 *
 * Follows Android's recommended UDF (Unidirectional Data Flow) architecture pattern.
 * Provides a single unified UI state instead of multiple separate StateFlows.
 *
 * Key Features:
 * - Single UI state following UDF architecture principles
 * - Streaming atlas results with immediate UI updates
 * - Progressive LOD enhancement (LEVEL_0 → LEVEL_2 → LEVEL_4 → LEVEL_7)
 * - Persistent cache management for instant fallback rendering
 * - Debounced user interactions to prevent atlas generation spam
 * - Memory-efficient atlas state management
 * 
 * Reference: https://developer.android.com/develop/ui/compose/architecture
 */
@HiltViewModel
class StreamingGalleryViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val groupMediaUseCase: GroupMediaUseCase,
    private val generateHexGridLayoutUseCase: GenerateHexGridLayoutUseCase,
    private val streamingAtlasManager: StreamingAtlasManager
) : ViewModel() {

    // Single UI state following UDF architecture
    private val _uiState = MutableStateFlow(GalleryUiState.initial())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Current request tracking for UI updates
    private var currentRequestSequence: Long = 0

    // Selection tracking for cleanup
    private var lastSelectedMedia: Media? = null

    // Debouncing for rapid user interactions
    private var debounceJob: Job? = null
    private val debounceDelayMs = 100L
    
    // Generation timeout tracking
    private var generationTimeoutJob: Job? = null
    private val generationTimeoutMs = 3000L // 3 seconds timeout

    // Grouping period property with UI state update
    var currentPeriod: GroupingPeriod = GroupingPeriod.MONTHLY
        set(value) {
            field = value
            groupMedia()
        }

    init {
        loadMedia()
        // setupAtlasStreaming() // DISABLED: Using bucket system instead
        setupBucketStreaming()
    }

    /**
     * Setup streaming atlas collection - immediate UI updates as LODs become ready
     */
    private fun setupAtlasStreaming() {
        viewModelScope.launch {
            streamingAtlasManager.getAtlasStream().collectLatest { result ->
                android.util.Log.d("StreamingGalleryVM", "Received atlas stream result: ${result.javaClass.simpleName} for ${result.lodLevel}, sequence=${result.requestSequence}")

                // Only process results newer than or equal to current sequence
                // Allow equal sequences to handle multiple LOD completions from same request
                android.util.Log.d("StreamingGalleryVM", "Checking sequence: result.sequence=${result.requestSequence}, current=$currentRequestSequence")
                if (result.requestSequence >= currentRequestSequence) {
                    android.util.Log.d("StreamingGalleryVM", "Processing result: ${result.javaClass.simpleName} for ${result.lodLevel}")
                    // Update current sequence only for non-LODReady results to avoid blocking parallel LOD completions
                    if (result !is AtlasStreamResult.LODReady) {
                        currentRequestSequence = result.requestSequence
                        android.util.Log.d("StreamingGalleryVM", "Updated currentRequestSequence to $currentRequestSequence")
                    }

                    when (result) {
                        is AtlasStreamResult.Loading -> {
                            updateUiState { 
                                it.copy(
                                    isAtlasGenerating = true,
                                    atlasGenerationStatus = result.message
                                )
                            }
                            // Start generation timeout
                            startGenerationTimeout()
                        }

                        is AtlasStreamResult.Progress -> {
                            updateUiState { 
                                it.copy(
                                    isAtlasGenerating = true,
                                    atlasGenerationStatus = result.message
                                )
                            }
                            // Reset generation timeout
                            startGenerationTimeout()
                        }

                        is AtlasStreamResult.LODReady -> {
                            // Update streaming atlases and persistent cache
                            updateUiState { currentState ->
                                val updatedStreamingAtlases = currentState.streamingAtlases.toMutableMap()
                                updatedStreamingAtlases[result.lodLevel] = result.atlases

                                val updatedPersistentCache = if (result.lodLevel == LODLevel.LEVEL_0) {
                                    result.atlases
                                } else {
                                    currentState.persistentCache
                                }

                                currentState.copy(
                                    streamingAtlases = updatedStreamingAtlases,
                                    persistentCache = updatedPersistentCache,
                                    atlasGenerationStatus = "LOD ${result.lodLevel} ready (${result.generationTimeMs}ms)"
                                )
                            }

                            android.util.Log.d("StreamingGalleryVM", "LOD ${result.lodLevel} ready: ${result.atlases.size} atlases - ${result.reason}")
                            
                            // Stop generating indicator after successful LOD completion
                            stopGeneratingIndicator()
                        }

                        is AtlasStreamResult.LODFailed -> {
                            android.util.Log.w("StreamingGalleryVM", "LOD ${result.lodLevel} failed: ${result.error}")
                            updateUiState { 
                                it.copy(atlasGenerationStatus = "LOD ${result.lodLevel} failed: ${result.error}")
                            }
                            
                            // Stop generating indicator on failure
                            stopGeneratingIndicator()
                        }

                        is AtlasStreamResult.AllComplete -> {
                            updateUiState { 
                                it.copy(
                                    isAtlasGenerating = false,
                                    atlasGenerationStatus = "All LODs complete (${result.totalGenerationTimeMs}ms)"
                                )
                            }
                            android.util.Log.d("StreamingGalleryVM", "All LODs complete: ${result.totalAtlases} total atlases")
                        }

                        is AtlasStreamResult.AtlasRemoved -> {
                            // Remove atlas from UI state to prevent rendering recycled bitmaps
                            updateUiState { currentState ->
                                val updatedStreamingAtlases = currentState.streamingAtlases.toMutableMap()
                                updatedStreamingAtlases.remove(result.lodLevel)

                                currentState.copy(
                                    streamingAtlases = updatedStreamingAtlases,
                                    atlasGenerationStatus = "Cleaned up redundant ${result.lodLevel} atlas - ${result.reason}"
                                )
                            }
                            android.util.Log.d("StreamingGalleryVM", "Atlas removed: ${result.lodLevel} (${result.removedAtlasCount} atlases) - ${result.reason}")
                        }
                    }
                } else {
                    android.util.Log.d("StreamingGalleryVM", "Ignoring stale result: ${result.requestSequence} <= $currentRequestSequence")
                }
            }
        }
    }

    /**
     * Setup bucket atlas streaming - sync UI state with bucket atlas changes
     */
    private fun setupBucketStreaming() {
        viewModelScope.launch {
            streamingAtlasManager.getAtlasBucketManager().atlasFlow.collectLatest { bucketAtlases ->
                android.util.Log.d("StreamingGalleryVM", "Bucket atlas flow update: ${bucketAtlases.size} total atlases")
                
                // Group bucket atlases by LOD level for UI state
                val atlasesByLOD = bucketAtlases.groupBy { it.lodLevel }
                val level0Atlases = atlasesByLOD[LODLevel.LEVEL_0]
                
                updateUiState { currentState ->
                    currentState.copy(
                        streamingAtlases = atlasesByLOD,
                        persistentCache = level0Atlases
                    )
                }
                
                android.util.Log.d("StreamingGalleryVM", "Updated UI state with bucket atlases: ${atlasesByLOD.map { "${it.key.name}(${it.value.size})" }}")
            }
        }
    }

    /**
     * Helper method to update UI state safely
     */
    private fun updateUiState(update: (GalleryUiState) -> GalleryUiState) {
        _uiState.value = update(_uiState.value)
    }

    private fun loadMedia() {
        viewModelScope.launch {
            updateUiState { it.copy(isLoading = true) }
            
            try {
                val media = getMediaUseCase()
                updateUiState { currentState ->
                    val groupedMedia = groupMediaUseCase(media, currentPeriod)
                    currentState.copy(
                        media = media,
                        groupedMedia = groupedMedia,
                        isLoading = false,
                        error = null
                    )
                }

                // Initialize persistent cache with all photos
                initializePersistentCache()
            } catch (e: Exception) {
                android.util.Log.e("StreamingGalleryVM", "Error loading media", e)
                updateUiState { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to load media: ${e.message}"
                    )
                }
            }
        }
    }

    private fun groupMedia() {
        val currentMedia = _uiState.value.media
        if (currentMedia.isNotEmpty()) {
            val groupedMedia = groupMediaUseCase(currentMedia, currentPeriod)
            updateUiState { it.copy(groupedMedia = groupedMedia) }
        }
    }

    /**
     * Initialize persistent cache during app launch
     */
    private fun initializePersistentCache() {
        viewModelScope.launch {
            val allPhotos = _uiState.value.media.filterIsInstance<Media.Image>()
            if (allPhotos.isNotEmpty()) {
                android.util.Log.d("StreamingGalleryVM", "Initializing persistent cache with ${allPhotos.size} photos")
                streamingAtlasManager.initializePersistentCache(allPhotos)
            }
        }
    }

    /**
     * Generates hex grid layout with canvas size and density.
     * Call this when canvas size becomes available.
     */
    fun generateHexGridLayout(density: Density, canvasSize: Size) {
        viewModelScope.launch {
            try {
                val layout = generateHexGridLayoutUseCase.execute(
                    density = density,
                    canvasSize = canvasSize,
                    groupingPeriod = currentPeriod
                )
                updateUiState { it.copy(hexGridLayout = layout) }
            } catch (e: Exception) {
                android.util.Log.e("StreamingGalleryVM", "Error generating hex grid layout", e)
                updateUiState { 
                    it.copy(error = "Failed to generate layout: ${e.message}")
                }
            }
        }
    }

    /**
     * Handle visible cells changed with debouncing for smooth interactions.
     * Updates streaming atlas manager with current context.
     */
    fun onVisibleCellsChanged(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float,
        selectedMedia: Media? = null,
        selectionMode: SelectionMode = SelectionMode.CELL_MODE,
        activeCell: HexCellWithMedia? = null
    ) {
        android.util.Log.d("StreamingGalleryVM", "onVisibleCellsChanged: ${visibleCells.size} cells, zoom=$currentZoom, selectedMedia=${selectedMedia?.displayName}")

        // Check if selection changed (especially deselection)
        val selectionChanged = lastSelectedMedia != selectedMedia
        val wasDeselected = lastSelectedMedia != null && selectedMedia == null
        
        if (selectionChanged) {
            android.util.Log.d("StreamingGalleryVM", "Selection changed: ${lastSelectedMedia?.displayName} -> ${selectedMedia?.displayName}")
            lastSelectedMedia = selectedMedia
        }

        // IMMEDIATE L7 CLEANUP: If photo was deselected, remove L7 atlas immediately
        // This happens BEFORE debouncing to prevent race conditions with job cancellation
        // Uses synchronous cleanup to ensure completion before any new generation requests
        if (wasDeselected) {
            android.util.Log.d("StreamingGalleryVM", "Photo deselected - removing L7 atlas immediately (sync)")
            streamingAtlasManager.cleanupL7AtlasSync()
        }

        // Cancel previous debounce job
        debounceJob?.cancel()

        // Debounce rapid interactions
        debounceJob = viewModelScope.launch {
            delay(debounceDelayMs)

            try {
                streamingAtlasManager.updateVisibleCellsStreaming(
                    visibleCells = visibleCells,
                    currentZoom = currentZoom,
                    selectedMedia = selectedMedia,
                    selectionMode = selectionMode,
                    activeCell = activeCell
                )
            } catch (e: CancellationException) {
                // Expected when debouncing
                throw e
            } catch (e: Exception) {
                android.util.Log.e("StreamingGalleryVM", "Error in streaming atlas update", e)
                updateUiState { 
                    it.copy(atlasGenerationStatus = "Error: ${e.message}")
                }
            }
        }
    }

    // UI State Update Methods for App Composable

    /**
     * Update selected media from UI
     */
    fun updateSelectedMedia(media: Media?) {
        updateUiState { it.copy(selectedMedia = media) }
        
        // Clear highlight bucket when media is deselected
        if (media == null) {
            viewModelScope.launch {
                streamingAtlasManager.updateSelectedMedia(null, 1.0f, SelectionMode.CELL_MODE)
            }
        }
    }

    /**
     * Update selection mode from UI
     */
    fun updateSelectionMode(mode: SelectionMode) {
        updateUiState { it.copy(selectionMode = mode) }
    }

    /**
     * Update focused cell from UI
     */
    fun updateFocusedCell(cellWithMedia: HexCellWithMedia?) {
        updateUiState { it.copy(focusedCellWithMedia = cellWithMedia) }
    }

    /**
     * Update significant cells from UI
     */
    fun updateSignificantCells(cells: Set<dev.serhiiyaremych.lumina.domain.model.HexCell>) {
        updateUiState { it.copy(significantCells = cells) }
    }

    /**
     * Update permission granted status
     */
    fun updatePermissionGranted(granted: Boolean) {
        updateUiState { it.copy(permissionGranted = granted) }
    }

    /**
     * Get streaming atlas manager for direct UI access
     */
    fun getStreamingAtlasManager(): StreamingAtlasManager = streamingAtlasManager

    /**
     * Get current atlases for specific LOD level
     */
    suspend fun getCurrentAtlases(lodLevel: LODLevel): List<TextureAtlas> {
        return streamingAtlasManager.getCurrentAtlases(lodLevel)
    }

    /**
     * Get best available atlas region for photo
     */
    suspend fun getBestPhotoRegion(photoUri: android.net.Uri) = streamingAtlasManager.getBestPhotoRegion(photoUri)

    /**
     * Get persistent cache atlases (immediate fallback rendering)
     */
    suspend fun getPersistentCache(): List<TextureAtlas>? = streamingAtlasManager.getPersistentCache()

    /**
     * Manual refresh of persistent cache (for debugging)
     */
    fun refreshPersistentCache() {
        initializePersistentCache()
    }
    
    /**
     * Start generation timeout to automatically stop generating indicator
     */
    private fun startGenerationTimeout() {
        generationTimeoutJob?.cancel()
        generationTimeoutJob = viewModelScope.launch {
            delay(generationTimeoutMs)
            android.util.Log.d("StreamingGalleryVM", "Generation timeout reached - stopping generating indicator")
            updateUiState { 
                it.copy(
                    isAtlasGenerating = false,
                    atlasGenerationStatus = "Generation completed"
                )
            }
        }
    }
    
    /**
     * Stop generating indicator with delay to avoid flickering
     */
    private fun stopGeneratingIndicator() {
        generationTimeoutJob?.cancel()
        viewModelScope.launch {
            delay(500) // Small delay to avoid flickering if multiple LODs complete quickly
            updateUiState { it.copy(isAtlasGenerating = false) }
            android.util.Log.d("StreamingGalleryVM", "Stopped generating indicator after LOD completion")
        }
    }
}
