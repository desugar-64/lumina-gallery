package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.serhiiyaremych.lumina.domain.model.HexGridGenerator
import dev.serhiiyaremych.lumina.ui.gallery.GalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
fun App(modifier: Modifier = Modifier) {
    LuminaGalleryTheme {
        val galleryViewModel: GalleryViewModel = hiltViewModel()
        val media by galleryViewModel.mediaState.collectAsState()
        val groupedMedia by galleryViewModel.groupedMediaState.collectAsState()

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
        val transformableState = rememberTransformableState()
        val hexGridRenderer = remember { HexGridRenderer() }
        val hexGridGenerator = remember { HexGridGenerator() }

        TransformableContent(
            modifier = modifier.fillMaxSize(),
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
                    onFocusRequested = { transformableState.focusOn(it) }
                )
            }
        }
    }
}
