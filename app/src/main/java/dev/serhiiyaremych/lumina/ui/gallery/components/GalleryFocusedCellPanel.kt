package dev.serhiiyaremych.lumina.ui.gallery.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.ui.MediaHexState
import dev.serhiiyaremych.lumina.ui.TransformableState
import dev.serhiiyaremych.lumina.ui.components.FocusedCellPanel
import dev.serhiiyaremych.lumina.ui.gallery.GalleryUiState
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun GalleryFocusedCellPanel(
    uiState: GalleryUiState,
    streamingGalleryViewModel: StreamingGalleryViewModel,
    transformableState: TransformableState,
    coroutineScope: CoroutineScope,
    canvasSize: Size,
    mediaHexState: MediaHexState?,
    modifier: Modifier = Modifier
) {
    uiState.focusedCellWithMedia?.let { cellWithMedia ->
        mediaHexState?.let { state ->
            FocusedCellPanel(
                hexCellWithMedia = cellWithMedia,
                level0Atlases = uiState.availableAtlases[LODLevel.LEVEL_0],
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
                modifier = modifier
            )
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
