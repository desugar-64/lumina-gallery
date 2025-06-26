package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.serhiiyaremych.lumina.domain.model.HexGridGenerator
import dev.serhiiyaremych.lumina.ui.components.MediaPermissionFlow
import dev.serhiiyaremych.lumina.ui.gallery.GalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.sqrt

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

        // Permission state management
        var permissionGranted by remember { mutableStateOf(false) }

        // Calculate hex grid size based on number of groups
        val hexGridSize = remember(groupedMedia) {
            if (groupedMedia.isEmpty()) {
                15
            } else {
                val groupCount = groupedMedia.size
                // Calculate grid size to fit all groups (with some padding)
                val minSize = ceil(sqrt(groupCount.toDouble())).toInt()
                (minSize + 2).coerceAtLeast(10).coerceAtMost(25)
            }
        }

        // Determine hex cell size based on grid size
        val hexCellSize = remember(hexGridSize) {
            when {
                hexGridSize <= 10 -> 230.dp
                hexGridSize <= 15 -> 200.dp
                hexGridSize <= 20 -> 170.dp
                else -> 140.dp
            }
        }

        Log.d(
            "App",
            "Media loaded: ${media.size} items, grouped into ${groupedMedia.size} groups, grid size: $hexGridSize"
        )

        val gridState = rememberGridCanvasState()
        val transformableState = rememberTransformableState(
            animationSpec = tween(durationMillis = 400)
        )
        val hexGridRenderer = remember { HexGridRenderer() }
        val hexGridGenerator = remember { HexGridGenerator() }
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = modifier.fillMaxSize()) {
            if (permissionGranted) {
                // Main gallery interface - only shown when permissions are granted
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
                        MediaHexVisualization(
                            hexGridGenerator = hexGridGenerator,
                            hexGridRenderer = hexGridRenderer,
                            groupedMedia = groupedMedia,
                            hexGridSize = hexGridSize,
                            hexCellSize = hexCellSize,
                            zoom = transformableState.zoom,
                            offset = transformableState.offset,
                            onFocusRequested = { bounds ->
                                coroutineScope.launch {
                                    transformableState.focusOn(bounds)
                                }
                            }
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

