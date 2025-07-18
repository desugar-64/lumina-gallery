package dev.serhiiyaremych.lumina.ui.selection

import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.MediaWithPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages photo selection state and handles state transitions.
 * Provides reactive state updates for UI components.
 */
@Singleton
class SelectionStateManager @Inject constructor() {
    
    private val _state = MutableStateFlow(PhotoSelectionState())
    val state: StateFlow<PhotoSelectionState> = _state.asStateFlow()
    
    private val _config = MutableStateFlow(PhotoSelectionConfig())
    val config: StateFlow<PhotoSelectionConfig> = _config.asStateFlow()
    
    /**
     * Handles photo selection events and updates state accordingly.
     */
    fun handleEvent(event: PhotoSelectionEvent) {
        when (event) {
            is PhotoSelectionEvent.PhotoSelected -> handlePhotoSelected(event)
            is PhotoSelectionEvent.PhotoDeselected -> handlePhotoDeselected()
            is PhotoSelectionEvent.NavigateNext -> handleNavigateNext()
            is PhotoSelectionEvent.NavigatePrevious -> handleNavigatePrevious()
            is PhotoSelectionEvent.NavigateToIndex -> handleNavigateToIndex(event.index)
            is PhotoSelectionEvent.OverlayVisibilityChanged -> handleOverlayVisibilityChanged(event.visible)
            is PhotoSelectionEvent.ViewportOccupationChanged -> handleViewportOccupationChanged(event.occupation)
            is PhotoSelectionEvent.SelectionModeChanged -> handleSelectionModeChanged(event.mode)
        }
    }
    
    /**
     * Selects a photo and updates related state.
     */
    private fun handlePhotoSelected(event: PhotoSelectionEvent.PhotoSelected) {
        _state.value = _state.value.copy(
            selectedMedia = event.media,
            selectedMediaWithPosition = event.mediaWithPosition,
            currentCell = event.cell,
            cellNavigationIndex = event.cellIndex,
            selectionMode = event.mode,
            overlayVisible = true
        )
    }
    
    /**
     * Clears photo selection.
     */
    private fun handlePhotoDeselected() {
        _state.value = _state.value.copy(
            selectedMedia = null,
            selectedMediaWithPosition = null,
            currentCell = null,
            cellNavigationIndex = 0,
            selectionMode = SelectionMode.NONE,
            overlayVisible = false
        )
    }
    
    /**
     * Navigates to the next photo in the current cell.
     */
    private fun handleNavigateNext() {
        val currentState = _state.value
        val cell = currentState.currentCell ?: return
        
        if (currentState.canNavigateNext) {
            val nextIndex = currentState.cellNavigationIndex + 1
            val nextMediaWithPosition = cell.mediaItems[nextIndex]
            
            _state.value = currentState.copy(
                selectedMedia = nextMediaWithPosition.media,
                selectedMediaWithPosition = nextMediaWithPosition,
                cellNavigationIndex = nextIndex
            )
        }
    }
    
    /**
     * Navigates to the previous photo in the current cell.
     */
    private fun handleNavigatePrevious() {
        val currentState = _state.value
        val cell = currentState.currentCell ?: return
        
        if (currentState.canNavigatePrevious) {
            val prevIndex = currentState.cellNavigationIndex - 1
            val prevMediaWithPosition = cell.mediaItems[prevIndex]
            
            _state.value = currentState.copy(
                selectedMedia = prevMediaWithPosition.media,
                selectedMediaWithPosition = prevMediaWithPosition,
                cellNavigationIndex = prevIndex
            )
        }
    }
    
    /**
     * Navigates to a specific photo index in the current cell.
     */
    private fun handleNavigateToIndex(index: Int) {
        val currentState = _state.value
        val cell = currentState.currentCell ?: return
        
        if (index >= 0 && index < cell.mediaItems.size) {
            val mediaWithPosition = cell.mediaItems[index]
            
            _state.value = currentState.copy(
                selectedMedia = mediaWithPosition.media,
                selectedMediaWithPosition = mediaWithPosition,
                cellNavigationIndex = index
            )
        }
    }
    
    /**
     * Updates overlay visibility.
     */
    private fun handleOverlayVisibilityChanged(visible: Boolean) {
        _state.value = _state.value.copy(overlayVisible = visible)
    }
    
    /**
     * Updates viewport occupation and handles auto mode logic.
     */
    private fun handleViewportOccupationChanged(occupation: Float) {
        val currentState = _state.value
        val currentConfig = _config.value
        
        val newState = currentState.copy(viewportOccupation = occupation)
        
        // Handle auto mode logic
        if (currentConfig.autoModeEnabled && currentState.selectionMode == SelectionMode.AUTO_VIEWPORT) {
            val shouldShow = currentConfig.shouldAutoShow(occupation)
            val shouldHide = currentConfig.shouldAutoHide(occupation)
            
            when {
                shouldShow && !currentState.overlayVisible -> {
                    _state.value = newState.copy(overlayVisible = true)
                }
                shouldHide && currentState.overlayVisible -> {
                    _state.value = newState.copy(overlayVisible = false)
                }
                else -> {
                    _state.value = newState
                }
            }
        } else {
            _state.value = newState
        }
    }
    
    /**
     * Updates selection mode.
     */
    private fun handleSelectionModeChanged(mode: SelectionMode) {
        _state.value = _state.value.copy(selectionMode = mode)
    }
    
    /**
     * Updates configuration.
     */
    fun updateConfig(config: PhotoSelectionConfig) {
        _config.value = config
    }
    
    /**
     * Convenience method to select a photo by finding it in the grid.
     */
    fun selectPhoto(
        media: Media,
        cells: List<HexCellWithMedia>,
        mode: SelectionMode = SelectionMode.MANUAL_SELECTION
    ) {
        // Find the cell and index containing this media
        cells.forEach { cell ->
            cell.mediaItems.forEachIndexed { index, mediaWithPosition ->
                if (mediaWithPosition.media.id == media.id) {
                    handleEvent(
                        PhotoSelectionEvent.PhotoSelected(
                            media = media,
                            mediaWithPosition = mediaWithPosition,
                            cell = cell,
                            cellIndex = index,
                            mode = mode
                        )
                    )
                    return
                }
            }
        }
    }
    
    /**
     * Convenience method to check if a media item is currently selected.
     */
    fun isSelected(media: Media): Boolean {
        return _state.value.selectedMedia?.id == media.id
    }
    
    /**
     * Gets the current selection state.
     */
    fun getCurrentState(): PhotoSelectionState = _state.value
    
    /**
     * Gets the current configuration.
     */
    fun getCurrentConfig(): PhotoSelectionConfig = _config.value
}