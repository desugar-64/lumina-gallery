package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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

        val currentOffset by galleryViewModel.tsOffset
        val currentZoom by galleryViewModel.tsZoom
        val currentDisplayMatrix by galleryViewModel.currentDisplayMatrix

        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val viewWidthPx = remember(configuration, density) { (configuration.screenWidthDp.dp * density.density).toPx().toInt() }
        val viewHeightPx = remember(configuration, density) { (configuration.screenHeightDp.dp * density.density).toPx().toInt() }

        LaunchedEffect(galleryViewModel) {
            galleryViewModel.matrixUpdateEvent.collect { matrixUpdate ->
                galleryViewModel.updateTransformFromMatrixUpdate(matrixUpdate)
            }
        }

        val hexGridSize = remember(groupedMedia) {
            if (groupedMedia.isEmpty()) {
                15
            } else {
                val groupCount = groupedMedia.size
                val minSize = ceil(sqrt(groupCount.toDouble())).toInt()
                (minSize + 2).coerceAtLeast(10).coerceAtMost(25)
            }
        }

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
        val hexGridRenderer = remember { HexGridRenderer() }
        val hexGridGenerator = remember { HexGridGenerator() }

        val defaultVisibleBounds = remember { Rect.Zero }

        TransformableContent(
            modifier = modifier.fillMaxSize(),
            zoom = currentZoom,
            offset = currentOffset,
            onTransformChanged = galleryViewModel::onTransformChanged
        ) {
            GridCanvas(
                modifier = Modifier.fillMaxSize(),
                zoom = currentZoom,
                offset = currentOffset,
                state = gridState
            ) {
                MediaHexVisualization(
                    modifier = Modifier.fillMaxSize(),
                    hexGridGenerator = hexGridGenerator,
                    hexGridRenderer = hexGridRenderer,
                    groupedMedia = groupedMedia,
                    hexGridSize = hexGridSize,
                    hexCellSize = hexCellSize,
                    zoom = currentZoom,
                    offset = currentOffset,
                    geometryReader = galleryViewModel.geometryReader,
                    onHexCellClick = { hexCell, matrix, size ->
                        galleryViewModel.onHexCellClicked(hexCell, matrix, viewWidthPx, viewHeightPx)
                    },
                    currentMatrix = currentDisplayMatrix,
                    visibleBounds = defaultVisibleBounds
                )
            }
        }
    }
}
