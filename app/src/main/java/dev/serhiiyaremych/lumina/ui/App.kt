package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
        
        // Selected media state for unrotation
        var selectedMedia by remember { mutableStateOf<dev.serhiiyaremych.lumina.domain.model.Media?>(null) }
        
        // Cell focus debug state
        var significantCells by remember { mutableStateOf(setOf<dev.serhiiyaremych.lumina.domain.model.HexCell>()) }
        
        // UI state for focused cell panel
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
                            onDismiss = { focusedCellWithMedia = null },
                            onMediaSelected = { media ->
                                selectedMedia = media
                            },
                            zoom = transformableState.zoom,
                            offset = transformableState.offset,
                            canvasSize = canvasSize,
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
    onDismiss: () -> Unit,
    onMediaSelected: (dev.serhiiyaremych.lumina.domain.model.Media) -> Unit,
    zoom: Float,
    offset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size,
    modifier: Modifier = Modifier
) {
    // Calculate cell bounds from vertices
    val cellBounds = remember(hexCellWithMedia.hexCell) {
        val vertices = hexCellWithMedia.hexCell.vertices
        val minX = vertices.minOf { it.x }
        val maxX = vertices.maxOf { it.x }
        val minY = vertices.minOf { it.y }
        val maxY = vertices.maxOf { it.y }
        androidx.compose.ui.geometry.Rect(minX, minY, maxX, maxY)
    }
    
    // Transform cell bottom position to screen coordinates  
    val screenX = (cellBounds.center.x * zoom) + offset.x
    val screenY = (cellBounds.bottom * zoom) + offset.y
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .graphicsLayer {
                translationX = screenX - canvasSize.width / 2
                translationY = screenY // Panel top edge aligns to cell bottom edge
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        // Compact horizontal row of media icons
        LazyRow(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(hexCellWithMedia.mediaItems) { mediaWithPosition ->
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onMediaSelected(mediaWithPosition.media) },
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (mediaWithPosition.media) {
                                is dev.serhiiyaremych.lumina.domain.model.Media.Image -> Icons.Default.Info
                                is dev.serhiiyaremych.lumina.domain.model.Media.Video -> Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

