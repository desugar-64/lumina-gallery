package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.serhiiyaremych.lumina.ui.calculateCellFocusBounds
import dev.serhiiyaremych.lumina.ui.CellFocusHandler
import dev.serhiiyaremych.lumina.ui.CellFocusListener
import dev.serhiiyaremych.lumina.ui.HexGridRenderer
import dev.serhiiyaremych.lumina.ui.gallery.GalleryUiState
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import kotlinx.coroutines.launch

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

    // Unified cell focus handler for both clicks and automatic detection
    val cellFocusHandler = remember(coroutineScope, transformableState) {
        object : CellFocusHandler {
            override fun onCellFocused(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia, coverage: Float) {
                val isManualClick = coverage >= 1.0f // Manual clicks have max coverage (1.0f)
                Log.d("CellFocus", "Cell FOCUSED: (${hexCellWithMedia.hexCell.q}, ${hexCellWithMedia.hexCell.r}) coverage=${String.format("%.2f", coverage)} manual=$isManualClick")

                // Update selected cell - this should replace any previous selection
                streamingGalleryViewModel.updateSelectedCell(hexCellWithMedia.hexCell)

                // Clear selected media and set cell mode
                streamingGalleryViewModel.updateSelectedMedia(null)
                streamingGalleryViewModel.updateSelectionMode(dev.serhiiyaremych.lumina.ui.SelectionMode.CELL_MODE)
                Log.d("CellFocus", "Selection mode: CELL_MODE (focused cell, manual=$isManualClick)")

                // Show focused cell panel
                streamingGalleryViewModel.updateFocusedCell(hexCellWithMedia)

                // Trigger focus animation for both manual clicks AND auto-detection
                val cellBounds = calculateCellFocusBounds(hexCellWithMedia.hexCell)
                Log.d("CellFocus", "Triggering focus animation to bounds: $cellBounds (${if (isManualClick) "manual click" else "auto-detection"})")
                coroutineScope.launch {
                    // Cell focus uses larger padding for comfortable cell viewing
                    transformableState.focusOn(cellBounds, padding = 64.dp)
                }
            }

            override fun onCellUnfocused(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia) {
                val hexCell = hexCellWithMedia.hexCell
                Log.d("CellFocus", "Cell UNFOCUSED: (${hexCell.q}, ${hexCell.r})")

                // Clear selected cell if this was the selected cell
                if (uiState.selectedCell == hexCell) {
                    streamingGalleryViewModel.updateSelectedCell(null)
                }

                // Hide focused cell panel if this was the focused cell
                if (uiState.focusedCellWithMedia?.hexCell == hexCell) {
                    streamingGalleryViewModel.updateFocusedCell(null)
                }
            }
        }
    }

    // Cell focus listener adapter for the existing CellFocusManager interface
    val cellFocusListener = remember(cellFocusHandler) {
        object : CellFocusListener {
            override fun onCellSignificant(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia, coverage: Float) {
                cellFocusHandler.onCellFocused(hexCellWithMedia, coverage)
            }

            override fun onCellInsignificant(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia) {
                cellFocusHandler.onCellUnfocused(hexCellWithMedia)
            }
        }
    }

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
                        activeCell = uiState.focusedCellWithMedia
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
                    activeCell = uiState.focusedCellWithMedia
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
