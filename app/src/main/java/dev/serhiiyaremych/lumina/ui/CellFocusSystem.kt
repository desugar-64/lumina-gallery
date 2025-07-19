package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Configuration for cell focus detection.
 */
data class CellFocusConfig(
    /** Minimum viewport coverage to be considered significant (default: 0.25f = 25%) */
    val significanceThreshold: Float = 0.25f,
    /** Delay in ms before triggering callbacks after gesture updates (default: 200ms) */
    val gestureDelayMs: Long = 200L,
    /** Enable debug logging (default: false for performance) */
    val debugLogging: Boolean = false
)

/**
 * Interface for receiving cell focus updates.
 */
interface CellFocusListener {
    /**
     * Called when a cell becomes significant in the viewport.
     */
    fun onCellSignificant(hexCellWithMedia: HexCellWithMedia, coverage: Float)
    
    /**
     * Called when a cell becomes insignificant or leaves viewport.
     */
    fun onCellInsignificant(hexCellWithMedia: HexCellWithMedia)
}

/**
 * Manages cell focus detection based on gesture updates and viewport calculations.
 */
class CellFocusManager(
    private val config: CellFocusConfig = CellFocusConfig(),
    private val scope: CoroutineScope,
    private val listener: CellFocusListener
) {
    private val significantCells = mutableSetOf<HexCell>()
    private var gestureJob: Job? = null
    
    /**
     * Called on every gesture update (pan/zoom). Calculates focus with debouncing.
     */
    fun onGestureUpdate(
        geometryReader: GeometryReader,
        contentSize: Size,
        zoom: Float,
        offset: Offset,
        hexCellsWithMedia: List<HexCellWithMedia>
    ) {
        // Cancel previous calculation
        gestureJob?.cancel()
        
        // Start new delayed calculation on background dispatcher for heavy geometry math
        gestureJob = scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            delay(config.gestureDelayMs)
            calculateCellFocus(geometryReader, contentSize, zoom, offset, hexCellsWithMedia)
        }
    }
    
    /**
     * Immediate callback for user clicks - no delay.
     * Clicked cells will be evaluated by subsequent viewport calculations.
     */
    fun onCellClicked(hexCell: HexCell, hexCellWithMedia: HexCellWithMedia, clickedMedia: Media? = null) {
        // Don't cancel gesture calculations - let viewport determine final state
        
        // Immediate callback for clicks (may cause duplicate if cell is already significant)
        listener.onCellSignificant(hexCellWithMedia, 1.0f) // Max coverage for clicks
    }
    
    private fun calculateCellFocus(
        geometryReader: GeometryReader,
        contentSize: Size,
        zoom: Float,
        offset: Offset,
        hexCellsWithMedia: List<HexCellWithMedia>
    ) {
        // Calculate current viewport in content coordinates
        val viewportRect = calculateViewportRect(contentSize, zoom, offset)
        
        // Update visible area in geometry reader
        geometryReader.updateVisibleArea(viewportRect)
        
        val newSignificantCells = mutableSetOf<HexCell>()
        
        // Check each cell's coverage
        hexCellsWithMedia.forEach { cellWithMedia ->
            val cellBounds = geometryReader.getHexCellBounds(cellWithMedia.hexCell)
            if (cellBounds != null) {
                val coverage = calculateViewportCoverage(cellBounds, viewportRect)
                val isSignificant = coverage >= config.significanceThreshold
                
                if (isSignificant) {
                    newSignificantCells.add(cellWithMedia.hexCell)
                    
                    // Trigger callback if this cell wasn't previously significant
                    if (!significantCells.contains(cellWithMedia.hexCell)) {
                        if (config.debugLogging) {
                            android.util.Log.d("CellFocus", "NEW Cell SIGNIFICANT: (${cellWithMedia.hexCell.q}, ${cellWithMedia.hexCell.r}) coverage=${String.format("%.3f", coverage)}")
                        }
                        listener.onCellSignificant(cellWithMedia, coverage)
                    }
                }
            }
        }
        
        // Find cells that became insignificant (BEFORE updating the set)
        val cellsToRemove = significantCells - newSignificantCells
        if (config.debugLogging) {
            android.util.Log.d("CellFocus", "Significant before: ${significantCells.size}, new: ${newSignificantCells.size}, removing: ${cellsToRemove.size}")
        }
        
        // Trigger callbacks for cells that became insignificant
        cellsToRemove.forEach { hexCell ->
            if (config.debugLogging) {
                android.util.Log.d("CellFocus", "Cell INSIGNIFICANT: (${hexCell.q}, ${hexCell.r})")
            }
            // Find the HexCellWithMedia for this hexCell
            val cellWithMedia = hexCellsWithMedia.first { it.hexCell == hexCell }
            listener.onCellInsignificant(cellWithMedia)
        }
        
        // Update significant cells set (do this AFTER calculating removals)
        significantCells.clear()
        significantCells.addAll(newSignificantCells)
        if (config.debugLogging) {
            android.util.Log.d("CellFocus", "Final significant cells: ${significantCells.size}")
        }
    }
    
    /**
     * Calculates viewport rectangle in content coordinates based on current transform.
     */
    private fun calculateViewportRect(contentSize: Size, zoom: Float, offset: Offset): Rect {
        // Guard against zero zoom to prevent division by zero
        val safeZoom = zoom.coerceAtLeast(0.001f)
        
        val contentWidth = contentSize.width / safeZoom
        val contentHeight = contentSize.height / safeZoom
        val contentLeft = -offset.x / safeZoom
        val contentTop = -offset.y / safeZoom
        
        return Rect(
            left = contentLeft,
            top = contentTop,
            right = contentLeft + contentWidth,
            bottom = contentTop + contentHeight
        )
    }
    
    /**
     * Calculates what percentage of the viewport this cell occupies.
     */
    private fun calculateViewportCoverage(cellBounds: Rect, viewportRect: Rect): Float {
        val intersection = cellBounds.intersect(viewportRect)
        if (intersection.isEmpty) return 0.0f
        
        val intersectionArea = intersection.width * intersection.height
        val viewportArea = viewportRect.width * viewportRect.height
        
        return intersectionArea / viewportArea
    }
}