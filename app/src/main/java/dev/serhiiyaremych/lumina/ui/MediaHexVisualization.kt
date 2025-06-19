package dev.serhiiyaremych.lumina.ui

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexGrid
import dev.serhiiyaremych.lumina.domain.model.HexGridGenerator
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.ui.geometry.GeometryReader
import timber.log.Timber
import java.time.LocalDate
import kotlin.math.min

fun Rect.toAndroidGraphicsRectF(): android.graphics.RectF {
    return android.graphics.RectF(left, top, right, bottom)
}

fun Matrix.mapOffset(offset: Offset): Offset {
    val point = floatArrayOf(offset.x, offset.y)
    this.mapPoints(point)
    return Offset(point[0], point[1])
}

@Composable
fun MediaHexVisualization(
    modifier: Modifier = Modifier,
    hexGridGenerator: HexGridGenerator,
    hexGridRenderer: HexGridRenderer,
    groupedMedia: Map<LocalDate, List<Media>>,
    hexGridSize: Int,
    hexCellSize: Dp,
    zoom: Float,
    offset: Offset,
    geometryReader: GeometryReader,
    onHexCellClick: (HexCell, Matrix, IntSize) -> Unit,
    currentMatrix: Matrix,
    visibleBounds: Rect
) {
    val density = LocalDensity.current
    if (groupedMedia.isEmpty()) {
        Timber.d("MediaHexVisualization: No grouped media to display.")
        return
    }

    val sortedGroups = groupedMedia.keys.sorted()
    val grid: HexGrid = remember(hexGridSize, hexCellSize, density, hexGridGenerator) {
        Timber.d("MediaHexVisualization: Regenerating grid. Size: $hexGridSize, CellSize: $hexCellSize")
        hexGridGenerator.generateGrid(
            gridSize = hexGridSize,
            cellSizeDp = hexCellSize,
            density = density
        )
    }

    LaunchedEffect(grid, groupedMedia, geometryReader) {
        Timber.d("MediaHexVisualization: hexGrid, mediaGroups, or geometryReader changed. Clearing/populating GeometryReader.")
        geometryReader.clearAllBounds()
        grid.cells.forEach { hexCell ->
             geometryReader.addHexCellBounds(hexCell, hexCell.path.getBounds())
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(grid, currentMatrix, onHexCellClick, size) {
            detectTapGestures { tapOffset ->
                val invertedMatrix = Matrix()
                if (currentMatrix.invert(invertedMatrix)) {
                    val mappedOffset = invertedMatrix.mapOffset(tapOffset)

                    var clickedCellTarget: HexCell? = null
                    for (hexCell in grid.cells) {
                        if (hexCell.path.getBounds().contains(mappedOffset.x, mappedOffset.y)) {
                            Timber.d("Tap on cell ${hexCell.id} (bounds check)")
                            clickedCellTarget = hexCell
                            break
                        }
                    }

                    clickedCellTarget?.let {
                        Timber.d("Clicked on HexCell: ${it.id}")
                        onHexCellClick(it, currentMatrix, IntSize(this.size.width.toInt(), this.size.height.toInt()))
                    }
                } else {
                    Timber.w("Matrix inversion failed, cannot process tap.")
                }
            }
        }
    ) {
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        withTransform({
            scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
            translate(offset.x / clampedZoom, offset.y / clampedZoom)
        }) {
            hexGridRenderer.drawHexGrid(this, grid, 1f, Offset.Zero)
            sortedGroups.forEachIndexed { index, date ->
                val mediaForGroup = groupedMedia[date] ?: return@forEachIndexed
                if (index < grid.cells.size) {
                    val hexCell = grid.cells[index]
                    drawMediaInHexCell(this, hexCell, mediaForGroup, Offset.Zero, geometryReader)
                }
            }
        }
    }
}

private fun drawMediaInHexCell(
    drawScope: DrawScope,
    hexCell: HexCell,
    mediaList: List<Media>,
    offset: Offset,
    geometryReader: GeometryReader
) {
    val cellWorldBoundsComposeRect = calculateHexBounds(hexCell)
    val minDimension = min(cellWorldBoundsComposeRect.width, cellWorldBoundsComposeRect.height)
    val thumbnailMaxSize = minDimension * 0.4f

    mediaList.forEach { media ->
        val seed = (media.id + hexCell.q * 1000000 + hexCell.r * 1000).toInt()
        val (localPositionInCell, size) = generateScaledPositionWithOffset(
            media = media,
            scaledHexBounds = cellWorldBoundsComposeRect,
            thumbnailMaxSize = thumbnailMaxSize,
            seed = seed
        )
        val color = when (media) {
            is Media.Image -> Color(0xFF2196F3)
            is Media.Video -> Color(0xFF4CAF50)
        }

        val mediaWorldTopLeft = Offset(localPositionInCell.x + offset.x, localPositionInCell.y + offset.y)
        val mediaWorldRectCompose = Rect(mediaWorldTopLeft, size)

        geometryReader.addMediaBounds(media, mediaWorldRectCompose.toAndroidGraphicsRectF())

        drawScope.drawRect(
            color = color,
            topLeft = mediaWorldTopLeft,
            size = size
        )
    }
}

private fun generateScaledPositionWithOffset(
    media: Media,
    scaledHexBounds: Rect,
    thumbnailMaxSize: Float,
    seed: Int
): Pair<Offset, androidx.compose.ui.geometry.Size> {
    val aspectRatio = if (media.height != 0) media.width.toFloat() / media.height.toFloat() else 1f
    val (width, height) = if (aspectRatio >= 1f) {
        thumbnailMaxSize to thumbnailMaxSize / aspectRatio
    } else {
        thumbnailMaxSize * aspectRatio to thumbnailMaxSize
    }

    val random = kotlin.random.Random(seed)
    val availableWidth = scaledHexBounds.width - width
    val availableHeight = scaledHexBounds.height - height

    return if (availableWidth > 0f && availableHeight > 0f) {
         Offset(
            x = scaledHexBounds.left + random.nextFloat() * availableWidth,
            y = scaledHexBounds.top + random.nextFloat() * availableHeight
        ) to androidx.compose.ui.geometry.Size(width, height)
    } else {
        Offset(
            scaledHexBounds.left + (scaledHexBounds.width - width) / 2f,
            scaledHexBounds.top + (scaledHexBounds.height - height) / 2f
        ) to androidx.compose.ui.geometry.Size(width, height)
    }
}

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
