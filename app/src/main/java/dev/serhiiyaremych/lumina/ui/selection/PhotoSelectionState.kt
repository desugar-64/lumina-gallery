package dev.serhiiyaremych.lumina.ui.selection

import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.MediaWithPosition

/**
 * Represents the state of photo selection overlay system.
 * Tracks current selection, overlay visibility, and navigation context.
 */
data class PhotoSelectionState(
    val selectedMedia: Media? = null,
    val selectedMediaWithPosition: MediaWithPosition? = null,
    val selectionMode: SelectionMode = SelectionMode.NONE,
    val overlayVisible: Boolean = false,
    val currentCell: HexCellWithMedia? = null,
    val cellNavigationIndex: Int = 0,
    val isAutoMode: Boolean = true,
    val viewportOccupation: Float = 0f
) {
    /**
     * Returns the total number of photos in the current cell.
     */
    val totalPhotosInCell: Int
        get() = currentCell?.mediaItems?.size ?: 0
    
    /**
     * Returns true if there are multiple photos in the current cell.
     */
    val hasMultiplePhotosInCell: Boolean
        get() = totalPhotosInCell > 1
    
    /**
     * Returns the current photo's position in the cell (1-based).
     */
    val currentPhotoPosition: Int
        get() = if (currentCell != null) cellNavigationIndex + 1 else 0
    
    /**
     * Returns true if overlay should be shown based on current state.
     */
    val shouldShowOverlay: Boolean
        get() = overlayVisible && selectedMedia != null && selectionMode != SelectionMode.NONE
    
    /**
     * Returns true if navigation controls should be shown.
     */
    val shouldShowNavigation: Boolean
        get() = hasMultiplePhotosInCell && currentCell != null
    
    /**
     * Returns the media items in the current cell for navigation.
     */
    val cellMediaItems: List<MediaWithPosition>
        get() = currentCell?.mediaItems ?: emptyList()
    
    /**
     * Returns true if we can navigate to the previous photo.
     */
    val canNavigatePrevious: Boolean
        get() = hasMultiplePhotosInCell && cellNavigationIndex > 0
    
    /**
     * Returns true if we can navigate to the next photo.
     */
    val canNavigateNext: Boolean
        get() = hasMultiplePhotosInCell && cellNavigationIndex < totalPhotosInCell - 1
}

/**
 * Defines the selection mode for photo overlay.
 */
enum class SelectionMode {
    /**
     * No selection active.
     */
    NONE,
    
    /**
     * Automatic selection based on viewport occupation.
     */
    AUTO_VIEWPORT,
    
    /**
     * Manual selection triggered by user tap.
     */
    MANUAL_SELECTION,
    
    /**
     * Photo is focused and overlay is showing.
     */
    OVERLAY_ACTIVE
}

/**
 * Events for photo selection state changes.
 */
sealed class PhotoSelectionEvent {
    /**
     * A photo was selected (either by tap or viewport focus).
     */
    data class PhotoSelected(
        val media: Media,
        val mediaWithPosition: MediaWithPosition,
        val cell: HexCellWithMedia,
        val cellIndex: Int,
        val mode: SelectionMode
    ) : PhotoSelectionEvent()
    
    /**
     * Photo selection was cleared.
     */
    object PhotoDeselected : PhotoSelectionEvent()
    
    /**
     * Navigate to next photo in current cell.
     */
    object NavigateNext : PhotoSelectionEvent()
    
    /**
     * Navigate to previous photo in current cell.
     */
    object NavigatePrevious : PhotoSelectionEvent()
    
    /**
     * Navigate to specific photo index in current cell.
     */
    data class NavigateToIndex(val index: Int) : PhotoSelectionEvent()
    
    /**
     * Overlay visibility changed.
     */
    data class OverlayVisibilityChanged(val visible: Boolean) : PhotoSelectionEvent()
    
    /**
     * Viewport occupation changed (for auto mode).
     */
    data class ViewportOccupationChanged(val occupation: Float) : PhotoSelectionEvent()
    
    /**
     * Selection mode changed.
     */
    data class SelectionModeChanged(val mode: SelectionMode) : PhotoSelectionEvent()
}

/**
 * Configuration for photo selection behavior.
 */
data class PhotoSelectionConfig(
    val autoModeEnabled: Boolean = true,
    val autoShowThreshold: Float = 0.7f,    // Show overlay when photo occupies >70% of viewport
    val autoHideThreshold: Float = 0.3f,    // Hide overlay when photo occupies <30% of viewport
    val enableCellNavigation: Boolean = true,
    val enableSwipeGestures: Boolean = true,
    val overlayAnimationDuration: Long = 300L,
    val metadataLoadingEnabled: Boolean = true
) {
    /**
     * Returns true if viewport occupation should trigger auto show.
     */
    fun shouldAutoShow(occupation: Float): Boolean {
        return autoModeEnabled && occupation >= autoShowThreshold
    }
    
    /**
     * Returns true if viewport occupation should trigger auto hide.
     */
    fun shouldAutoHide(occupation: Float): Boolean {
        return autoModeEnabled && occupation <= autoHideThreshold
    }
}