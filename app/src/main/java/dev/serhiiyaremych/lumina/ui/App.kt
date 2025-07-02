package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.ui.components.MediaPermissionFlow
import dev.serhiiyaremych.lumina.ui.gallery.GalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

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
@OptIn(ExperimentalMaterial3Api::class)
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
        val density = LocalDensity.current

        // Permission state management
        var permissionGranted by remember { mutableStateOf(false) }

        Log.d(
            "App",
            "Media loaded: ${media.size} items, grouped into ${groupedMedia.size} groups, layout: ${hexGridLayout?.totalMediaCount ?: 0} positioned"
        )

        val gridState = rememberGridCanvasState()
        val transformableState = rememberTransformableState(
            animationSpec = tween(durationMillis = 400)
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
                                // 3. Zoom in more to trigger LOD_4 (512px)
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
                                    onMediaClicked = { media ->
                                        Log.d("App", "Media clicked: ${media.displayName}")
                                    },
                                    onHexCellClicked = { hexCell ->
                                        Log.d("App", "Hex cell clicked: (${hexCell.q}, ${hexCell.r})")
                                    },
                                    onFocusRequested = { bounds ->
                                        Log.d("App", "Focus requested: $bounds")
                                        coroutineScope.launch {
                                            transformableState.focusOn(bounds)
                                        }
                                    },
                                    onVisibleCellsChanged = { visibleCells ->
                                        Log.d("App", "Visible cells changed: ${visibleCells.size} cells")
                                        galleryViewModel.onVisibleCellsChanged(visibleCells, transformableState.zoom)
                                    }
                                )
                            }
                        }
                    }

                    // Debug: Display atlas bitmap when ready
                    atlasState?.let { state ->
                        if (state is dev.serhiiyaremych.lumina.domain.usecase.AtlasUpdateResult.Success) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.0f)
                                    .align(Alignment.TopEnd)
                                    .border(0.5.dp, Color.Magenta),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = state.atlas.bitmap.asImageBitmap(),
                                    contentDescription = "Debug Atlas",
                                    modifier = Modifier.size(200.dp)
                                )
                            }
                        }
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

