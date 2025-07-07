package dev.serhiiyaremych.lumina.ui.gallery

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.HexGridLayout
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.AtlasManager
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import dev.serhiiyaremych.lumina.domain.usecase.GenerateHexGridLayoutUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GetMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GroupMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GroupingPeriod
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val groupMediaUseCase: GroupMediaUseCase,
    private val generateHexGridLayoutUseCase: GenerateHexGridLayoutUseCase,
    private val atlasManager: AtlasManager
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

    private val _atlasState = MutableStateFlow<MultiAtlasUpdateResult?>(null)
    val atlasState: StateFlow<MultiAtlasUpdateResult?> = _atlasState.asStateFlow()

    // Atlas generation state for benchmarking
    private val _isAtlasGenerating = MutableStateFlow(false)
    val isAtlasGenerating: StateFlow<Boolean> = _isAtlasGenerating.asStateFlow()

    private val _memoryStatus = MutableStateFlow<dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager.MemoryStatus?>(null)
    val memoryStatus: StateFlow<dev.serhiiyaremych.lumina.domain.usecase.SmartMemoryManager.MemoryStatus?> = _memoryStatus.asStateFlow()

    // Job tracker for atlas generation cancellation
    private var atlasGenerationJob: Job? = null

    // Track current request sequence to prevent stale results
    private var currentRequestSequence: Long = 0

    private val updateAtlasFlow = MutableStateFlow<Pair<List<HexCellWithMedia>, Float>>(Pair(emptyList(), 1.0f))

    init {
        loadMedia()
        
        // Initialize memory status
        updateMemoryStatus()

        // Periodic memory status updates for debug overlay responsiveness
        viewModelScope.launch {
            while (true) {
                delay(500) // Update every 500ms when debug panel might be visible
                updateMemoryStatus()
            }
        }

        viewModelScope.launch {
            updateAtlasFlow
                .filter { it.first.isNotEmpty() }
                .distinctUntilChanged()
                .collectLatest { (visibleCells, currentZoom) ->
                    _isAtlasGenerating.value = true
                    
                    runCatching {
                        atlasManager.updateVisibleCells(
                            visibleCells = visibleCells,
                            currentZoom = currentZoom
                        )
                    }.fold(
                        onSuccess = { result ->
                            android.util.Log.d(
                                "GalleryViewModel",
                                "Atlas result received: ${result::class.simpleName}, sequence=${result.requestSequence}, current=$currentRequestSequence"
                            )

                            // Only update UI state if this result is not stale
                            if (result.requestSequence > currentRequestSequence) {
                                android.util.Log.d(
                                    "GalleryViewModel",
                                    "Updating atlas state with sequence ${result.requestSequence}"
                                )
                                currentRequestSequence = result.requestSequence
                                _atlasState.value = result
                                
                                // Update memory status immediately after atlas state change
                                updateMemoryStatus()
                            } else {
                                android.util.Log.d(
                                    "GalleryViewModel",
                                    "Ignoring stale atlas result: ${result.requestSequence} <= $currentRequestSequence"
                                )
                            }
                        },
                        onFailure = { e ->
                            android.util.Log.e("GalleryViewModel", "Atlas generation failed", e)
                            if (e is CancellationException) throw e
                            
                            // Update memory status even on failure to reflect current state
                            updateMemoryStatus()
                        }
                    )
                    
                    _isAtlasGenerating.value = false
                    
                    // Final memory status update after generation completes
                    updateMemoryStatus()
                }
        }
    }

    private fun loadMedia() {
        viewModelScope.launch {
            val media = getMediaUseCase()
            _mediaState.value = media
            groupMedia()
        }
    }

    private fun groupMedia() {
        _groupedMediaState.value = groupMediaUseCase(_mediaState.value, currentPeriod)
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
     * Handle visible cells changed from UI callback.
     * Updates atlas manager with current visible cells and viewport dimensions.
     * Cancels any previous atlas generation to prevent race conditions.
     */
    fun onVisibleCellsChanged(
        visibleCells: List<HexCellWithMedia>,
        currentZoom: Float
    ) {
        android.util.Log.d("GalleryViewModel", "onVisibleCellsChanged: ${visibleCells.size} cells, zoom=$currentZoom")
        updateAtlasFlow.value = Pair(visibleCells, currentZoom)
        
        // Update memory status for debug panel
        updateMemoryStatus()
    }

    /**
     * Update memory status from atlas manager.
     */
    private fun updateMemoryStatus() {
        _memoryStatus.value = atlasManager.getMemoryStatus()
    }

    /**
     * Manually refresh memory status (for debug purposes).
     */
    fun refreshMemoryStatus() {
        updateMemoryStatus()
    }

    /**
     * Get current atlas manager for UI rendering.
     */
    fun getAtlasManager(): AtlasManager = atlasManager
}
