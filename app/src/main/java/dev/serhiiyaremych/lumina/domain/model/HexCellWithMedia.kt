package dev.serhiiyaremych.lumina.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeColorFilter

/**
 * A hex cell containing positioned media items.
 * This pre-computed structure eliminates business logic from the UI layer.
 *
 * @property hexCell The hexagonal cell geometry
 * @property mediaItems List of media items positioned within this cell
 * @property bounds Cached bounding rectangle for viewport calculations
 */
data class HexCellWithMedia(
    val hexCell: HexCell,
    val mediaItems: List<MediaWithPosition>,
    val bounds: Rect = calculateHexBounds(hexCell)
) {
    /**
     * Returns true if this hex cell intersects with the given viewport rectangle.
     * Used for efficient viewport-based filtering.
     */
    fun isInViewport(viewport: Rect, margin: Float = 0f): Boolean {
        val expandedViewport = if (margin > 0f) {
            Rect(
                left = viewport.left - margin,
                top = viewport.top - margin,
                right = viewport.right + margin,
                bottom = viewport.bottom + margin
            )
        } else {
            viewport
        }

        return bounds.overlaps(expandedViewport)
    }
}

/**
 * Media item with pre-computed positioning within a hex cell.
 * All positioning calculations are done once in the domain layer.
 *
 * @property media The actual media data
 * @property relativePosition Position within the hex cell (0,0 = hex cell top-left)
 * @property size Computed size maintaining aspect ratio
 * @property absoluteBounds Absolute bounds in world coordinates for hit testing
 * @property seed Random seed used for positioning (for consistency)
 * @property rotationAngle Rotation angle in degrees for realistic scattered photo effect
 */
data class MediaWithPosition(
    val media: Media,
    val relativePosition: Offset,
    val size: Size,
    val absoluteBounds: Rect,
    val seed: Int,
    val rotationAngle: Float = 0f
) {
    /**
     * Aspect ratio of the media item
     */
    val aspectRatio: Float = if (media.height != 0) {
        media.width.toFloat() / media.height.toFloat()
    } else {
        1f
    }

    /**
     * Returns true if the given point (in world coordinates) hits this media item
     */
    fun containsPoint(point: Offset): Boolean = absoluteBounds.contains(point)
}

/**
 * Complete hex grid layout with all media items positioned.
 * This is the clean data structure that MediaHexVisualization will consume.
 *
 * @property hexGrid The underlying hex grid geometry
 * @property hexCellsWithMedia List of hex cells containing positioned media
 * @property totalMediaCount Total number of media items across all cells
 * @property bounds Overall bounds of the entire grid
 */
data class HexGridLayout(
    val hexGrid: HexGrid,
    val hexCellsWithMedia: List<HexCellWithMedia>,
    val totalMediaCount: Int = hexCellsWithMedia.sumOf { it.mediaItems.size },
    val bounds: Rect = calculateLayoutBounds(hexCellsWithMedia)
) {
    /**
     * Returns hex cells that are visible within the given viewport.
     * This is the primary method for viewport-based filtering.
     */
    fun getVisibleHexCells(viewport: Rect, margin: Float = 0f): List<HexCellWithMedia> = hexCellsWithMedia.filter { hexCellWithMedia ->
        hexCellWithMedia.isInViewport(viewport, margin)
    }

    /**
     * Finds the media item at the given world coordinate position.
     * Used for click/tap handling.
     */
    fun getMediaAtPosition(position: Offset): Media? = hexCellsWithMedia
        .firstNotNullOfOrNull { hexCellWithMedia ->
            hexCellWithMedia.mediaItems.find { mediaWithPosition ->
                mediaWithPosition.containsPoint(position)
            }?.media
        }

    /**
     * Finds the hex cell at the given world coordinate position.
     * Used for hex cell click/tap handling.
     */
    fun getHexCellAtPosition(position: Offset): HexCell? = hexCellsWithMedia
        .find { hexCellWithMedia ->
            hexCellWithMedia.bounds.contains(position)
        }?.hexCell
}

/**
 * Utility function to calculate hex cell bounding rectangle.
 * Moved from UI layer to domain layer.
 */
private fun calculateHexBounds(hexCell: HexCell): Rect {
    val vertices = hexCell.vertices
    val minX = vertices.minOf { it.x }
    val maxX = vertices.maxOf { it.x }
    val minY = vertices.minOf { it.y }
    val maxY = vertices.maxOf { it.y }

    return Rect(
        left = minX,
        top = minY,
        right = maxX,
        bottom = maxY
    )
}

/**
 * Utility function to calculate overall layout bounds.
 */
private fun calculateLayoutBounds(hexCellsWithMedia: List<HexCellWithMedia>): Rect {
    if (hexCellsWithMedia.isEmpty()) {
        return Rect.Zero
    }

    val allBounds = hexCellsWithMedia.map { it.bounds }
    val minX = allBounds.minOf { it.left }
    val maxX = allBounds.maxOf { it.right }
    val minY = allBounds.minOf { it.top }
    val maxY = allBounds.maxOf { it.bottom }

    return Rect(
        left = minX,
        top = minY,
        right = maxX,
        bottom = maxY
    )
}
