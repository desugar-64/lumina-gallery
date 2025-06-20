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
    onHexCellClicked: (HexCell) -> Unit = {}
) {
    val density = LocalDensity.current
    val geometryReader = remember { GeometryReader() }
    // Add state for visual feedback
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
            .pointerInput(Unit, zoom, offset) {  // Add zoom and offset as keys
                detectTapGestures { tapOffset ->
                    val clampedZoom = zoom.coerceIn(0.01f, 100f)
                    val transformedPos = if (clampedZoom != 1f || offset != Offset.Zero) {
                        Offset(
                            (tapOffset.x - offset.x) / clampedZoom,
                            (tapOffset.y - offset.y) / clampedZoom
                        )
                    } else {
                        tapOffset // Use raw coordinates when no transform applied
                    }

                    println("=== TAP DEBUG ===")
                    println("Raw tap: $tapOffset")
                    println("After transform: $transformedPos")
                    println("Current offset: $offset")
                    println("Current zoom: $clampedZoom")

                    ripplePosition = tapOffset

                    // Reset both states first
                    clickedMedia = null
                    clickedHexCell = null

                    // Check media first
                    clickedMedia = geometryReader.getMediaAtPosition(transformedPos)?.also {
                        onMediaClicked(it)
                    }

                    // Only check hex cells if no media was clicked
                    if (clickedMedia == null) {
                        clickedHexCell = geometryReader.getHexCellAtPosition(transformedPos)?.also {
                            onHexCellClicked(it)
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
            // Debug draw first cell center
            grid.cells.firstOrNull()?.let { firstCell ->
                drawCircle(
                    color = Color.Red,
                    radius = 10f,
                    center = firstCell.center
                )
                println("First cell center at: ${firstCell.center}")
            }

            grid.cells.forEach { hexCell ->
                geometryReader.storeHexCellBounds(hexCell)
            }

            hexGridRenderer.drawHexGrid(
                drawScope = this,
                hexGrid = grid,
                zoom = 1f,
                offset = Offset.Zero
            )

            // Add debug visualization
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

            // Add visual feedback for selected items
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
                    // Draw over the entire hex area
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
                    // Outline the hex cell
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

        // Draw ripple effect
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
            ripplePosition = null // Clear after drawing
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
