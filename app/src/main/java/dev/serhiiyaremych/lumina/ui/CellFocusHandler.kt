package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import kotlin.math.min

/**
 * Unified cell focus handler that provides consistent focus behavior
 * for both manual clicks and automatic viewport-based detection.
 *
 * Single source of truth for:
 * - Cell bounds calculation and focus animations
 * - Visual highlighting and selection state
 * - Focus animation triggering
 */
interface CellFocusHandler {
    /**
     * Handle cell becoming focused/significant.
     * Triggers all the same actions as a manual cell click:
     * - Shows focused cell panel
     * - Triggers focus animation to center on cell
     * - Sets cell selection visual state
     * - Clears any selected media
     * - Sets selection mode to CELL_MODE
     */
    fun onCellFocused(hexCellWithMedia: HexCellWithMedia, coverage: Float)

    /**
     * Handle cell losing focus/becoming insignificant.
     * Clears focus state and hides panels.
     */
    fun onCellUnfocused(hexCellWithMedia: HexCellWithMedia)
}

/**
 * Enhanced cell focus configuration with centered viewport detection
 */
data class EnhancedCellFocusConfig(
    /** Minimum viewport coverage to be considered significant (default: 0.25f = 25%) */
    val significanceThreshold: Float = 0.25f,
    /** Delay in ms before triggering callbacks after gesture updates (default: 200ms) */
    val gestureDelayMs: Long = 200L,
    /** Enable debug logging (default: false for performance) */
    val debugLogging: Boolean = false,
    /** Use centered square viewport detection instead of full viewport (default: true) */
    val useCenteredDetection: Boolean = true,
    /** Size factor for centered detection square (default: 0.6f = 60% of smallest viewport dimension) */
    val centeredSquareFactor: Float = 0.6f
)

/**
 * Enhanced cell focus detection that prioritizes cells that are:
 * 1. Most centered in the viewport
 * 2. Take significant space in a centered square region
 *
 * This provides more intuitive focus behavior compared to simple coverage detection.
 */
fun calculateCenteredCellFocus(
    geometryReader: GeometryReader,
    contentSize: Size,
    zoom: Float,
    offset: Offset,
    hexCellsWithMedia: List<HexCellWithMedia>,
    config: EnhancedCellFocusConfig = EnhancedCellFocusConfig()
): HexCellWithMedia? {
    // Calculate viewport in content coordinates
    val viewportRect = calculateViewportRect(contentSize, zoom, offset)

    // Calculate centered detection region
    val detectionRect = if (config.useCenteredDetection) {
        calculateCenteredDetectionRect(viewportRect, config.centeredSquareFactor)
    } else {
        viewportRect
    }

    var mostSignificantCell: HexCellWithMedia? = null
    var highestScore = 0f

    hexCellsWithMedia.forEach { cellWithMedia ->
        val cellBounds = geometryReader.getHexCellBounds(cellWithMedia.hexCell)
        if (cellBounds != null) {
            val score = calculateCellSignificanceScore(
                cellBounds = cellBounds,
                detectionRect = detectionRect,
                viewportRect = viewportRect,
                config = config
            )

            if (score > highestScore && score >= config.significanceThreshold) {
                mostSignificantCell = cellWithMedia
                highestScore = score
            }
        }
    }

    return mostSignificantCell
}

/**
 * Calculate viewport rectangle in content coordinates
 */
private fun calculateViewportRect(contentSize: Size, zoom: Float, offset: Offset): Rect {
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
 * Calculate centered detection rectangle as a square in the center of viewport
 */
private fun calculateCenteredDetectionRect(viewportRect: Rect, sizeFactor: Float): Rect {
    val viewportCenter = viewportRect.center
    val smallestDimension = min(viewportRect.width, viewportRect.height)
    val squareSize = smallestDimension * sizeFactor
    val halfSize = squareSize / 2f

    return Rect(
        left = viewportCenter.x - halfSize,
        top = viewportCenter.y - halfSize,
        right = viewportCenter.x + halfSize,
        bottom = viewportCenter.y + halfSize
    )
}

/**
 * Calculate cell significance score combining coverage and centering
 */
private fun calculateCellSignificanceScore(
    cellBounds: Rect,
    detectionRect: Rect,
    viewportRect: Rect,
    config: EnhancedCellFocusConfig
): Float {
    // Calculate coverage in the detection region
    val detectionIntersection = cellBounds.intersect(detectionRect)
    if (detectionIntersection.isEmpty) return 0f

    val detectionCoverage = (detectionIntersection.width * detectionIntersection.height) /
        (detectionRect.width * detectionRect.height)

    if (config.useCenteredDetection) {
        // For centered detection, also consider how well-centered the cell is
        val cellCenter = cellBounds.center
        val viewportCenter = viewportRect.center
        val maxDistance = min(viewportRect.width, viewportRect.height) / 2f
        val distance = kotlin.math.sqrt(
            (cellCenter.x - viewportCenter.x).let { it * it } +
                (cellCenter.y - viewportCenter.y).let { it * it }
        )
        val centeringScore = 1f - (distance / maxDistance).coerceIn(0f, 1f)

        // Combine coverage (70%) and centering (30%) scores
        // Add additional constraint: require at least 20% coverage to be considered significant
        if (detectionCoverage < 0.2f) return 0f

        return (detectionCoverage * 0.7f) + (centeringScore * 0.3f)
    } else {
        return detectionCoverage
    }
}

/**
 * Calculate cell bounds for focus animation
 */
fun calculateCellFocusBounds(hexCell: HexCell): Rect {
    val vertices = hexCell.vertices
    val minX = vertices.minOf { it.x }
    val maxX = vertices.maxOf { it.x }
    val minY = vertices.minOf { it.y }
    val maxY = vertices.maxOf { it.y }
    return Rect(minX, minY, maxX, maxY)
}
