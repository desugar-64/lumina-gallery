package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.ui.OffscreenIndicatorManager
import dev.serhiiyaremych.lumina.ui.OffscreenIndicatorConfig
import dev.serhiiyaremych.lumina.ui.SelectionMode
import dev.serhiiyaremych.lumina.ui.SimpleViewportConfig
import dev.serhiiyaremych.lumina.ui.rememberGridCanvasState
import dev.serhiiyaremych.lumina.ui.rememberMediaHexState
import dev.serhiiyaremych.lumina.ui.rememberTransformableState
import dev.serhiiyaremych.lumina.ui.animation.AnimationConstants
import dev.serhiiyaremych.lumina.ui.CellFocusHandler
import dev.serhiiyaremych.lumina.ui.CellFocusListener
import dev.serhiiyaremych.lumina.ui.HexGridRenderer
import dev.serhiiyaremych.lumina.ui.gallery.GalleryUiState
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel

@Composable
fun GalleryContent(
    uiState: GalleryUiState,
    streamingGalleryViewModel: StreamingGalleryViewModel,
    isBenchmarkMode: Boolean,
    autoZoom: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val gridState = rememberGridCanvasState()
    val transformableState = rememberTransformableState(
        animationSpec = tween(durationMillis = AnimationConstants.ANIMATION_DURATION_MS),
        focusPadding = 64.dp // Extra padding for streaming gallery - provides breathing room on large displays
    )
    val hexGridRenderer = remember { HexGridRenderer() }
    val coroutineScope = rememberCoroutineScope()

    // State for cell focus events
    val cellFocusEvents = remember { mutableStateListOf<CellFocusEvent>() }

    // Unified cell focus handler for both clicks and automatic detection
    val cellFocusHandler = remember(coroutineScope, transformableState) {
        object : CellFocusHandler {
            override fun onCellFocused(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia, coverage: Float) {
                // Add focus event to be handled by effect handler
                cellFocusEvents.add(CellFocusEvent(CellFocusEventType.CELL_FOCUSED, hexCellWithMedia, coverage))
            }

            override fun onCellUnfocused(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia) {
                // Unfocusing is now handled by ViewportStateManager - no event needed
                Log.d("CellFocus", "Cell unfocus event ignored - handled by ViewportStateManager")
            }
        }
    }

    // Cell focus listener adapter for the existing CellFocusManager interface
    val cellFocusListener = remember(cellFocusHandler) {
        object : CellFocusListener {
            override fun onCellSignificant(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia, coverage: Float) {
                // Auto-focus events are still needed - only disable unfocus events
                cellFocusHandler.onCellFocused(hexCellWithMedia, coverage)
            }

            override fun onCellInsignificant(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia) {
                // Unfocusing is now handled by ViewportStateManager - no need to call handler
                Log.d("CellFocus", "Cell insignificant event ignored - handled by ViewportStateManager")
            }
        }
    }

    // Handle cell focus effects
    HandleCellFocusEffects(
        cellFocusEvents = cellFocusEvents.toList(),
        streamingGalleryViewModel = streamingGalleryViewModel,
        transformableState = transformableState,
        onEventsHandled = { cellFocusEvents.clear() }
    )

    // Viewport configuration
    val viewportConfig = remember { SimpleViewportConfig() }
    val viewportPaddingPx = with(density) { 32.dp.toPx() }
    val offscreenIndicatorManager = remember(viewportPaddingPx) {
        OffscreenIndicatorManager(
            OffscreenIndicatorConfig(
                viewportPadding = viewportPaddingPx
            )
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        // Create media hex state for streaming system
        val mediaHexState = uiState.hexGridLayout?.let { layout ->
            rememberMediaHexState(
                hexGridLayout = layout,
                selectedMedia = uiState.selectedMedia,
                onVisibleCellsChanged = { visibleCells ->
                    Log.d("StreamingApp", "Visible cells changed: ${visibleCells.size} cells")
                    streamingGalleryViewModel.onVisibleCellsChanged(
                        visibleCells = visibleCells,
                        currentZoom = transformableState.zoom,
                        selectedMedia = uiState.selectedMedia,
                        selectionMode = uiState.selectionMode,
                        activeCell = uiState.selectedCellWithMedia
                    )
                },
                provideZoom = { transformableState.zoom },
                provideOffset = { transformableState.offset }
            )
        }

        // Viewport state management
        val simpleViewportState = viewportStateManager(
            uiState = uiState,
            canvasSize = canvasSize,
            zoom = transformableState.zoom,
            offset = transformableState.offset,
            viewportConfig = viewportConfig,
            onSelectionModeChanged = { mode -> streamingGalleryViewModel.updateSelectionMode(mode) },
            onMediaDeselected = {
                streamingGalleryViewModel.updateSelectedMedia(null)
                streamingGalleryViewModel.updateSelectionMode(SelectionMode.CELL_MODE)
            },
            onCellUnfocused = {
                // Clear selected cell when viewport unfocuses (unified state)
                streamingGalleryViewModel.updateSelectedCellWithMedia(null)
            }
        )

        // Derived state for center button visibility
        val showCenterButton = simpleViewportState?.let { state ->
            state.gridBounds?.let { gridBounds ->
                !state.viewportRect.overlaps(gridBounds)
            } ?: false
        } ?: false

        // Generate hex grid layout when canvas size is available
        LaunchedEffect(canvasSize, uiState.groupedMedia) {
            if (uiState.groupedMedia.isNotEmpty()) {
                streamingGalleryViewModel.generateHexGridLayout(density, canvasSize)
            }
        }

        // Center the view on the grid when layout is first generated
        LaunchedEffect(uiState.hexGridLayout) {
            uiState.hexGridLayout?.let { layout ->
                if (layout.hexCellsWithMedia.isNotEmpty()) {
                    // Center on the grid bounds
                    val gridCenter = layout.bounds.center
                    val canvasCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                    val centerOffset = canvasCenter - gridCenter

                    Log.d("StreamingApp", "Centering grid: gridCenter=$gridCenter, canvasCenter=$canvasCenter, offset=$centerOffset")
                    transformableState.updateMatrix {
                        reset()
                        postScale(1f, 1f)
                        postTranslate(centerOffset.x, centerOffset.y)
                    }
                }
            }
        }

        // Transformable gallery canvas
        TransformableGalleryCanvas(
            uiState = uiState,
            streamingGalleryViewModel = streamingGalleryViewModel,
            transformableState = transformableState,
            gridState = gridState,
            hexGridRenderer = hexGridRenderer,
            cellFocusHandler = cellFocusHandler,
            cellFocusListener = cellFocusListener,
            coroutineScope = coroutineScope,
            mediaHexState = mediaHexState,
            onVisibleCellsChanged = { visibleCells ->
                streamingGalleryViewModel.onVisibleCellsChanged(
                    visibleCells = visibleCells,
                    currentZoom = transformableState.zoom,
                    selectedMedia = uiState.selectedMedia,
                    selectionMode = uiState.selectionMode,
                    activeCell = uiState.selectedCellWithMedia
                )
            }
        )

        // Debug overlay with streaming info
        GalleryDebugOverlay(
            uiState = uiState,
            streamingGalleryViewModel = streamingGalleryViewModel,
            currentZoom = transformableState.zoom
        )

        // Focused cell panel with conditional focus animations
        GalleryFocusedCellPanel(
            uiState = uiState,
            streamingGalleryViewModel = streamingGalleryViewModel,
            transformableState = transformableState,
            coroutineScope = coroutineScope,
            canvasSize = canvasSize,
            mediaHexState = mediaHexState
        )

        // Navigation controls
        GalleryNavigationControls(
            uiState = uiState,
            streamingGalleryViewModel = streamingGalleryViewModel,
            transformableState = transformableState,
            coroutineScope = coroutineScope,
            showCenterButton = showCenterButton
        )

        // Directional indicators for offscreen content
        GalleryDirectionalIndicators(
            simpleViewportState = simpleViewportState,
            offscreenIndicatorManager = offscreenIndicatorManager,
            streamingGalleryViewModel = streamingGalleryViewModel,
            transformableState = transformableState,
            coroutineScope = coroutineScope
        )
    }
}
