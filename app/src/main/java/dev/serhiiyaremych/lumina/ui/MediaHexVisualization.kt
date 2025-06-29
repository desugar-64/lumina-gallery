package dev.serhiiyaremych.lumina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.Media


@Composable
fun MediaHexVisualization(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    hexGridRenderer: HexGridRenderer,
    provideZoom: () -> Float,
    provideOffset: () -> Offset,
    onMediaClicked: (Media) -> Unit = {},
    onHexCellClicked: (HexCell) -> Unit = {},
    /**
     * Callback when content requests programmatic focus.
     * @param bounds The [Rect] bounds of the content to focus on, in content coordinates.
     *               The transformation system will smoothly center and zoom to these bounds.
     */
    onFocusRequested: (Rect) -> Unit = {},
    /**
     * Callback when visible cells change due to zoom/pan operations.
     * Reports cells that are actually being rendered on screen.
     */
    onVisibleCellsChanged: (List<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia>) -> Unit = {}
) {
    val geometryReader = remember { GeometryReader() }
    var clickedMedia by remember { mutableStateOf<Media?>(null) }
    var clickedHexCell by remember { mutableStateOf<HexCell?>(null) }
    var ripplePosition by remember { mutableStateOf<Offset?>(null) }

    // Capture latest callback to avoid restarting effect when callback changes
    val currentOnVisibleCellsChanged by rememberUpdatedState(onVisibleCellsChanged)

    if (hexGridLayout.hexCellsWithMedia.isEmpty()) return

    LaunchedEffect(hexGridLayout) {
        geometryReader.clear()
        clickedMedia = null
        clickedHexCell = null
    }

    // Monitor zoom/offset changes and report visible cells
    // Use currentOnVisibleCellsChanged as key to restart when callback logic changes
    LaunchedEffect(hexGridLayout, currentOnVisibleCellsChanged) {
        snapshotFlow { 
            provideZoom() to provideOffset() 
        }.collect { (zoom, offset) ->
            val visibleCells = calculateVisibleCells(
                hexGridLayout = hexGridLayout,
                zoom = zoom,
                offset = offset
            )
            currentOnVisibleCellsChanged(visibleCells)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit, provideZoom, provideOffset) {
                detectTapGestures { tapOffset ->
                    val zoom = provideZoom()
                    val offset = provideOffset()
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
        val zoom = provideZoom()
        val offset = provideOffset()
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        withTransform({
            scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
            translate(offset.x / clampedZoom, offset.y / clampedZoom)
        }) {
            // Store hex cell bounds for hit testing (world coordinates, same as before)
            hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                geometryReader.storeHexCellBounds(hexCellWithMedia.hexCell)
            }

            hexGridRenderer.drawHexGrid(
                drawScope = this,
                hexGrid = hexGridLayout.hexGrid,
                zoom = 1f,
                offset = Offset.Zero
            )

            geometryReader.debugDrawHexCellBounds(this)

            // Draw media using pre-computed positions (world coordinates, same as before)
            hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                hexCellWithMedia.mediaItems.forEach { mediaWithPosition ->
                    // Store world coordinates for hit testing (preserves current approach)
                    geometryReader.storeMediaBounds(
                        media = mediaWithPosition.media,
                        bounds = mediaWithPosition.absoluteBounds, // World coordinates
                        hexCell = hexCellWithMedia.hexCell
                    )
                    
                    // TODO: Replace with atlas-based rendering
                    drawRect(
                        color = when (mediaWithPosition.media) {
                            is Media.Image -> Color(0xFF2196F3)
                            is Media.Video -> Color(0xFF4CAF50)
                        },
                        topLeft = mediaWithPosition.absoluteBounds.topLeft,
                        size = mediaWithPosition.absoluteBounds.size
                    )
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

/**
 * Calculates which hex cells are visible in the current viewport.
 * This is called from UI thread and has access to current zoom/offset state.
 */
private fun calculateVisibleCells(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    zoom: Float,
    offset: Offset
): List<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia> {
    // Simple approach: return all cells for now
    // TODO: Implement actual viewport intersection based on zoom/offset
    return hexGridLayout.hexCellsWithMedia
}

