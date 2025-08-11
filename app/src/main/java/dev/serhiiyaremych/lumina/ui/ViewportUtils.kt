package dev.serhiiyaremych.lumina.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media

/**
 * Configuration for viewport-based decisions
 */
@Stable
data class SimpleViewportConfig(
    val cellSignificanceThreshold: Float = 0.35f, // 35% viewport coverage for cell focus (higher than unfocus threshold)
    val cellUnfocusThreshold: Float = 0.1f, // 10% threshold - unfocus when cell is mostly out of viewport
    val modeTransitionThreshold: Float = 1.2f, // 120% viewport size for photo mode
    val minViewportCoverage: Float = 0.4f, // 40% minimum coverage to stay selected
    val gestureDelayMs: Long = 200L, // Debounce gesture updates
    // 15% coverage to show panel
    val panelVisibilityThreshold: Float = 0.15f
)

/**
 * Calculate viewport rectangle in content coordinates
 */
fun calculateSimpleViewportRect(canvasSize: Size, zoom: Float, offset: Offset): Rect {
    // Transform screen viewport to content coordinates
    // The screen viewport (0,0) to (canvasSize.width, canvasSize.height) in screen space
    // needs to be transformed to content coordinates by:
    // 1. Subtracting the offset (to reverse the translation)
    // 2. Dividing by zoom (to reverse the scaling)
    val contentLeft = (0f - offset.x) / zoom
    val contentTop = (0f - offset.y) / zoom
    val contentRight = (canvasSize.width - offset.x) / zoom
    val contentBottom = (canvasSize.height - offset.y) / zoom

    return Rect(
        left = contentLeft,
        top = contentTop,
        right = contentRight,
        bottom = contentBottom
    )
}

/**
 * Calculate cell coverage as percentage of viewport occupied by cell
 */
fun calculateCellCoverage(cell: HexCellWithMedia, viewportRect: Rect): Float {
    // Calculate cell bounds in content coordinates
    val cellVertices = cell.hexCell.vertices
    val cellMinX = cellVertices.minOf { it.x }
    val cellMaxX = cellVertices.maxOf { it.x }
    val cellMinY = cellVertices.minOf { it.y }
    val cellMaxY = cellVertices.maxOf { it.y }
    val contentCellBounds = Rect(cellMinX, cellMinY, cellMaxX, cellMaxY)

    // Calculate viewport coverage (what % of viewport does this cell occupy)
    val intersection = viewportRect.intersect(contentCellBounds)
    return if (intersection.isEmpty) {
        0f
    } else {
        (intersection.width * intersection.height) / (viewportRect.width * viewportRect.height)
    }
}

/**
 * Check if cell is larger than viewport (for mode switching)
 */
fun isCellLargerThanViewport(
    cell: HexCellWithMedia,
    canvasSize: Size,
    zoom: Float,
    offset: Offset,
    modeTransitionThreshold: Float
): Boolean {
    // Calculate cell bounds in content coordinates
    val cellVertices = cell.hexCell.vertices
    val cellMinX = cellVertices.minOf { it.x }
    val cellMaxX = cellVertices.maxOf { it.x }
    val cellMinY = cellVertices.minOf { it.y }
    val cellMaxY = cellVertices.maxOf { it.y }

    // Transform to screen coordinates for size comparison
    val screenMinX = (cellMinX * zoom) + offset.x
    val screenMaxX = (cellMaxX * zoom) + offset.x
    val screenMinY = (cellMinY * zoom) + offset.y
    val screenMaxY = (cellMaxY * zoom) + offset.y

    // Check if cell is larger than viewport (for mode switching)
    val screenCellWidth = screenMaxX - screenMinX
    val screenCellHeight = screenMaxY - screenMinY
    return screenCellWidth > (canvasSize.width * modeTransitionThreshold) ||
        screenCellHeight > (canvasSize.height * modeTransitionThreshold)
}

/**
 * Determine suggested selection mode based on viewport conditions
 */
fun determineSuggestedSelectionMode(
    cell: HexCellWithMedia?,
    canvasSize: Size,
    zoom: Float,
    offset: Offset,
    currentMode: SelectionMode,
    hasSelectedMedia: Boolean,
    config: SimpleViewportConfig
): SelectionMode {
    // No change if no cell context
    if (cell == null || !hasSelectedMedia) {
        return SelectionMode.CELL_MODE
    }

    val cellLargerThanViewport = isCellLargerThanViewport(cell, canvasSize, zoom, offset, config.modeTransitionThreshold)

    // Switch to PHOTO_MODE if cell is significantly larger than viewport
    if (cellLargerThanViewport && currentMode == SelectionMode.CELL_MODE) {
        return SelectionMode.PHOTO_MODE
    }

    // Switch back to CELL_MODE if cell is no longer larger than viewport
    if (!cellLargerThanViewport && currentMode == SelectionMode.PHOTO_MODE) {
        return SelectionMode.CELL_MODE
    }

    return currentMode
}

/**
 * Determine if media should be deselected due to viewport constraints
 */
fun shouldDeselectMedia(
    cell: HexCellWithMedia?,
    selectedMedia: Media?,
    viewportRect: Rect,
    config: SimpleViewportConfig
): Boolean {
    if (selectedMedia == null) return false

    // Deselect if cell is out of viewport (use configured threshold for deselection)
    if (cell != null) {
        val coverage = calculateCellCoverage(cell, viewportRect)
        if (coverage <= config.minViewportCoverage) {
            return true
        }
    }

    return false
}

/**
 * Determine if focused cell should be unfocused due to viewport constraints
 */
fun shouldUnfocusCell(
    cell: HexCellWithMedia?,
    viewportRect: Rect,
    config: SimpleViewportConfig
): Boolean {
    if (cell == null) return false

    // Unfocus if cell coverage is too low
    val coverage = calculateCellCoverage(cell, viewportRect)
    return coverage <= config.cellUnfocusThreshold
}

/**
 * Determine if panel should be shown based on cell coverage
 */
fun shouldShowPanel(coverage: Float, config: SimpleViewportConfig): Boolean = coverage >= config.panelVisibilityThreshold

/**
 * Simplified viewport state that contains only the essential information
 * Derived properties are calculated on-demand using Compose's derivedStateOf
 */
@Immutable
data class SimpleViewportState(
    val viewportRect: Rect,
    val canvasSize: Size,
    val zoom: Float,
    val offset: Offset,
    val focusedCell: HexCellWithMedia?,
    val selectedMedia: Media?,
    val selectionMode: SelectionMode,
    val gridBounds: Rect?
)
