package dev.serhiiyaremych.lumina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    onFocusRequested: (Rect) -> Unit = {},
    onVisibleCellsChanged: (List<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia>) -> Unit = {},
    cellFocusListener: CellFocusListener? = null,
    cellFocusConfig: CellFocusConfig = CellFocusConfig(debugLogging = true),
    onClearClickedMedia: () -> Unit = {},
    externalState: MediaHexState? = null,
    significantCells: Set<HexCell> = emptySet()
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

        val gridColor = MaterialTheme.colorScheme.outline             // Subtle and clean normal grid lines
        val selectedColor = MaterialTheme.colorScheme.tertiary       // Vibrant and energetic selected grid lines
        val placeholderColor = MaterialTheme.colorScheme.surfaceVariant // Light and muted placeholder color

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

            // Resolve selectedMedia to AnimatableMediaItem for performance optimization
            val selectedAnimatableItem = selectedMedia?.let { media ->
                // Find the MediaWithPosition for the selected media
                val mediaWithPosition = hexGridLayout.hexCellsWithMedia
                    .flatMap { it.mediaItems }
                    .find { it.media == media }

                // Get or create the AnimatableMediaItem
                mediaWithPosition?.let { state.animationManager.getOrCreateAnimatable(it) }
            }

            // Layer rendering configuration with streaming atlases
            val layerConfig = StreamingMediaLayerConfig(
                hexGridLayout = hexGridLayout,
                hexGridRenderer = hexGridRenderer,
                animationManager = state.animationManager,
                geometryReader = state.geometryReader,
                selectedMedia = selectedMedia,
                selectedAnimatableItem = selectedAnimatableItem,
                streamingAtlases = streamingAtlases,
                zoom = zoom,
                clickedHexCell = state.clickedHexCell,
                bounceAnimationManager = state.bounceAnimationManager,
                significantCells = significantCells
            )

            // Record and draw both content and selected layers
            with(layerManager) {
                recordAndDrawLayers(layerConfig, canvasSize, zoom, offset, gridColor, selectedColor, placeholderColor)
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

