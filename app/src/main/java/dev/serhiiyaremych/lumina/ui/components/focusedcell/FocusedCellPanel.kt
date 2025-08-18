package dev.serhiiyaremych.lumina.ui.components.focusedcell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.ui.SelectionMode

/**
 * Panel displaying media items in a focused cell with photo previews.
 * Shows actual photo thumbnails from L0 atlas (persistent cache) with gradient fade-out edges.
 *
 * Enhanced with Material 3 Expressive motion:
 * - Panel entrance animations with spring physics
 * - Staggered item animations for smooth reveal
 * - State-aware transitions responsive to selection mode
 * - Loading state animations for better UX
 *
 * Responsive design with Material 3 Adaptive:
 * - < 600dp width (phones): Uses full width for optimal space usage
 * - 600dp-839dp width (small tablets): Constrains to 70% width for better UX
 * - ≥ 840dp width (large tablets): Constrains to 50% width to prevent awkward stretching
 *
 * Supports conditional focus animations based on selection mode:
 * - PHOTO_MODE: Panel selections trigger focus animations
 * - CELL_MODE: Panel selections just update selection without animation
 *
 * Uses only L0 atlases (32px thumbnails) which are always cached and contain all photos.
 * This provides optimal performance for thumbnail display in 44dp previews.
 */
@Composable
fun FocusedCellPanel(
    hexCellWithMedia: HexCellWithMedia,
    level0Atlases: List<TextureAtlas>?,
    selectionMode: SelectionMode,
    modifier: Modifier = Modifier,
    selectedMedia: Media? = null,
    onDismiss: () -> Unit,
    onMediaSelected: (Media) -> Unit,
    onFocusRequested: (Rect) -> Unit,
    getMediaBounds: (Media) -> Rect?,
    provideTranslationOffset: (panelSize: Size) -> Offset
) {
    val panelAnimations = rememberPanelAnimations()

    AnimatedVisibility(
        visible = panelAnimations.isVisible,
        enter = panelAnimations.enterTransition,
        exit = panelAnimations.exitTransition
    ) {
        FocusedCellPanelContent(
            hexCellWithMedia = hexCellWithMedia,
            level0Atlases = level0Atlases,
            selectionMode = selectionMode,
            selectedMedia = selectedMedia,
            isVisible = panelAnimations.isVisible,
            onMediaSelected = onMediaSelected,
            onFocusRequested = onFocusRequested,
            getMediaBounds = getMediaBounds,
            modifier = modifier.panelTransformation(provideTranslationOffset)
        )
    }
}

/**
 * Main content component of the FocusedCellPanel.
 * Contains the Surface and photo preview grid.
 */
@Composable
private fun FocusedCellPanelContent(
    hexCellWithMedia: HexCellWithMedia,
    level0Atlases: List<TextureAtlas>?,
    selectionMode: SelectionMode,
    selectedMedia: Media?,
    isVisible: Boolean,
    onMediaSelected: (Media) -> Unit,
    onFocusRequested: (Rect) -> Unit,
    getMediaBounds: (Media) -> Rect?,
    modifier: Modifier = Modifier
) {
    val cellColor = MaterialTheme.colorScheme.tertiary

    val panelColor = cellColor.copy(alpha = 0.12f)
        .compositeOver(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))

    val cornerShape = RoundedCornerShape(24.dp)

    Surface(
        modifier = modifier.border(0.5.dp, cellColor, cornerShape),
        shape = cornerShape,
        color = panelColor,
        shadowElevation = 0.dp
    ) {
        MediaPreviewCarousel(
            mediaItems = hexCellWithMedia.mediaItems,
            level0Atlases = level0Atlases,
            selectedMedia = selectedMedia,
            panelVisible = isVisible,
            onMediaClick = createMediaClickHandler(
                onMediaSelected = onMediaSelected,
                onFocusRequested = onFocusRequested,
                getMediaBounds = getMediaBounds,
                selectionMode = selectionMode
            )
        )
    }
}
