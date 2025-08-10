package dev.serhiiyaremych.lumina.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.serhiiyaremych.lumina.ui.gallery.StreamingGalleryViewModel
import dev.serhiiyaremych.lumina.ui.theme.LuminaGalleryTheme
import dev.serhiiyaremych.lumina.ui.gallery.components.PermissionManager
import dev.serhiiyaremych.lumina.ui.gallery.components.GalleryContent

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
    autoZoom: Boolean = false
) {
    LuminaGalleryTheme {
        // Single UI state following UDF architecture
        val uiState by streamingGalleryViewModel.uiState.collectAsState()

        Box(modifier = modifier.fillMaxSize()) {
            PermissionManager(
                permissionGranted = uiState.permissionGranted,
                onPermissionGranted = { streamingGalleryViewModel.updatePermissionGranted(it) }
            )

            if (uiState.permissionGranted) {
                GalleryContent(
                    uiState = uiState,
                    streamingGalleryViewModel = streamingGalleryViewModel,
                    isBenchmarkMode = isBenchmarkMode,
                    autoZoom = autoZoom
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
