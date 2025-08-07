package dev.serhiiyaremych.lumina.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media

/**
 * Unified viewport state manager that consolidates all viewport-aware decisions.
 * 
 * Single source of truth for:
 * - Viewport bounds calculation
 * - Selection mode decisions (CELL_MODE vs PHOTO_MODE) 
 * - Panel visibility logic
 * - Media deselection when out of viewport
 * - Cell significance detection
 * 
 * This eliminates duplicate viewport calculations across CellFocusManager, 
 * App.kt selection logic, panel positioning, and other components.
 */
@Stable
data class ViewportState(
    val viewportRect: Rect,
    val canvasSize: Size,
    val zoom: Float,
    val offset: Offset,
    val focusedCell: HexCellWithMedia?,
    val selectedMedia: Media?,
    val selectionMode: SelectionMode,
    
    // Derived state
    val cellInViewport: Boolean,
    val mediaInViewport: Boolean,
    val cellLargerThanViewport: Boolean,
    val suggestedSelectionMode: SelectionMode,
    val shouldShowPanel: Boolean,
    val shouldDeselectMedia: Boolean
)

/**
 * Configuration for viewport-based decisions
 */
data class ViewportConfig(
    val cellSignificanceThreshold: Float = 0.25f, // 25% viewport coverage for cell focus
    val modeTransitionThreshold: Float = 1.2f,    // 120% viewport size for photo mode
    val minViewportCoverage: Float = 0.1f,        // 10% minimum coverage to stay selected
    val gestureDelayMs: Long = 200L,              // Debounce gesture updates
    val panelVisibilityThreshold: Float = 0.15f   // 15% coverage to show panel
)

/**
 * Unified viewport state manager - single source of truth for all viewport decisions
 */
class ViewportStateManager(
    private val config: ViewportConfig = ViewportConfig()
) {
    
    /**
     * Calculate the current viewport state based on transformation and selection parameters
     */
    fun calculateViewportState(
        canvasSize: Size,
        zoom: Float,
        offset: Offset,
        focusedCell: HexCellWithMedia?,
        selectedMedia: Media?,
        currentSelectionMode: SelectionMode
    ): ViewportState {
        
        // Calculate viewport rectangle in content coordinates
        val viewportRect = calculateViewportRect(canvasSize, zoom, offset)
        
        // Calculate cell state if we have a focused cell
        val cellState = focusedCell?.let { cell ->
            calculateCellViewportState(cell, viewportRect, canvasSize, zoom, offset)
        }
        
        // Calculate media state if we have selected media
        val mediaState = if (selectedMedia != null && focusedCell != null) {
            calculateMediaViewportState(selectedMedia, focusedCell, viewportRect)
        } else null
        
        // Determine suggested selection mode based on viewport conditions
        val suggestedMode = determineSuggestedSelectionMode(
            cellState, currentSelectionMode, selectedMedia != null
        )
        
        // Determine if media should be deselected due to viewport constraints
        val shouldDeselect = shouldDeselectMedia(cellState, mediaState, selectedMedia != null)
        
        return ViewportState(
            viewportRect = viewportRect,
            canvasSize = canvasSize,
            zoom = zoom,
            offset = offset,
            focusedCell = focusedCell,
            selectedMedia = selectedMedia,
            selectionMode = currentSelectionMode,
            
            // Derived state
            cellInViewport = cellState?.inViewport ?: false,
            mediaInViewport = mediaState?.inViewport ?: false,
            cellLargerThanViewport = cellState?.largerThanViewport ?: false,
            suggestedSelectionMode = suggestedMode,
            shouldShowPanel = cellState?.shouldShowPanel ?: false,
            shouldDeselectMedia = shouldDeselect
        )
    }
    
    /**
     * Calculate viewport rectangle in content coordinates
     */
    private fun calculateViewportRect(canvasSize: Size, zoom: Float, offset: Offset): Rect {
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
     * Cell viewport analysis result
     */
    private data class CellViewportState(
        val inViewport: Boolean,
        val coverage: Float,
        val largerThanViewport: Boolean,
        val shouldShowPanel: Boolean,
        val screenBounds: Rect
    )
    
    /**
     * Calculate cell's relationship to viewport
     */
    private fun calculateCellViewportState(
        cell: HexCellWithMedia,
        viewportRect: Rect,
        canvasSize: Size,
        zoom: Float,
        offset: Offset
    ): CellViewportState {
        
        // Calculate cell bounds in content coordinates
        val cellVertices = cell.hexCell.vertices
        val cellMinX = cellVertices.minOf { it.x }
        val cellMaxX = cellVertices.maxOf { it.x }
        val cellMinY = cellVertices.minOf { it.y }
        val cellMaxY = cellVertices.maxOf { it.y }
        val contentCellBounds = Rect(cellMinX, cellMinY, cellMaxX, cellMaxY)
        
        // Transform to screen coordinates for size comparison
        val screenMinX = (cellMinX * zoom) + offset.x
        val screenMaxX = (cellMaxX * zoom) + offset.x
        val screenMinY = (cellMinY * zoom) + offset.y
        val screenMaxY = (cellMaxY * zoom) + offset.y
        val screenBounds = Rect(screenMinX, screenMinY, screenMaxX, screenMaxY)
        
        // Calculate viewport coverage (what % of viewport does this cell occupy)
        val intersection = viewportRect.intersect(contentCellBounds)
        val coverage = if (intersection.isEmpty) {
            0f
        } else {
            (intersection.width * intersection.height) / (viewportRect.width * viewportRect.height)
        }
        
        // Check if cell is larger than viewport (for mode switching)
        val screenCellWidth = screenMaxX - screenMinX
        val screenCellHeight = screenMaxY - screenMinY
        val isLargerThanViewport = screenCellWidth > (canvasSize.width * config.modeTransitionThreshold) || 
                                  screenCellHeight > (canvasSize.height * config.modeTransitionThreshold)
        
        return CellViewportState(
            inViewport = coverage > config.minViewportCoverage,
            coverage = coverage,
            largerThanViewport = isLargerThanViewport,
            shouldShowPanel = coverage >= config.panelVisibilityThreshold,
            screenBounds = screenBounds
        )
    }
    
    /**
     * Media viewport analysis result
     */
    private data class MediaViewportState(
        val inViewport: Boolean,
        val coverage: Float
    )
    
    /**
     * Calculate media's relationship to viewport (simplified - assumes it's within the cell)
     */
    private fun calculateMediaViewportState(
        media: Media,
        cell: HexCellWithMedia,
        viewportRect: Rect
    ): MediaViewportState {
        // For now, assume media visibility follows cell visibility
        // This could be enhanced to check individual media bounds if needed
        val cellVertices = cell.hexCell.vertices
        val cellMinX = cellVertices.minOf { it.x }
        val cellMaxX = cellVertices.maxOf { it.x }
        val cellMinY = cellVertices.minOf { it.y }
        val cellMaxY = cellVertices.maxOf { it.y }
        val contentCellBounds = Rect(cellMinX, cellMinY, cellMaxX, cellMaxY)
        
        val intersection = viewportRect.intersect(contentCellBounds)
        val coverage = if (intersection.isEmpty) {
            0f
        } else {
            (intersection.width * intersection.height) / (viewportRect.width * viewportRect.height)
        }
        
        return MediaViewportState(
            inViewport = coverage > config.minViewportCoverage,
            coverage = coverage
        )
    }
    
    /**
     * Determine suggested selection mode based on viewport conditions
     */
    private fun determineSuggestedSelectionMode(
        cellState: CellViewportState?,
        currentMode: SelectionMode,
        hasSelectedMedia: Boolean
    ): SelectionMode {
        // No change if no cell context
        if (cellState == null || !hasSelectedMedia) {
            return SelectionMode.CELL_MODE
        }
        
        // Switch to PHOTO_MODE if cell is significantly larger than viewport
        if (cellState.largerThanViewport && currentMode == SelectionMode.CELL_MODE) {
            return SelectionMode.PHOTO_MODE
        }
        
        // Switch back to CELL_MODE if cell is no longer larger than viewport
        if (!cellState.largerThanViewport && currentMode == SelectionMode.PHOTO_MODE) {
            return SelectionMode.CELL_MODE
        }
        
        return currentMode
    }
    
    /**
     * Determine if media should be deselected due to viewport constraints
     */
    private fun shouldDeselectMedia(
        cellState: CellViewportState?,
        mediaState: MediaViewportState?,
        hasSelectedMedia: Boolean
    ): Boolean {
        if (!hasSelectedMedia) return false
        
        // Deselect if cell is out of viewport
        if (cellState != null && !cellState.inViewport) {
            return true
        }
        
        // Deselect if media is out of viewport (when we have specific media tracking)
        if (mediaState != null && !mediaState.inViewport) {
            return true
        }
        
        return false
    }
}