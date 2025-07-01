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
import dev.serhiiyaremych.lumina.domain.usecase.AtlasUpdateResult
import dev.serhiiyaremych.lumina.domain.usecase.GenerateHexGridLayoutUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GetMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GroupMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GroupingPeriod
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val _atlasState = MutableStateFlow<AtlasUpdateResult?>(null)
    val atlasState: StateFlow<AtlasUpdateResult?> = _atlasState.asStateFlow()

    // Atlas generation state for benchmarking
    private val _isAtlasGenerating = MutableStateFlow(false)
    val isAtlasGenerating: StateFlow<Boolean> = _isAtlasGenerating.asStateFlow()

    init {
        loadMedia()
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
     * Updates atlas manager with current visible cells.
     */
    fun onVisibleCellsChanged(visibleCells: List<HexCellWithMedia>, currentZoom: Float) {
        viewModelScope.launch {
            _isAtlasGenerating.value = true // Mark as generating
            try {
                val result = atlasManager.updateVisibleCells(visibleCells, currentZoom)
                _atlasState.value = result
            } finally {
                _isAtlasGenerating.value = false // Mark as complete
            }
        }
    }

    /**
     * Get current atlas manager for UI rendering.
     */
    fun getAtlasManager(): AtlasManager = atlasManager
}
