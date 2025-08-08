package dev.serhiiyaremych.lumina.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.*

/**
 * Configuration for offscreen indicator behavior
 */
data class OffscreenIndicatorConfig(
    val edgePadding: Float = 24f,          // Distance from viewport edge
    val minIndicatorDistance: Float = 48f, // Minimum distance between indicators
    val fadeOutDistance: Float = 0.8f,     // Start fading when grid is X% outside viewport
    val maxIndicators: Int = 4             // Maximum number of indicators to show
)

/**
 * Represents a single offscreen indicator with its position and properties
 */
@Stable
data class OffscreenIndicator(
    val id: String,
    val position: Offset,           // Position in screen coordinates
    val direction: Float,           // Angle in radians pointing toward content
    val distance: Float,           // Normalized distance to content (0.0-1.0)
    val contentBounds: Rect,       // The offscreen content bounds
    val priority: Int = 1          // Priority for when multiple indicators compete
)

/**
 * Edge position enum for indicator placement
 */
enum class ViewportEdge {
    TOP, BOTTOM, LEFT, RIGHT, 
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

/**
 * Manages calculation and positioning of offscreen content indicators.
 * 
 * This system calculates directional indicators that appear on viewport edges
 * when hex grid content moves outside the visible area, similar to radar or
 * minimap systems in games.
 */
class OffscreenIndicatorManager(
    private val config: OffscreenIndicatorConfig = OffscreenIndicatorConfig()
) {
    
    /**
     * Calculate all offscreen indicators using dual-clamping system.
     * Marker follows grid bounds when "grabbed" but stays within viewport bounds.
     */
    fun calculateIndicators(
        viewportRect: Rect,
        canvasSize: Size,
        gridBounds: Rect
    ): List<OffscreenIndicator> {
        
        if (viewportRect.overlaps(gridBounds)) {
            // Grid is at least partially visible, no indicators needed
            return emptyList()
        }
        
        // Start with marker at viewport center (in content coordinates)
        val viewportCenterContent = viewportRect.center
        
        android.util.Log.d("OffscreenIndicator", "ViewportCenter (content): $viewportCenterContent")
        android.util.Log.d("OffscreenIndicator", "ViewportRect: $viewportRect")
        android.util.Log.d("OffscreenIndicator", "GridBounds: $gridBounds")
        
        // Apply dual-clamping: first to grid bounds (grabbing), then to viewport bounds (hard limit)
        val clampedPositionContent = Offset(
            x = viewportCenterContent.x
                .coerceIn(gridBounds.left, gridBounds.right)      // Grid grabbing
                .coerceIn(viewportRect.left, viewportRect.right), // Viewport limit
            y = viewportCenterContent.y
                .coerceIn(gridBounds.top, gridBounds.bottom)      // Grid grabbing
                .coerceIn(viewportRect.top, viewportRect.bottom)  // Viewport limit
        )
        
        android.util.Log.d("OffscreenIndicator", "Clamped position (content): $clampedPositionContent")
        
        // Convert from content coordinates to screen coordinates
        val contentToScreenScaleX = canvasSize.width / viewportRect.width
        val contentToScreenScaleY = canvasSize.height / viewportRect.height
        val contentToScreenOffsetX = -viewportRect.left * contentToScreenScaleX
        val contentToScreenOffsetY = -viewportRect.top * contentToScreenScaleY
        
        val markerPositionScreen = Offset(
            x = clampedPositionContent.x * contentToScreenScaleX + contentToScreenOffsetX,
            y = clampedPositionContent.y * contentToScreenScaleY + contentToScreenOffsetY
        )
        
        // Apply viewport edge padding (final screen coordinate clamping)
        val padding = config.edgePadding
        val finalMarkerPosition = Offset(
            x = markerPositionScreen.x.coerceIn(padding, canvasSize.width - padding),
            y = markerPositionScreen.y.coerceIn(padding, canvasSize.height - padding)
        )
        
        android.util.Log.d("OffscreenIndicator", "Final marker position (screen): $finalMarkerPosition")
        
        // Calculate direction arrow should point (toward grid center)
        val gridCenter = gridBounds.center
        val directionToGrid = gridCenter - viewportCenterContent
        val directionAngle = atan2(directionToGrid.y, directionToGrid.x)
        
        android.util.Log.d("OffscreenIndicator", "Direction angle: ${directionAngle * 180 / PI} degrees")
        
        // Calculate distance metric for fading
        val distance = calculateNormalizedDistance(viewportRect, gridBounds)
        
        return listOf(
            OffscreenIndicator(
                id = "grid_indicator",
                position = finalMarkerPosition,
                direction = directionAngle,
                distance = distance,
                contentBounds = gridBounds,
                priority = 1
            )
        )
    }
    
    /**
     * Find the closest point on a rectangle to a given point
     */
    private fun findClosestPointOnRect(point: Offset, rect: Rect): Offset {
        val clampedX = point.x.coerceIn(rect.left, rect.right)
        val clampedY = point.y.coerceIn(rect.top, rect.bottom)
        return Offset(clampedX, clampedY)
    }
    
    
    /**
     * Calculate normalized distance between viewport and grid (0.0 = touching, 1.0 = far)
     */
    private fun calculateNormalizedDistance(viewportRect: Rect, gridBounds: Rect): Float {
        val viewportCenter = viewportRect.center
        val gridCenter = gridBounds.center
        val distance = (viewportCenter - gridCenter).getDistance()
        
        // Normalize against viewport diagonal as reference
        val viewportDiagonal = sqrt(viewportRect.width * viewportRect.width + viewportRect.height * viewportRect.height)
        
        return (distance / viewportDiagonal).coerceIn(0f, 1f)
    }
    
    /**
     * Determine which viewport edge is closest to a given angle
     */
    private fun determineEdgeFromAngle(angle: Float): ViewportEdge {
        val normalizedAngle = (angle + 2 * PI) % (2 * PI) // Normalize to 0-2π
        val degrees = normalizedAngle * 180 / PI
        
        return when {
            degrees < 22.5 || degrees >= 337.5 -> ViewportEdge.RIGHT
            degrees < 67.5 -> ViewportEdge.BOTTOM_RIGHT
            degrees < 112.5 -> ViewportEdge.BOTTOM
            degrees < 157.5 -> ViewportEdge.BOTTOM_LEFT
            degrees < 202.5 -> ViewportEdge.LEFT
            degrees < 247.5 -> ViewportEdge.TOP_LEFT
            degrees < 292.5 -> ViewportEdge.TOP
            degrees < 337.5 -> ViewportEdge.TOP_RIGHT
            else -> ViewportEdge.RIGHT
        }
    }
}