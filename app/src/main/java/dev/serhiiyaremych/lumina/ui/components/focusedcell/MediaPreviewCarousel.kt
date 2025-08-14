package dev.serhiiyaremych.lumina.ui.components.focusedcell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.MediaWithPosition
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.ui.SelectionMode

/**
 * Creates a click handler for media items with proper focus behavior.
 */
internal fun createMediaClickHandler(
    onMediaSelected: (Media) -> Unit,
    onFocusRequested: (Rect) -> Unit,
    getMediaBounds: (Media) -> Rect?,
    selectionMode: SelectionMode
): (Media) -> Unit = { media ->
    onMediaSelected(media)

    if (selectionMode == SelectionMode.PHOTO_MODE) {
        getMediaBounds(media)?.let { bounds ->
            onFocusRequested(bounds)
        }
    }
}

/**
 * Grid component displaying photo previews with gradient fade-out edges.
 * Handles the LazyRow layout and staggered animations.
 */
@Composable
internal fun MediaPreviewCarousel(
    mediaItems: List<MediaWithPosition>,
    level0Atlases: List<TextureAtlas>?,
    selectedMedia: Media?,
    panelVisible: Boolean,
    onMediaClick: (Media) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val fadeWidth = 16.dp.toPx()
                val leftHorizontalGradient = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = 0f,
                    endX = fadeWidth
                )
                val rightHorizontalGradient = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = size.width - fadeWidth,
                    endX = size.width
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = leftHorizontalGradient,
                        size = Size(fadeWidth, size.height),
                        blendMode = BlendMode.DstOut
                    )
                    drawRect(
                        brush = rightHorizontalGradient,
                        topLeft = Offset(size.width - fadeWidth, 0f),
                        size = Size(fadeWidth, size.height),
                        blendMode = BlendMode.DstOut
                    )
                }
            }
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            itemsIndexed(
                items = mediaItems,
                key = { _, item -> item.media.uri }
            ) { index, mediaWithPosition ->
                AnimatedPhotoPreview(
                    itemIndex = index,
                    media = mediaWithPosition.media,
                    level0Atlases = level0Atlases,
                    isSelected = selectedMedia == mediaWithPosition.media,
                    panelVisible = panelVisible,
                    onClick = { onMediaClick(mediaWithPosition.media) },
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}
