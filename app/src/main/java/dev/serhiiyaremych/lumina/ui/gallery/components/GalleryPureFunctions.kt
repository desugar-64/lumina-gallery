package dev.serhiiyaremych.lumina.ui.gallery.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.ui.SelectionMode
import dev.serhiiyaremych.lumina.ui.SimpleViewportConfig
import dev.serhiiyaremych.lumina.ui.SimpleViewportState
import dev.serhiiyaremych.lumina.ui.calculateSimpleViewportRect
import dev.serhiiyaremych.lumina.ui.determineSuggestedSelectionMode
import dev.serhiiyaremych.lumina.ui.gallery.GalleryUiState
import dev.serhiiyaremych.lumina.ui.shouldDeselectMedia

/**
 * Data class representing actions that need to be performed when a cell is focused
 */
@Immutable
data class CellFocusActions(
    val updateSelectedCell: dev.serhiiyaremych.lumina.domain.model.HexCell,
    val clearSelectedMedia: Boolean,
    val updateSelectionMode: SelectionMode,
    val updateFocusedCell: HexCellWithMedia,
    val triggerFocusAnimation: Rect?,
    val focusPadding: androidx.compose.ui.unit.Dp
)

/**
 * Data class representing actions that need to be performed for viewport changes
 */
@Immutable
data class ViewportActions(
    val suggestedSelectionMode: SelectionMode?,
    val shouldDeselectMedia: Boolean
)

/**
 * Pure function to calculate cell focus actions based on current state
 */
fun calculateCellFocusActions(
    hexCellWithMedia: HexCellWithMedia,
    currentSelectedMedia: dev.serhiiyaremych.lumina.domain.model.Media?,
    currentSelectionMode: SelectionMode
): CellFocusActions = CellFocusActions(
    updateSelectedCell = hexCellWithMedia.hexCell,
    clearSelectedMedia = currentSelectedMedia != null,
    updateSelectionMode = if (currentSelectionMode != SelectionMode.CELL_MODE) SelectionMode.CELL_MODE else currentSelectionMode,
    updateFocusedCell = hexCellWithMedia,
    triggerFocusAnimation = calculateCellFocusBounds(hexCellWithMedia.hexCell),
    focusPadding = 64.dp // Cell focus uses larger padding for comfortable cell viewing
)

/**
 * Pure function to calculate viewport state
 */
fun calculateViewportState(
    uiState: GalleryUiState,
    canvasSize: Size,
    zoom: Float,
    offset: Offset
): SimpleViewportState? = uiState.hexGridLayout?.let { layout ->
    SimpleViewportState(
        viewportRect = calculateSimpleViewportRect(canvasSize, zoom, offset),
        canvasSize = canvasSize,
        zoom = zoom,
        offset = offset,
        focusedCell = uiState.selectedCellWithMedia,
        selectedMedia = uiState.selectedMedia,
        selectionMode = uiState.selectionMode,
        gridBounds = layout.bounds
    )
}

/**
 * Pure function to calculate viewport actions
 */
fun calculateViewportActions(
    viewportState: SimpleViewportState?,
    uiState: GalleryUiState,
    viewportConfig: SimpleViewportConfig
): ViewportActions = if (viewportState != null) {
    val suggestedMode = determineSuggestedSelectionMode(
        cell = viewportState.focusedCell,
        canvasSize = viewportState.canvasSize,
        zoom = viewportState.zoom,
        offset = viewportState.offset,
        currentMode = uiState.selectionMode,
        hasSelectedMedia = viewportState.selectedMedia != null,
        config = viewportConfig
    )

    val shouldDeselect = shouldDeselectMedia(
        cell = viewportState.focusedCell,
        selectedMedia = viewportState.selectedMedia,
        viewportRect = viewportState.viewportRect,
        config = viewportConfig
    )

    ViewportActions(
        suggestedSelectionMode = if (suggestedMode != uiState.selectionMode) suggestedMode else null,
        shouldDeselectMedia = shouldDeselect && uiState.selectedMedia != null
    )
} else {
    ViewportActions(
        suggestedSelectionMode = null,
        shouldDeselectMedia = false
    )
}

/**
 * Pure function to calculate cell focus bounds
 */
fun calculateCellFocusBounds(hexCell: dev.serhiiyaremych.lumina.domain.model.HexCell): Rect? {
    val vertices = hexCell.vertices
    if (vertices.isEmpty()) return null

    val minX = vertices.minOf { it.x }
    val maxX = vertices.maxOf { it.x }
    val minY = vertices.minOf { it.y }
    val maxY = vertices.maxOf { it.y }
    return Rect(minX, minY, maxX, maxY)
}
