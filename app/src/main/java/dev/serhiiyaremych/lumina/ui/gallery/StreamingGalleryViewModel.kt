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
 * Streaming Gallery ViewModel - Responsive Atlas System Integration
 *
 * Enhanced ViewModel that integrates with the new streaming atlas system for
 * immediate UI updates and progressive enhancement. Handles multiple LOD levels
 * independently and provides zero-wait user experience.
 *
 * Key Features:
 * - Streaming atlas results with immediate UI updates
 * - Progressive LOD enhancement (LEVEL_0 → LEVEL_2 → LEVEL_4 → LEVEL_7)
 * - Persistent cache management for instant fallback rendering
 * - Debounced user interactions to prevent atlas generation spam
 * - Memory-efficient atlas state management
 */
@HiltViewModel
class StreamingGalleryViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val groupMediaUseCase: GroupMediaUseCase,
    private val generateHexGridLayoutUseCase: GenerateHexGridLayoutUseCase,
    private val streamingAtlasManager: StreamingAtlasManager
) : ViewModel() {

    var currentPeriod: GroupingPeriod = GroupingPeriod.MONTHLY
        set(value) {
            field = value
            groupMedia()
        }

    private val _mediaState = MutableStateFlow<List<Media>>(emptyList())
    val mediaState: StateFlow<List<Media>> = _mediaState.asStateFlow()

    private val _groupedMediaState = MutableStateFlow<Map<LocalDate, List<Media>>>(emptyMap())
    val groupedMediaState: StateFlow<Map<LocalDate, List<Media>>> = _groupedMediaState.asStateFlow()

    private val _hexGridLayoutState = MutableStateFlow<HexGridLayout?>(null)
    val hexGridLayoutState: StateFlow<HexGridLayout?> = _hexGridLayoutState.asStateFlow()

    // Streaming atlas state - separate state for each LOD level
    private val _atlasStates = mutableMapOf<LODLevel, MutableStateFlow<List<TextureAtlas>>>()
    
    // Current best atlases (highest LOD available for each photo)
    private val _currentBestAtlases = MutableStateFlow<Map<LODLevel, List<TextureAtlas>>>(emptyMap())
    val currentBestAtlases: StateFlow<Map<LODLevel, List<TextureAtlas>>> = _currentBestAtlases.asStateFlow()

    // Persistent cache state (LEVEL_0 with ALL photos)
    private val _persistentCacheState = MutableStateFlow<List<TextureAtlas>?>(null)
    val persistentCacheState: StateFlow<List<TextureAtlas>?> = _persistentCacheState.asStateFlow()

    // Atlas generation progress and status
    private val _isAtlasGenerating = MutableStateFlow(false)
    val isAtlasGenerating: StateFlow<Boolean> = _isAtlasGenerating.asStateFlow()

    private val _atlasGenerationStatus = MutableStateFlow<String?>(null)
    val atlasGenerationStatus: StateFlow<String?> = _atlasGenerationStatus.asStateFlow()

    // Current request tracking for UI updates
    private var currentRequestSequence: Long = 0

    // Debouncing for rapid user interactions
    private var debounceJob: Job? = null
    private val debounceDelayMs = 100L

    init {
        loadMedia()
        setupAtlasStreaming()
    }

    /**
     * Setup streaming atlas collection - immediate UI updates as LODs become ready
     */
    private fun setupAtlasStreaming() {
        viewModelScope.launch {
            streamingAtlasManager.getAtlasStream().collectLatest { result ->
                android.util.Log.d("StreamingGalleryVM", "Received atlas stream result: ${result.javaClass.simpleName} for ${result.lodLevel}, sequence=${result.requestSequence}")

                // Only process results newer than current sequence
                if (result.requestSequence > currentRequestSequence) {
                    currentRequestSequence = result.requestSequence

                    when (result) {
                        is AtlasStreamResult.Loading -> {
                            _isAtlasGenerating.value = true
                            _atlasGenerationStatus.value = result.message
                        }

                        is AtlasStreamResult.Progress -> {
                            _isAtlasGenerating.value = true
                            _atlasGenerationStatus.value = result.message
                        }

                        is AtlasStreamResult.LODReady -> {
                            // Update specific LOD level state
                            updateLODState(result.lodLevel, result.atlases)
                            
                            // Update persistent cache if this is LEVEL_0
                            if (result.lodLevel == LODLevel.LEVEL_0) {
                                _persistentCacheState.value = result.atlases
                                android.util.Log.d("StreamingGalleryVM", "Updated persistent cache: ${result.atlases.size} atlases")
                            }

                            // Update overall best atlases map
                            updateBestAtlases()
                            
                            // Update status
                            _atlasGenerationStatus.value = "LOD ${result.lodLevel} ready (${result.generationTimeMs}ms)"
                            
                            android.util.Log.d("StreamingGalleryVM", "LOD ${result.lodLevel} ready: ${result.atlases.size} atlases - ${result.reason}")
                        }

                        is AtlasStreamResult.LODFailed -> {
                            android.util.Log.w("StreamingGalleryVM", "LOD ${result.lodLevel} failed: ${result.error}")
                            _atlasGenerationStatus.value = "LOD ${result.lodLevel} failed: ${result.error}"
                        }

                        is AtlasStreamResult.AllComplete -> {
                            _isAtlasGenerating.value = false
                            _atlasGenerationStatus.value = "All LODs complete (${result.totalGenerationTimeMs}ms)"
                            android.util.Log.d("StreamingGalleryVM", "All LODs complete: ${result.totalAtlases} total atlases")
                        }
                    }
                } else {
                    android.util.Log.d("StreamingGalleryVM", "Ignoring stale result: ${result.requestSequence} <= $currentRequestSequence")
                }
            }
        }
    }

    /**
     * Update specific LOD level state
     */
    private fun updateLODState(lodLevel: LODLevel, atlases: List<TextureAtlas>) {
        // Get or create state flow for this LOD level
        val lodStateFlow = _atlasStates.getOrPut(lodLevel) {
            MutableStateFlow(emptyList())
        }
        
        // Update the state
        lodStateFlow.value = atlases
    }

    /**
     * Update best available atlases map (for UI rendering)
     */
    private suspend fun updateBestAtlases() {
        val allAtlases = streamingAtlasManager.getAllCurrentAtlases()
        _currentBestAtlases.value = allAtlases
    }

    /**
     * Get state flow for specific LOD level
     */
    fun getLODStateFlow(lodLevel: LODLevel): StateFlow<List<TextureAtlas>> {
        return _atlasStates.getOrPut(lodLevel) {
            MutableStateFlow(emptyList())
        }.asStateFlow()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            val media = getMediaUseCase()
            _mediaState.value = media
            groupMedia()
            
            // Initialize persistent cache with all photos
            initializePersistentCache()
        }
    }

    private fun groupMedia() {
        _groupedMediaState.value = groupMediaUseCase(_mediaState.value, currentPeriod)
    }

    /**
     * Initialize persistent cache during app launch
     */
    private fun initializePersistentCache() {
        viewModelScope.launch {
            val allPhotos = _mediaState.value.filterIsInstance<Media.Image>()
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
            val layout = generateHexGridLayoutUseCase.execute(
                density = density,
                canvasSize = canvasSize,
                groupingPeriod = currentPeriod
            )
            _hexGridLayoutState.value = layout
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
        android.util.Log.d("StreamingGalleryVM", "onVisibleCellsChanged: ${visibleCells.size} cells, zoom=$currentZoom")

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
                _atlasGenerationStatus.value = "Error: ${e.message}"
            }
        }
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
}