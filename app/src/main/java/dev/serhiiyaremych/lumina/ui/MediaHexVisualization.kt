package dev.serhiiyaremych.lumina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult


@Composable
fun MediaHexVisualization(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    hexGridRenderer: HexGridRenderer,
    provideZoom: () -> Float,
    provideOffset: () -> Offset,
    atlasState: MultiAtlasUpdateResult? = null,
    selectedMedia: Media? = null,
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
    if (hexGridLayout.hexCellsWithMedia.isEmpty()) return

    // State management - handles all LaunchedEffects, animation management, and state coordination
    val state = rememberMediaHexState(
        hexGridLayout = hexGridLayout,
        selectedMedia = selectedMedia,
        onVisibleCellsChanged = onVisibleCellsChanged,
        provideZoom = provideZoom,
        provideOffset = provideOffset
    )

    // Layer management - handles GraphicsLayer creation and recording
    val layerManager = rememberMediaLayers()

    // Input handling configuration
    val inputConfig = MediaInputConfig(
        hexGridLayout = hexGridLayout,
        animationManager = state.animationManager,
        geometryReader = state.geometryReader,
        provideZoom = provideZoom,
        provideOffset = provideOffset,
        onMediaClicked = onMediaClicked,
        onHexCellClicked = onHexCellClicked,
        onFocusRequested = onFocusRequested,
        onRipplePosition = state.setRipplePosition,
        onClickedMedia = state.setClickedMedia,
        onClickedHexCell = state.setClickedHexCell,
        onRevealAnimationTarget = state.setRevealAnimationTarget
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .mediaHexInput(inputConfig) // Clean input handling via modifier extension
    ) {
        val zoom = provideZoom()
        val offset = provideOffset()
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        val canvasSize = IntSize(size.width.toInt(), size.height.toInt())

        // Layer rendering configuration
        val layerConfig = MediaLayerConfig(
            hexGridLayout = hexGridLayout,
            hexGridRenderer = hexGridRenderer,
            animationManager = state.animationManager,
            geometryReader = state.geometryReader,
            selectedMedia = selectedMedia,
            atlasState = atlasState,
            zoom = zoom
        )

        // Record and draw both content and selected layers
        with(layerManager) {
            recordAndDrawLayers(layerConfig, canvasSize, zoom, offset)
        }

        // Draw debug overlays and UI elements on top
        withTransform({
            scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
            translate(offset.x / clampedZoom, offset.y / clampedZoom)
        }) {
            // Debug outline for clicked media
            state.clickedMedia?.let { media ->
                state.geometryReader.getMediaBounds(media)?.let { bounds ->
                    drawRect(
                        color = Color.Red,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        style = Stroke(width = 1.dp.toPx() / zoom)
                    )
                }
            }

            // Debug outline for clicked hex cell
            state.clickedHexCell?.let { hexCell ->
                state.geometryReader.getHexCellBounds(hexCell)?.let { bounds ->
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
                        style = Stroke(width = 1.dp.toPx() / zoom)
                    )
                }
            }
        }

        // Ripple effect for taps
        state.ripplePosition?.let {
            val color = when {
                state.clickedMedia != null -> Color.Yellow
                state.clickedHexCell != null -> Color.Green
                else -> Color.Gray
            }
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = 30f,
                center = it,
                style = Stroke(width = 3f)
            )
            state.setRipplePosition(null)
        }
    }
}

