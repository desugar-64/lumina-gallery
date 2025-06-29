package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import dev.serhiiyaremych.lumina.ui.components.MediaPermissionFlow
import dev.serhiiyaremych.lumina.ui.gallery.GalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(modifier: Modifier = Modifier) {
    LuminaGalleryTheme {
        val galleryViewModel: GalleryViewModel = hiltViewModel()
        val media by galleryViewModel.mediaState.collectAsState()
        val groupedMedia by galleryViewModel.groupedMediaState.collectAsState()
        val hexGridLayout by galleryViewModel.hexGridLayoutState.collectAsState()
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

                    TransformableContent(
                        modifier = Modifier.fillMaxSize(),
                        state = transformableState
                    ) {
                        GridCanvas(
                            modifier = Modifier.fillMaxSize(),
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
                                        // TODO: Pass to AtlasManager
                                    }
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

