package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp

import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.ui.animation.AnimationConstants
import dev.serhiiyaremych.lumina.ui.components.DirectionalIndicatorOverlay
import dev.serhiiyaremych.lumina.ui.components.FocusedCellPanel
import dev.serhiiyaremych.lumina.ui.components.MediaPermissionFlow
import dev.serhiiyaremych.lumina.ui.debug.EnhancedDebugOverlay
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Selection mode enum to differentiate between cell-level and photo-level selections.
 * Controls whether focus animations should trigger from FocusedCellPanel selections.
 */
enum class SelectionMode {
    /**
     * Cell mode: User selected a cell or selected from panel while in cell mode.
     * Panel selections do NOT trigger focus animations.
     */
    CELL_MODE,

    /**
     * Photo mode: User clicked photo directly on canvas or zoomed in significantly.
     * Panel selections DO trigger focus animations to new photos.
     */
    PHOTO_MODE
}

/**
 * Root composable for the Lumina gallery view with streaming atlas system.
 *
 * Responsibilities:
 * - Initializes and connects StreamingViewModel for media data
 * - Calculates optimal hex grid layout based on media groups
 * - Manages transformation state for zoom/pan interactions
 * - Coordinates between visualization and streaming atlas systems
 *
 * @param modifier Compose modifier for layout
 * @param streamingGalleryViewModel Streaming gallery view model instance
 */
@Composable
fun App(
    modifier: Modifier = Modifier,
    streamingGalleryViewModel: StreamingGalleryViewModel,
    isBenchmarkMode: Boolean = false,
    autoZoom: Boolean = false,
) {
    LuminaGalleryTheme {
        // Single UI state following UDF architecture
        val uiState by streamingGalleryViewModel.uiState.collectAsState()
        val density = LocalDensity.current

        Log.d(
            "StreamingApp",
            "Media loaded: ${uiState.media.size} items, grouped into ${uiState.groupedMedia.size} groups, layout: ${uiState.hexGridLayout?.totalMediaCount ?: 0} positioned"
        )

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
                override fun onCellFocused(hexCellWithMedia: HexCellWithMedia, coverage: Float) {
                    val isManualClick = coverage >= 1.0f // Manual clicks have max coverage (1.0f)
                    Log.d("CellFocus", "Cell FOCUSED: (${hexCellWithMedia.hexCell.q}, ${hexCellWithMedia.hexCell.r}) coverage=${String.format("%.2f", coverage)} manual=$isManualClick")

                    // Update selected cell - this should replace any previous selection
                    streamingGalleryViewModel.updateSelectedCell(hexCellWithMedia.hexCell)

                    // Clear selected media and set cell mode
                    streamingGalleryViewModel.updateSelectedMedia(null)
                    streamingGalleryViewModel.updateSelectionMode(SelectionMode.CELL_MODE)
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

                override fun onCellUnfocused(hexCellWithMedia: HexCellWithMedia) {
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
                override fun onCellSignificant(hexCellWithMedia: HexCellWithMedia, coverage: Float) {
                    cellFocusHandler.onCellFocused(hexCellWithMedia, coverage)
                }

                override fun onCellInsignificant(hexCellWithMedia: HexCellWithMedia) {
                    cellFocusHandler.onCellUnfocused(hexCellWithMedia)
                }
            }
        }

        // Unified viewport state manager - single source of truth for all viewport decisions
        val viewportPaddingPx = with(density) { 32.dp.toPx() }
        val viewportStateManager = remember(viewportPaddingPx) {
            ViewportStateManager(
                offscreenIndicatorManager = OffscreenIndicatorManager(
                    OffscreenIndicatorConfig(
                        viewportPadding = viewportPaddingPx
                    )
                )
            )
        }

        // State to track if content is out of viewport bounds
        var showCenterButton by remember { mutableStateOf(false) }

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

        Box(modifier = modifier.fillMaxSize()) {
            // Show permission flow if permissions are not granted (regardless of loading state)
            if (!uiState.permissionGranted) {
                // Permission flow - shown when permissions are not granted
                MediaPermissionFlow(
                    onPermissionGranted = { streamingGalleryViewModel.updatePermissionGranted(true) },
                    onPermissionDenied = {
                        Log.w("StreamingApp", "Media permissions denied")
                    }
                )
            } else if (uiState.permissionGranted) {
                // Main gallery interface - shown only when permissions are granted
                BoxWithConstraints {
                    val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

                    // Unified viewport state monitoring
                    val zoomProvider = rememberUpdatedState { transformableState.zoom }
                    val offsetProvider = rememberUpdatedState { transformableState.offset }

                    LaunchedEffect(canvasSize, uiState.hexGridLayout) {
                        snapshotFlow {
                            Triple(zoomProvider.value(), offsetProvider.value(), uiState.hexGridLayout)
                        }
                        .distinctUntilChanged()
                        .collect { (currentZoom, currentOffset, layout) ->
                            // Calculate viewport state once and reuse
                            val viewportState = viewportStateManager.calculateViewportState(
                                canvasSize = canvasSize,
                                zoom = currentZoom,
                                offset = currentOffset,
                                focusedCell = uiState.focusedCellWithMedia,
                                selectedMedia = uiState.selectedMedia,
                                currentSelectionMode = uiState.selectionMode,
                                gridBounds = layout?.bounds
                            )

                            // Update center button visibility - show when grid is outside viewport bounds
                            layout?.let { hexLayout ->
                                val viewportRect = viewportState.viewportRect
                                val gridBounds = hexLayout.bounds

                                // Use Rect.overlaps for efficient intersection check
                                val hasOverlap = viewportRect.overlaps(gridBounds)

                                // Show center button only when grid content is outside viewport bounds (no overlap)
                                showCenterButton = !hasOverlap
                            }

                            // Apply selection mode changes
                            if (viewportState.suggestedSelectionMode != uiState.selectionMode) {
                                streamingGalleryViewModel.updateSelectionMode(viewportState.suggestedSelectionMode)
                                Log.d("StreamingApp", "Selection mode changed: ${viewportState.suggestedSelectionMode} (viewport-based)")
                            }

                            // Apply media deselection when out of viewport
                            if (viewportState.shouldDeselectMedia && uiState.selectedMedia != null) {
                                streamingGalleryViewModel.updateSelectedMedia(null)
                                streamingGalleryViewModel.updateSelectionMode(SelectionMode.CELL_MODE)
                                Log.d("StreamingApp", "Media deselected: out of viewport")
                            }
                        }
                    }

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

                    TransformableContent(
                        modifier = Modifier.fillMaxSize(),
                        state = transformableState
                    ) {
                        GridCanvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics {
                                    testTagsAsResourceId = true
                                    contentDescription = "Streaming gallery canvas"
                                }
                                .testTag(BenchmarkLabels.GALLERY_CANVAS_TEST_TAG),
                            zoom = transformableState.zoom,
                            offset = transformableState.offset,
                            state = gridState
                        ) {
                            // Draw media visualization with streaming atlas
                            uiState.hexGridLayout?.let { layout ->
                                mediaHexState?.let { state ->
                                    // Debug: Log what atlases we're passing to the UI
                                    Log.d("StreamingApp", "Passing to UI: ${uiState.availableAtlases.keys.map { "${it.name}(${uiState.availableAtlases[it]?.size ?: 0})" }}")

                                    MediaHexVisualization(
                                        hexGridLayout = layout,
                                        hexGridRenderer = hexGridRenderer,
                                        provideZoom = { transformableState.zoom },
                                        provideOffset = { transformableState.offset },
                                        streamingAtlases = uiState.availableAtlases,
                                        selectedMedia = uiState.selectedMedia,
                                        onMediaClicked = { media ->
                                            Log.d("StreamingApp", "Media clicked: ${media.displayName}")
                                            if (uiState.selectedMedia == media) {
                                                Log.d("StreamingApp", "Deselecting media: ${media.displayName}")
                                                streamingGalleryViewModel.updateSelectionMode(SelectionMode.CELL_MODE)
                                                streamingGalleryViewModel.updateSelectedMedia(null)
                                            } else {
                                                streamingGalleryViewModel.updateSelectionMode(SelectionMode.PHOTO_MODE)
                                                streamingGalleryViewModel.updateSelectedMedia(media)
                                                Log.d("StreamingApp", "Selection mode: PHOTO_MODE (canvas click)")
                                            }
                                        },
                                        onHexCellClicked = { hexCell ->
                                            Log.d("StreamingApp", "Hex cell clicked: (${hexCell.q}, ${hexCell.r})")

                                            // Clear selected media immediately when any hex cell is clicked
                                            streamingGalleryViewModel.updateSelectedMedia(null)
                                            streamingGalleryViewModel.updateSelectionMode(SelectionMode.CELL_MODE)
                                            Log.d("StreamingApp", "Cleared selected media on hex cell click")

                                            // Find the HexCellWithMedia for this clicked cell
                                            layout.hexCellsWithMedia.find { it.hexCell == hexCell }?.let { hexCellWithMedia ->
                                                cellFocusHandler.onCellFocused(hexCellWithMedia, 1.0f) // Max coverage for manual clicks
                                            }
                                        },
                                        onFocusRequested = { bounds ->
                                            Log.d("StreamingApp", "Focus requested: $bounds")
                                            coroutineScope.launch {
                                                // Canvas focus uses small padding in PHOTO_MODE, larger padding in CELL_MODE
                                                val padding = if (uiState.selectionMode == SelectionMode.PHOTO_MODE) 4.dp else 64.dp
                                                transformableState.focusOn(bounds, padding = padding)
                                            }
                                        },
                                        onVisibleCellsChanged = {}, // Handled by mediaHexState
                                        cellFocusListener = cellFocusListener,
                                        onClearClickedMedia = {
                                            Log.d("CellFocus", "Clearing clicked media state for red outline")
                                        },
                                        externalState = state,
                                        significantCells = uiState.selectedCell?.let { setOf(it) } ?: emptySet()
                                    )
                                }
                            }
                        }
                    }

                    // Debug overlay with streaming info
                    EnhancedDebugOverlay(
                        atlasState = null, // Legacy atlas state (not used with streaming)
                        isAtlasGenerating = uiState.isAtlasGenerating,
                        currentZoom = transformableState.zoom,
                        memoryStatus = null, // TODO: Get memory status from streaming manager
                        smartMemoryManager = streamingGalleryViewModel.getStreamingAtlasManager().getSmartMemoryManager(),
                        deviceCapabilities = null, // TODO: Add device capabilities to streaming system
                        significantCells = uiState.selectedCell?.let { setOf(it) } ?: emptySet(),
                        streamingAtlases = uiState.availableAtlases,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Focused cell panel with conditional focus animations
                    uiState.focusedCellWithMedia?.let { cellWithMedia ->
                        mediaHexState?.let { state ->
                            FocusedCellPanel(
                                hexCellWithMedia = cellWithMedia,
                                level0Atlases = uiState.availableAtlases[dev.serhiiyaremych.lumina.domain.model.LODLevel.LEVEL_0],
                                selectionMode = uiState.selectionMode,
                                selectedMedia = uiState.selectedMedia,
                                onDismiss = { streamingGalleryViewModel.updateFocusedCell(null) },
                                onMediaSelected = { media ->
                                    streamingGalleryViewModel.updateSelectedMedia(media)
                                    // Panel selections preserve current mode - don't change it
                                    Log.d("App", "Panel selection: ${media.displayName}, mode: ${uiState.selectionMode}")
                                },
                                onFocusRequested = { bounds ->
                                    Log.d("App", "Panel focus requested: $bounds")
                                    coroutineScope.launch {
                                        // Panel focus uses minimal padding for close photo viewing
                                        transformableState.focusOn(bounds, padding = 4.dp)
                                    }
                                },
                                getMediaBounds = { media ->
                                    state.geometryReader.getMediaBounds(media)
                                },
                                provideTranslationOffset = { panelSize ->
                                    calculateCellPanelPosition(
                                        hexCell = cellWithMedia.hexCell,
                                        zoom = transformableState.zoom,
                                        offset = transformableState.offset,
                                        canvasSize = canvasSize,
                                        panelSize = panelSize
                                    )
                                },
                                modifier = Modifier
                            )
                        }
                    }

                     if (showCenterButton) {
                         FloatingActionButton(
                             onClick = {
                                 Log.d("CenterButton", "Tracking button clicked")
                                 uiState.hexGridLayout?.let { layout ->
                                     // Calculate the bounds that encompass all hex cells
                                     val gridBounds = layout.bounds

                                     Log.d("CenterButton", "Grid bounds: $gridBounds, isEmpty: ${gridBounds.isEmpty}")

                                     // Safety check to ensure we have valid bounds and no animation is in progress
                                     if (!gridBounds.isEmpty && !transformableState.isAnimating) {
                                         // Close panel first to avoid conflicts
                                         streamingGalleryViewModel.updateFocusedCell(null)

                                         // Use focusOn with padding to center and fit the entire grid
                                         coroutineScope.launch {
                                             Log.d("CenterButton", "Launching focusOn animation")
                                             // Stop any ongoing fling animation before starting focus animation
                                             transformableState.stopAllAnimations()
                                             transformableState.focusOn(gridBounds, padding = 32.dp)
                                             Log.d("CenterButton", "FocusOn animation completed")
                                         }
                                     } else {
                                         Log.w("CenterButton", "Skipping focusOn: bounds empty=${gridBounds.isEmpty}, animating=${transformableState.isAnimating}")
                                     }
                                 } ?: Log.w("CenterButton", "Skipping focusOn due to null layout")
                             },
                             modifier = Modifier
                                 .align(Alignment.BottomEnd)
                                 .padding(16.dp),
                             containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                             contentColor = MaterialTheme.colorScheme.onSurface
                         ) {
                             Text(
                                 text = "🎯",
                                 style = MaterialTheme.typography.headlineSmall
                             )
                         }
                     }

                    // Store current viewport state for indicator access
                    val currentViewportState = remember { mutableStateOf<ViewportState?>(null) }

                    LaunchedEffect(canvasSize, uiState.hexGridLayout) {
                        snapshotFlow {
                            Triple(zoomProvider.value(), offsetProvider.value(), uiState.hexGridLayout)
                        }
                        .distinctUntilChanged()
                        .collect { (currentZoom, currentOffset, layout) ->
                            if (layout != null) {
                                currentViewportState.value = viewportStateManager.calculateViewportState(
                                    canvasSize = canvasSize,
                                    zoom = currentZoom,
                                    offset = currentOffset,
                                    focusedCell = uiState.focusedCellWithMedia,
                                    selectedMedia = uiState.selectedMedia,
                                    currentSelectionMode = uiState.selectionMode,
                                    gridBounds = layout.bounds
                                )
                            }
                        }
                    }

                    // Directional indicators for offscreen content
                    currentViewportState.value?.let { viewportState ->
                        DirectionalIndicatorOverlay(
                            indicators = viewportState.offscreenIndicators,
                            onIndicatorClick = { indicator ->
                                Log.d("DirectionalIndicator", "Indicator clicked for bounds: ${indicator.contentBounds}")
                                // Close panel first to avoid conflicts
                                streamingGalleryViewModel.updateFocusedCell(null)

                                // Navigate to the offscreen content
                                if (!transformableState.isAnimating) {
                                    coroutineScope.launch {
                                        transformableState.stopAllAnimations()
                                        transformableState.focusOn(indicator.contentBounds, padding = 32.dp)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}



/**
 * Calculates the translation offset for positioning a panel relative to a hex cell.
 * Panel is positioned below the cell's bottom edge, centered horizontally on screen.
 * Clamps panel position to stay within viewport bounds on all sides.
 */
private fun calculateCellPanelPosition(
    hexCell: dev.serhiiyaremych.lumina.domain.model.HexCell,
    zoom: Float,
    offset: Offset,
    canvasSize: Size,
    panelSize: Size,
    viewportRect: androidx.compose.ui.geometry.Rect? = null
): Offset {
    // Calculate cell bounds from vertices
    val vertices = hexCell.vertices
    val minX = vertices.minOf { it.x }
    val maxX = vertices.maxOf { it.x }
    val minY = vertices.minOf { it.y }
    val maxY = vertices.maxOf { it.y }
    val cellBounds = androidx.compose.ui.geometry.Rect(minX, minY, maxX, maxY)

    // Transform cell bottom position to screen coordinates
    val screenX = (cellBounds.center.x * zoom) + offset.x
    val screenY = (cellBounds.bottom * zoom) + offset.y

    // Use provided viewport or create default from canvas size
    val viewport = viewportRect ?: androidx.compose.ui.geometry.Rect(
        offset = Offset.Zero,
        size = canvasSize
    )

    // Calculate initial panel position
    val initialX = screenX - panelSize.width / 2 // Center panel horizontally relative to cell
    val initialY = screenY // Position panel top edge at cell bottom edge

    // Clamp panel horizontally (left and right)
    val panelLeft = initialX
    val panelRight = initialX + panelSize.width
    val clampedX = when {
        panelLeft < viewport.left -> viewport.left
        panelRight > viewport.right -> viewport.right - panelSize.width
        else -> initialX
    }

    // Clamp panel vertically (bottom)
    val panelBottom = initialY + panelSize.height
    val clampedY = if (panelBottom > viewport.bottom) {
        viewport.bottom - panelSize.height
    } else {
        initialY
    }

    return Offset(
        x = clampedX,
        y = clampedY
    )
}


