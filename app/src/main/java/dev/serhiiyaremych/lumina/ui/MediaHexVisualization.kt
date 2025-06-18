package dev.serhiiyaremych.lumina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexGridGenerator
import dev.serhiiyaremych.lumina.domain.model.Media
import java.time.LocalDate
import kotlin.math.min

@Composable
fun MediaHexVisualization(
    hexGridGenerator: HexGridGenerator,
    hexGridRenderer: HexGridRenderer,
    groupedMedia: Map<LocalDate, List<Media>>,
    hexGridSize: Int,
    hexCellSize: Dp,
    zoom: Float,
    offset: Offset
) {
    val density = LocalDensity.current
    if (groupedMedia.isEmpty()) return

    val sortedGroups = groupedMedia.keys.sorted()
    val grid = remember(hexGridSize, hexCellSize, density) {
        hexGridGenerator.generateGrid(
            gridSize = hexGridSize,
            cellSizeDp = hexCellSize,
            density = density
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Apply zoom (clamped to avoid precision issues) and pan transforms to canvas
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        withTransform({
            scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
            translate(offset.x / clampedZoom, offset.y / clampedZoom)
        }) {
            // Draw hex grid at original scale
            hexGridRenderer.drawHexGrid(
                drawScope = this,
                hexGrid = grid,
                zoom = 1f, // Draw at native scale since transforms are applied
                offset = Offset.Zero
            )

            // Draw media in hex cells at original scale
            sortedGroups.forEachIndexed { index, date ->
                val mediaForGroup = groupedMedia[date] ?: return@forEachIndexed

                if (index < grid.cells.size) {
                    val hexCell = grid.cells[index]

                    drawMediaInHexCell(
                        hexCell = hexCell,
                        mediaList = mediaForGroup,
                        // Draw at native scale
                        offset = Offset.Zero
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMediaInHexCell(
    hexCell: HexCell,
    mediaList: List<Media>,
    offset: Offset
) {
    // Calculate hexBounds only scaled by zoom (without offset)
    val scaledHexBounds = calculateHexBounds(hexCell) // Use native scale since canvas is transformed
    val minDimension = min(scaledHexBounds.width, scaledHexBounds.height)
    val thumbnailMaxSize = minDimension * 0.4f

    mediaList.forEach { media ->
        val seed = (media.id + hexCell.q * 1000000 + hexCell.r * 1000).toInt()
        val (localPosition, size) = generateScaledPositionWithOffset(
            media = media,
            scaledHexBounds = scaledHexBounds,
            thumbnailMaxSize = thumbnailMaxSize,
            seed = seed
        )
        val color = when (media) {
            is Media.Image -> Color(0xFF2196F3)
            is Media.Video -> Color(0xFF4CAF50)
        }

        // Apply only TransformableContent's offset to position
        drawRect(
            color = color,
            topLeft = Offset(localPosition.x + offset.x, localPosition.y + offset.y),
            size = size
        )
    }
}

/**
 * Generates a position for the media thumbnail in hex cell relative to hex bounds (without any pan offset)
 */
private fun generateScaledPositionWithOffset(
    media: Media,
    scaledHexBounds: Rect,
    thumbnailMaxSize: Float,
    seed: Int
): Pair<Offset, androidx.compose.ui.geometry.Size> {
    // Calculate thumbnail dimensions with aspect ratio
    val aspectRatio = if (media.height != 0) media.width.toFloat() / media.height.toFloat() else 1f
    val (width, height) = if (aspectRatio >= 1f) {
        thumbnailMaxSize to thumbnailMaxSize / aspectRatio
    } else {
        thumbnailMaxSize * aspectRatio to thumbnailMaxSize
    }

    val random = kotlin.random.Random(seed)
    val availableWidth = scaledHexBounds.width - width
    val availableHeight = scaledHexBounds.height - height

    // Calculate position with scaled hex bounds (which are in hex local zoomed coordinates)
    return if (availableWidth > 0 && availableHeight > 0) {
        Offset(
            x = scaledHexBounds.left + random.nextFloat() * availableWidth,
            y = scaledHexBounds.top + random.nextFloat() * availableHeight
        ) to androidx.compose.ui.geometry.Size(width, height)
    } else {
        // Fallback position: center of the scaled hex bounds
        Offset(
            scaledHexBounds.left + scaledHexBounds.width / 2 - width / 2,
            scaledHexBounds.top + scaledHexBounds.height / 2 - height / 2
        ) to androidx.compose.ui.geometry.Size(width, height)
    }
}

private fun calculateHexBounds(hexCell: HexCell): androidx.compose.ui.geometry.Rect {
    val vertices = hexCell.vertices
    val minX = vertices.minOf { it.x }
    val maxX = vertices.maxOf { it.x }
    val minY = vertices.minOf { it.y }
    val maxY = vertices.maxOf { it.y }

    return androidx.compose.ui.geometry.Rect(
        left = minX,
        top = minY,
        right = maxX,
        bottom = maxY
    )
}
