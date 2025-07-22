package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.ui.animation.AnimationConstants
import dev.serhiiyaremych.lumina.ui.components.MediaPermissionFlow
import dev.serhiiyaremych.lumina.ui.debug.EnhancedDebugOverlay
import dev.serhiiyaremych.lumina.ui.gallery.GalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Root composable for the Lumina gallery view.
 *
 * Responsibilities:
 * - Initializes and connects ViewModel for media data
 * - Calculates optimal hex grid layout based on media groups
 * - Manages transformation state for zoom/pan interactions
 * - Coordinates between visualization and transformation systems
 *
 * @param modifier Compose modifier for layout
 * @param galleryViewModel Gallery view model instance (passed from MainActivity)
 */
@Composable
fun App(
    modifier: Modifier = Modifier,
    galleryViewModel: GalleryViewModel,
    isBenchmarkMode: Boolean = false,
    autoZoom: Boolean = false,
) {
    LuminaGalleryTheme {
        val media by galleryViewModel.mediaState.collectAsState()
        val groupedMedia by galleryViewModel.groupedMediaState.collectAsState()
        val hexGridLayout by galleryViewModel.hexGridLayoutState.collectAsState()
        val atlasState by galleryViewModel.atlasState.collectAsState()
        val isAtlasGenerating by galleryViewModel.isAtlasGenerating.collectAsState()
        val memoryStatus by galleryViewModel.memoryStatus.collectAsState()
        val density = LocalDensity.current

        // Permission state management
        var permissionGranted by remember { mutableStateOf(false) }

        var selectedMedia by remember { mutableStateOf<dev.serhiiyaremych.lumina.domain.model.Media?>(null) }
        var significantCells by remember { mutableStateOf(setOf<dev.serhiiyaremych.lumina.domain.model.HexCell>()) }
        var focusedCellWithMedia by remember { mutableStateOf<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia?>(null) }

        // Remember stable cell focus listener
        val cellFocusListener = remember {
            object : CellFocusListener {
                override fun onCellSignificant(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia, coverage: Float) {
                    Log.d("CellFocus", "Cell SIGNIFICANT: (${hexCellWithMedia.hexCell.q}, ${hexCellWithMedia.hexCell.r}) coverage=${String.format("%.2f", coverage)}")
                    significantCells = significantCells + hexCellWithMedia.hexCell

                    // Show focused cell panel
                    focusedCellWithMedia = hexCellWithMedia
                }

                override fun onCellInsignificant(hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia) {
                    val hexCell = hexCellWithMedia.hexCell
                    Log.d("CellFocus", "App callback - Cell INSIGNIFICANT: (${hexCell.q}, ${hexCell.r})")
                    Log.d("CellFocus", "App callback - Before removal: ${significantCells.size} cells")

                    // Clear selection if the current selectedMedia belongs to this cell
                    selectedMedia?.let { currentSelection ->
                        if (hexCellWithMedia.mediaItems.any { it.media == currentSelection }) {
                            Log.d("CellFocus", "Clearing selected media as its cell became insignificant")
                            selectedMedia = null
                        }
                    }

                    significantCells = significantCells - hexCell
                    Log.d("CellFocus", "App callback - After removal: ${significantCells.size} cells")

                    // Hide focused cell panel if this was the focused cell
                    if (focusedCellWithMedia?.hexCell == hexCell) {
                        focusedCellWithMedia = null
                    }
                }
            }
        }

        Log.d(
            "App",
            "Media loaded: ${media.size} items, grouped into ${groupedMedia.size} groups, layout: ${hexGridLayout?.totalMediaCount ?: 0} positioned"
        )

        val gridState = rememberGridCanvasState()
        val transformableState = rememberTransformableState(
            animationSpec = tween(durationMillis = AnimationConstants.ANIMATION_DURATION_MS)
        )
        val hexGridRenderer = remember { HexGridRenderer() }
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = modifier.fillMaxSize()) {
            if (permissionGranted) {
                // Main gallery interface - only shown when permissions are granted
                BoxWithConstraints {
                    val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

                    // Generate hex grid layout when canvas size is available
                    LaunchedEffect(canvasSize, groupedMedia) {
                        if (groupedMedia.isNotEmpty()) {
                            galleryViewModel.generateHexGridLayout(density, canvasSize)
                        }
                    }

                    // Center the view on the grid when layout is first generated
                    LaunchedEffect(hexGridLayout) {
                        hexGridLayout?.let { layout ->
                            if (layout.hexCellsWithMedia.isNotEmpty()) {
                                // Center on the grid bounds
                                val gridCenter = layout.bounds.center
                                val canvasCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                                val centerOffset = canvasCenter - gridCenter

                                Log.d("App", "Centering grid: gridCenter=$gridCenter, canvasCenter=$canvasCenter, offset=$centerOffset")
                                transformableState.updateMatrix {
                                    reset()
                                    postScale(1f, 1f)
                                    postTranslate(centerOffset.x, centerOffset.y)
                                }
                            }
                        }
                    }

                    // Automatic benchmark interactions
                    LaunchedEffect(isBenchmarkMode, autoZoom, hexGridLayout) {
                        if (isBenchmarkMode && hexGridLayout != null) {
                            // Wait for initial atlas generation

                            if (autoZoom) {
                                galleryViewModel.isAtlasGenerating.filter { !it }.first()
                                Log.d("App", "Starting auto zoom benchmark sequence")

                                val centerX = canvasSize.width / 2f
                                val centerY = canvasSize.height / 2f

                                // 1. Zoom out to trigger LOD_0 (32px)
                                Log.d("App", "Auto zoom: zooming out to 0.3x")
                                transformableState.updateMatrix {
                                    val currentZoom = transformableState.zoom
                                    val scaleFactor = 0.3f / currentZoom
                                    postScale(scaleFactor, scaleFactor, centerX, centerY)
                                }
                                delay(500) // Wait for atlas generation
                                galleryViewModel.isAtlasGenerating.filter { !it }.first()
                                // 2. Zoom in to trigger LOD_2 (128px)
                                Log.d("App", "Auto zoom: zooming in to 1.5x")
                                transformableState.updateMatrix {
                                    val currentZoom = transformableState.zoom
                                    val scaleFactor = 1.5f / currentZoom
                                    postScale(scaleFactor, scaleFactor, centerX, centerY)
                                }
                                delay(500) // Wait for atlas generation
                                galleryViewModel.isAtlasGenerating.filter { !it }.first()
                                // 3. Zoom in more to trigger LOD_6 (512px)
                                Log.d("App", "Auto zoom: zooming in to 3.0x")
                                transformableState.updateMatrix {
                                    val currentZoom = transformableState.zoom
                                    val scaleFactor = 3.0f / currentZoom
                                    postScale(scaleFactor, scaleFactor, centerX, centerY)
                                }
                                delay(500) // Wait for atlas generation
                                galleryViewModel.isAtlasGenerating.filter { !it }.first()
                                // 4. Zoom back to medium level
                                Log.d("App", "Auto zoom: zooming back to 1.0x")
                                transformableState.updateMatrix {
                                    val currentZoom = transformableState.zoom
                                    val scaleFactor = 1.0f / currentZoom
                                    postScale(scaleFactor, scaleFactor, centerX, centerY)
                                }
                                delay(500)
                                galleryViewModel.isAtlasGenerating.filter { !it }.first()                            }

                            Log.d("App", "Benchmark sequence completed")
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
                                    contentDescription = "Gallery canvas"
                                }
                                .testTag(BenchmarkLabels.GALLERY_CANVAS_TEST_TAG),
                            zoom = transformableState.zoom,
                            offset = transformableState.offset,
                            state = gridState
                        ) {
                            // Draw media visualization on hex grid
                            hexGridLayout?.let { layout ->
                                MediaHexVisualization(
                                    hexGridLayout = layout,
                                    hexGridRenderer = hexGridRenderer,
                                    provideZoom = { transformableState.zoom },
                                    provideOffset = { transformableState.offset },
                                    atlasState = atlasState,
                                    selectedMedia = selectedMedia,
                                    onMediaClicked = { media ->
                                        Log.d("App", "Media clicked: ${media.displayName}")
                                        // If the same media is clicked twice, deselect it
                                        selectedMedia = if (selectedMedia == media) {
                                            Log.d("App", "Deselecting media: ${media.displayName}")
                                            null
                                        } else {
                                            media
                                        }
                                    },
                                    onHexCellClicked = { hexCell ->
                                        Log.d("App", "Hex cell clicked: (${hexCell.q}, ${hexCell.r})")
                                        selectedMedia = null
                                    },
                                    onFocusRequested = { bounds ->
                                        Log.d("App", "Focus requested: $bounds")
                                        coroutineScope.launch {
                                            transformableState.focusOn(bounds)
                                        }
                                    },
                                    onVisibleCellsChanged = { visibleCells ->
                                        Log.d("App", "Visible cells changed: ${visibleCells.size} cells")
                                        galleryViewModel.onVisibleCellsChanged(visibleCells, transformableState.zoom, selectedMedia)
                                    },
                                    cellFocusListener = cellFocusListener,
                                    onClearClickedMedia = {
                                        Log.d("CellFocus", "Clearing clicked media state for red outline")
                                    }
                                )
                            }
                        }
                    }

                    // Enhanced debug overlay with toggle button and comprehensive information
                    EnhancedDebugOverlay(
                        atlasState = atlasState,
                        isAtlasGenerating = isAtlasGenerating,
                        currentZoom = transformableState.zoom,
                        memoryStatus = memoryStatus,
                        smartMemoryManager = galleryViewModel.getSmartMemoryManager(),
                        deviceCapabilities = galleryViewModel.getDeviceCapabilities(),
                        significantCells = significantCells,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Focused cell panel UI stub - positioned relative to cell
                    focusedCellWithMedia?.let { cellWithMedia ->
                        FocusedCellPanel(
                            hexCellWithMedia = cellWithMedia,
                            atlasState = atlasState,
                            onDismiss = { focusedCellWithMedia = null },
                            onMediaSelected = { media ->
                                selectedMedia = media
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
            } else {
                // Permission flow - shown when permissions are not granted
                MediaPermissionFlow(
                    onPermissionGranted = { permissionGranted = true },
                    onPermissionDenied = {
                        // Handle permission denial - maybe show limited UI or exit
                        Log.w("App", "Media permissions denied")
                    }
                )
            }
        }
    }
}

/**
 * Simple UI stub for displaying media items in a focused cell.
 * This will be replaced with the actual design later.
 */
@Composable
private fun FocusedCellPanel(
    hexCellWithMedia: dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia,
    atlasState: dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult?,
    onDismiss: () -> Unit,
    onMediaSelected: (dev.serhiiyaremych.lumina.domain.model.Media) -> Unit,
    provideTranslationOffset: (panelSize: Size) -> Offset,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val offset = provideTranslationOffset(size)
                translationX = offset.x
                translationY = offset.y
            }
            .padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shadowElevation = 0.dp
    ) {
        // Compact horizontal row of photo previews
        LazyRow(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(hexCellWithMedia.mediaItems) { mediaWithPosition ->
                PhotoPreviewItem(
                    media = mediaWithPosition.media,
                    atlasState = atlasState,
                    onClick = { onMediaSelected(mediaWithPosition.media) },
                    modifier = Modifier.size(32.dp)
                )
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
    val initialY = screenY - panelSize.height / 2 // Center panel vertically relative to cell bottom

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

/**
 * Photo preview item displaying actual photo from atlas texture instead of icon.
 * Uses lowest LOD atlas for memory efficiency and optimal preview quality.
 */
@Composable
private fun PhotoPreviewItem(
    media: dev.serhiiyaremych.lumina.domain.model.Media,
    atlasState: dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable { onClick() }
            .clip(RoundedCornerShape(4.dp))
            .drawWithCache {
                val bounds = androidx.compose.ui.geometry.Rect(Offset.Zero, size)

                onDrawBehind {
                    when (atlasState) {
                        is dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult.Success -> {
                            // Search for photo across all atlases
                            val atlasAndRegion = atlasState.atlases.firstNotNullOfOrNull { atlas ->
                                atlas.regions[media.uri]?.let { region -> atlas to region }
                            }

                            if (atlasAndRegion != null && !atlasAndRegion.first.bitmap.isRecycled) {
                                val (foundAtlas, foundRegion) = atlasAndRegion
                                
                                // Calculate aspect-aware bounds to avoid stretching
                                val srcAspectRatio = foundRegion.atlasRect.width / foundRegion.atlasRect.height
                                val dstAspectRatio = bounds.width / bounds.height
                                
                                val aspectAwareBounds = if (srcAspectRatio > dstAspectRatio) {
                                    // Source is wider, fit height and center horizontally
                                    val newWidth = bounds.height * srcAspectRatio
                                    val offsetX = (bounds.width - newWidth) / 2
                                    androidx.compose.ui.geometry.Rect(
                                        left = bounds.left + offsetX,
                                        top = bounds.top,
                                        right = bounds.left + offsetX + newWidth,
                                        bottom = bounds.bottom
                                    )
                                } else {
                                    // Source is taller, fit width and center vertically
                                    val newHeight = bounds.width / srcAspectRatio
                                    val offsetY = (bounds.height - newHeight) / 2
                                    androidx.compose.ui.geometry.Rect(
                                        left = bounds.left,
                                        top = bounds.top + offsetY,
                                        right = bounds.right,
                                        bottom = bounds.top + offsetY + newHeight
                                    )
                                }
                                
                                drawStyledPhoto(
                                    image = foundAtlas.bitmap.asImageBitmap(),
                                    srcOffset = androidx.compose.ui.unit.IntOffset(
                                        foundRegion.atlasRect.left.toInt(),
                                        foundRegion.atlasRect.top.toInt()
                                    ),
                                    srcSize = androidx.compose.ui.unit.IntSize(
                                        foundRegion.atlasRect.width.toInt(),
                                        foundRegion.atlasRect.height.toInt()
                                    ),
                                    bounds = aspectAwareBounds,
                                    zoom = 1f,
                                    alpha = 1f,
                                    drawBorder = false // No border for small previews
                                )
                            } else {
                                // Fallback: placeholder when photo not in atlas
                                drawPlaceholderRect(media, bounds, zoom = 1f, alpha = 1f)
                            }
                        }
                        else -> {
                            // Fallback: placeholder when atlas not available
                            drawPlaceholderRect(media, bounds, zoom = 1f, alpha = 1f)
                        }
                    }
                }
            },
    )
}

