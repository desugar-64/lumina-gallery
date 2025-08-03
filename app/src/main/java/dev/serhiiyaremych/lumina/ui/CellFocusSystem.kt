package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
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
 * Uses enhanced centered viewport detection for more intuitive focus behavior.
 */
class CellFocusManager(
    private val config: CellFocusConfig = CellFocusConfig(),
    private val scope: CoroutineScope,
    private val listener: CellFocusListener
) {
    private var currentSignificantCell: HexCell? = null
    private var gestureJob: Job? = null

    // Enhanced config with centered detection
    private val enhancedConfig = EnhancedCellFocusConfig(
        significanceThreshold = config.significanceThreshold,
        gestureDelayMs = config.gestureDelayMs,
        debugLogging = config.debugLogging,
        useCenteredDetection = true,
        centeredSquareFactor = 0.6f
    )

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
     * Properly manages focus state transitions to ensure unrotate animations work.
     */
    fun onCellClicked(
        hexCellWithMedia: HexCellWithMedia,
        allHexCellsWithMedia: List<HexCellWithMedia>
    ) {
        val newCell = hexCellWithMedia.hexCell
        val previousCell = currentSignificantCell
        
        // Only proceed if this is a different cell
        if (newCell != previousCell) {
            // Call onCellInsignificant for the previous cell (triggers unrotate animation)
            if (previousCell != null) {
                allHexCellsWithMedia.find { it.hexCell == previousCell }?.let { previousCellWithMedia ->
                    listener.onCellInsignificant(previousCellWithMedia)
                }
            }
            
            // Update internal state BEFORE calling onCellSignificant
            currentSignificantCell = newCell
            
            // Call onCellSignificant for the new cell
            listener.onCellSignificant(hexCellWithMedia, 1.0f) // Max coverage for clicks
        }
    }

    private fun calculateCellFocus(
        geometryReader: GeometryReader,
        contentSize: Size,
        zoom: Float,
        offset: Offset,
        hexCellsWithMedia: List<HexCellWithMedia>
    ) {
        // Use enhanced centered cell focus detection
        val newSignificantCell = calculateCenteredCellFocus(
            geometryReader = geometryReader,
            contentSize = contentSize,
            zoom = zoom,
            offset = offset,
            hexCellsWithMedia = hexCellsWithMedia,
            config = enhancedConfig
        )

        // Update visible area in geometry reader for other uses
        val viewportRect = calculateViewportRect(contentSize, zoom, offset)
        geometryReader.updateVisibleArea(viewportRect)

        // Handle cell focus transitions
        val previousCell = currentSignificantCell
        val newCell = newSignificantCell?.hexCell

        // Cell became insignificant
        if (previousCell != null && newCell != previousCell) {
            if (enhancedConfig.debugLogging) {
                android.util.Log.d("CellFocus", "Cell INSIGNIFICANT: (${previousCell.q}, ${previousCell.r})")
            }
            // Find the HexCellWithMedia for the previous cell
            hexCellsWithMedia.find { it.hexCell == previousCell }?.let { cellWithMedia ->
                listener.onCellInsignificant(cellWithMedia)
            }
        }

        // Cell became significant
        if (newCell != null && newCell != previousCell) {
            val coverage = calculateDetailedCoverage(geometryReader, newSignificantCell!!, contentSize, zoom, offset)
            if (enhancedConfig.debugLogging) {
                android.util.Log.d("CellFocus", "NEW Cell SIGNIFICANT: (${newCell.q}, ${newCell.r}) coverage=${String.format("%.3f", coverage)} (centered detection)")
            }
            listener.onCellSignificant(newSignificantCell, coverage)
        }

        // Update current state
        currentSignificantCell = newCell

        if (enhancedConfig.debugLogging) {
            android.util.Log.d("CellFocus", "Current significant cell: ${currentSignificantCell?.let { "(${it.q}, ${it.r})" } ?: "none"}")
        }
    }

    /**
     * Calculate detailed coverage for logging/debugging
     */
    private fun calculateDetailedCoverage(
        geometryReader: GeometryReader,
        cellWithMedia: HexCellWithMedia,
        contentSize: Size,
        zoom: Float,
        offset: Offset
    ): Float {
        val cellBounds = geometryReader.getHexCellBounds(cellWithMedia.hexCell) ?: return 0f
        val viewportRect = calculateViewportRect(contentSize, zoom, offset)
        return calculateViewportCoverage(cellBounds, viewportRect)
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
