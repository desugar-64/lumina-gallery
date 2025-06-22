package dev.serhiiyaremych.lumina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
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
    offset: Offset,
    onMediaClicked: (Media) -> Unit = {},
    onHexCellClicked: (HexCell) -> Unit = {},
    /**
     * Callback when content requests programmatic focus.
     * @param bounds The [Rect] bounds of the content to focus on, in content coordinates.
     *               The transformation system will smoothly center and zoom to these bounds.
     */
    onFocusRequested: (Rect) -> Unit = {}
) {
    val density = LocalDensity.current
    val geometryReader = remember { GeometryReader() }
    var clickedMedia by remember { mutableStateOf<Media?>(null) }
    var clickedHexCell by remember { mutableStateOf<HexCell?>(null) }
    var ripplePosition by remember { mutableStateOf<Offset?>(null) }

    if (groupedMedia.isEmpty()) return

    LaunchedEffect(hexGridSize, hexCellSize) {
        geometryReader.clear()
        clickedMedia = null
        clickedHexCell = null
    }

    val sortedGroups = groupedMedia.keys.sorted()
    val grid = remember(hexGridSize, hexCellSize, density) {
        hexGridGenerator.generateGrid(
            gridSize = hexGridSize,
            cellSizeDp = hexCellSize,
            density = density
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit, zoom, offset) {
                detectTapGestures { tapOffset ->
                    val clampedZoom = zoom.coerceIn(0.01f, 100f)
                    val transformedPos = Offset(
                        (tapOffset.x - offset.x) / clampedZoom,
                        (tapOffset.y - offset.y) / clampedZoom
                    )

                    ripplePosition = tapOffset

                    geometryReader.getMediaAtPosition(transformedPos)?.let { media ->
                        onMediaClicked(media)
                        clickedMedia = media
                        clickedHexCell = null
                        // Trigger focus request
                        geometryReader.getMediaBounds(media)?.let { bounds ->
                            onFocusRequested(bounds)
                        }
                    } ?: run {
                        geometryReader.getHexCellAtPosition(transformedPos)?.let { cell ->
                            onHexCellClicked(cell)
                            clickedHexCell = cell
                            clickedMedia = null
                            // Trigger focus request
                            geometryReader.getHexCellBounds(cell)?.let { bounds ->
                                onFocusRequested(bounds)
                            }
                        }
                    }
                }
            }
    ) {
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        withTransform({
            scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
            translate(offset.x / clampedZoom, offset.y / clampedZoom)
        }) {
            grid.cells.forEach { hexCell ->
                geometryReader.storeHexCellBounds(hexCell)
            }

            hexGridRenderer.drawHexGrid(
                drawScope = this,
                hexGrid = grid,
                zoom = 1f,
                offset = Offset.Zero
            )

            geometryReader.debugDrawHexCellBounds(this)

            sortedGroups.forEachIndexed { index, date ->
                val mediaList = groupedMedia[date] ?: return@forEachIndexed
                if (index < grid.cells.size) {
                    val hexCell = grid.cells[index]
                    drawMediaInHexCell(
                        hexCell = hexCell,
                        mediaList = mediaList,
                        offset = Offset.Zero
                    ).forEach { (media, bounds) ->
                        geometryReader.storeMediaBounds(media, bounds, hexCell)
                    }
                }
            }

            clickedMedia?.let { media ->
                geometryReader.getMediaBounds(media)?.let { bounds ->
                    drawRect(
                        color = Color.Yellow.copy(alpha = 0.5f),
                        topLeft = bounds.topLeft,
                        size = bounds.size
                    )
                    drawRect(
                        color = Color.Red,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        style = Stroke(width = 4f)
                    )
                }
            }

            clickedHexCell?.let { hexCell ->
                geometryReader.getHexCellBounds(hexCell)?.let { bounds ->
                    drawPath(
                        path = Path().apply {
                            hexCell.vertices.firstOrNull()?.let { first ->
                                moveTo(first.x, first.y)
                            }
                            hexCell.vertices.forEach { vertex ->
                                lineTo(vertex.x, vertex.y)
                            }
                            close()
                        },
                        color = Color.Green.copy(alpha = 0.3f),
                        style = Fill
                    )
                    drawPath(
                        path = Path().apply {
                            hexCell.vertices.firstOrNull()?.let { first ->
                                moveTo(first.x, first.y)
                            }
                            hexCell.vertices.forEach { vertex ->
                                lineTo(vertex.x, vertex.y)
                            }
                            close()
                        },
                        color = Color.Green,
                        style = Stroke(width = 4f)
                    )
                }
            }
        }

        ripplePosition?.let {
            val color = when {
                clickedMedia != null -> Color.Yellow
                clickedHexCell != null -> Color.Green
                else -> Color.Gray
            }
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = 30f,
                center = it,
                style = Stroke(width = 3f)
            )
            ripplePosition = null
        }
    }
}

private fun DrawScope.drawMediaInHexCell(
    hexCell: HexCell,
    mediaList: List<Media>,
    offset: Offset
): List<Pair<Media, Rect>> = mediaList.map { media ->
    val scaledHexBounds = calculateHexBounds(hexCell)
    val minDimension = min(scaledHexBounds.width, scaledHexBounds.height)
    val thumbnailMaxSize = minDimension * 0.4f

    val (position, size) = generateScaledPositionWithOffset(
        media = media,
        scaledHexBounds = scaledHexBounds,
        thumbnailMaxSize = thumbnailMaxSize,
        seed = (media.id + hexCell.q * 1000000 + hexCell.r * 1000).toInt()
    )

    val bounds = Rect(
        left = position.x + offset.x,
        top = position.y + offset.y,
        right = position.x + offset.x + size.width,
        bottom = position.y + offset.y + size.height
    )

    drawRect(
        color = when (media) {
            is Media.Image -> Color(0xFF2196F3)
            is Media.Video -> Color(0xFF4CAF50)
        },
        topLeft = position,
        size = size
    )

    media to bounds
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

    return if (availableWidth > 0 && availableHeight > 0) {
        Offset(
            x = scaledHexBounds.left + random.nextFloat() * availableWidth,
            y = scaledHexBounds.top + random.nextFloat() * availableHeight
        ) to androidx.compose.ui.geometry.Size(width, height)
    } else {
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
