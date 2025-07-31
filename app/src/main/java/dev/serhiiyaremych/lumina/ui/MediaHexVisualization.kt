package dev.serhiiyaremych.lumina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.Media
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MediaHexVisualization(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    hexGridRenderer: HexGridRenderer,
    provideZoom: () -> Float,
    provideOffset: () -> Offset,
    streamingAtlases: Map<dev.serhiiyaremych.lumina.domain.model.LODLevel, List<dev.serhiiyaremych.lumina.domain.model.TextureAtlas>>? = null,
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
    onVisibleCellsChanged: (List<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia>) -> Unit = {},
    /**
     * Optional cell focus listener for significant/insignificant cell focus events.
     */
    cellFocusListener: CellFocusListener? = null,
    /**
     * Configuration for cell focus detection.
     */
    cellFocusConfig: CellFocusConfig = CellFocusConfig(debugLogging = true),
    /**
     * Callback to clear clicked media state (for debug outline).
     */
    onClearClickedMedia: () -> Unit = {},
    /**
     * Optional external state to use instead of creating internal state.
     * When provided, MediaHexVisualization will use this state instead of creating its own.
     * This allows sharing the GeometryReader with external components like FocusedCellPanel.
     */
    externalState: MediaHexState? = null
) {
    if (hexGridLayout.hexCellsWithMedia.isEmpty()) return

    // Cell focus management
    val coroutineScope = rememberCoroutineScope()
    val cellFocusManager = remember(cellFocusListener, coroutineScope, cellFocusConfig) {
        cellFocusListener?.let { listener ->
            CellFocusManager(
                config = cellFocusConfig,
                scope = coroutineScope,
                listener = listener
            )
        }
    }

    // State management - use external state if provided, otherwise create internal state
    val state = externalState ?: rememberMediaHexState(
        hexGridLayout = hexGridLayout,
        selectedMedia = selectedMedia,
        onVisibleCellsChanged = onVisibleCellsChanged,
        provideZoom = provideZoom,
        provideOffset = provideOffset
    )

    // Layer management - handles GraphicsLayer creation and recording
    val layerManager = rememberMediaLayers()

    // Animate desaturation effect when selection changes
    LaunchedEffect(selectedMedia) {
        layerManager.animateDesaturation(selectedMedia)
    }

    // Clear clicked media state when selectedMedia is cleared
    LaunchedEffect(selectedMedia) {
        if (selectedMedia == null) {
            state.setClickedMedia(null)
            onClearClickedMedia()
        }
    }


    // Input handling configuration
    val inputConfig = state.toInputConfig(
        hexGridLayout = hexGridLayout,
        provideZoom = provideZoom,
        provideOffset = provideOffset,
        selectedMedia = selectedMedia,
        onMediaClicked = onMediaClicked,
        onHexCellClicked = onHexCellClicked,
        onFocusRequested = onFocusRequested,
        cellFocusManager = cellFocusManager
    )

    BoxWithConstraints {
        val canvasSize =
            Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        // Stable references to prevent LaunchedEffect restarts
        val zoomProvider = rememberUpdatedState(provideZoom)
        val offsetProvider = rememberUpdatedState(provideOffset)

        // Monitor gesture changes with actual canvas size
        LaunchedEffect(cellFocusManager, canvasSize, zoomProvider, offsetProvider) {
            cellFocusManager?.let { mgr ->
                snapshotFlow { zoomProvider.value() to offsetProvider.value() }
                    .distinctUntilChanged()
                    .collect { (zoom, offset) ->
                        mgr.onGestureUpdate(
                            geometryReader = state.geometryReader,
                            contentSize = canvasSize,
                            zoom = zoom,
                            offset = offset,
                            hexCellsWithMedia = hexGridLayout.hexCellsWithMedia
                        )
                    }
            }
        }

        // Material 3 dynamic colors that adapt to wallpaper
        val gridColor = MaterialTheme.colorScheme.outlineVariant      // Lighter, subtle normal grid lines
        val focusedColor = MaterialTheme.colorScheme.secondary  
        val selectedColor = MaterialTheme.colorScheme.primary         // Stronger selected grid lines

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

            // Layer rendering configuration with streaming atlases
            val layerConfig = StreamingMediaLayerConfig(
                hexGridLayout = hexGridLayout,
                hexGridRenderer = hexGridRenderer,
                animationManager = state.animationManager,
                geometryReader = state.geometryReader,
                selectedMedia = selectedMedia,
                streamingAtlases = streamingAtlases,
                zoom = zoom,
                clickedHexCell = state.clickedHexCell,
                bounceAnimationManager = state.bounceAnimationManager
            )

            // Record and draw both content and selected layers
            with(layerManager) {
                recordAndDrawLayers(layerConfig, canvasSize, zoom, offset, gridColor, focusedColor, selectedColor)
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
}

