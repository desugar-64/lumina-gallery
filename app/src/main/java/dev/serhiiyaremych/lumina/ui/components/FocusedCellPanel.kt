package dev.serhiiyaremych.lumina.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import dev.serhiiyaremych.lumina.ui.SelectionMode
import dev.serhiiyaremych.lumina.ui.drawStyledPhoto
import dev.serhiiyaremych.lumina.ui.drawPlaceholderRect

/**
 * Panel displaying media items in a focused cell with photo previews.
 * Shows actual photo thumbnails from atlas textures with gradient fade-out edges.
 *
 * Supports conditional focus animations based on selection mode:
 * - PHOTO_MODE: Panel selections trigger focus animations
 * - CELL_MODE: Panel selections just update selection without animation
 */
@Composable
fun FocusedCellPanel(
    hexCellWithMedia: HexCellWithMedia,
    atlasState: MultiAtlasUpdateResult?,
    selectionMode: SelectionMode,
    onDismiss: () -> Unit,
    onMediaSelected: (Media) -> Unit,
    onFocusRequested: (Rect) -> Unit,
    getMediaBounds: (Media) -> Rect?,
    provideTranslationOffset: (panelSize: Size) -> Offset,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val offset = provideTranslationOffset(size)
                translationX = offset.x
                translationY = offset.y
            }
            .padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shadowElevation = 0.dp
    ) {
        // Compact horizontal row of photo previews with gradient fade-out at edges
        Box(
            modifier = Modifier
                .padding(8.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithCache {
                    val fadeWidth = 16.dp.toPx() // Fixed fade width for visibility
                    onDrawWithContent {
                        // First draw the content
                        drawContent()

                        // Then apply gradient masks to fade edges
                        // Left fade-out
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = 0f,
                                endX = fadeWidth
                            ),
                            size = androidx.compose.ui.geometry.Size(fadeWidth, size.height),
                            blendMode = BlendMode.DstOut
                        )

                        // Right fade-out
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startX = size.width - fadeWidth,
                                endX = size.width
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width - fadeWidth, 0f),
                            size = androidx.compose.ui.geometry.Size(fadeWidth, size.height),
                            blendMode = BlendMode.DstOut
                        )
                    }
                }
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(hexCellWithMedia.mediaItems) { mediaWithPosition ->
                    PhotoPreviewItem(
                        media = mediaWithPosition.media,
                        atlasState = atlasState,
                        onClick = {
                            val media = mediaWithPosition.media

                            // Always update selection state first
                            onMediaSelected(media)

                            // Only trigger focus animation if we're in PHOTO_MODE
                            // PHOTO_MODE: User clicked photo directly or is in deep zoom
                            // CELL_MODE: User is browsing at cell level
                            if (selectionMode == dev.serhiiyaremych.lumina.ui.SelectionMode.PHOTO_MODE) {
                                getMediaBounds(media)?.let { bounds ->
                                    onFocusRequested(bounds)
                                }
                            }
                            // In CELL_MODE, just update selection without focus animation
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Photo preview item displaying actual photo from atlas texture instead of icon.
 * Uses lowest LOD atlas for memory efficiency and optimal preview quality.
 */
@Composable
private fun PhotoPreviewItem(
    media: dev.serhiiyaremych.lumina.domain.model.Media,
    atlasState: dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable { onClick() }
            .clip(RoundedCornerShape(4.dp)) // Restore individual photo clipping
            .drawWithCache {
                val bounds = androidx.compose.ui.geometry.Rect(Offset.Zero, size)

                onDrawBehind {
                    when (atlasState) {
                        is dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult.Success -> {
                            // Search for photo across all atlases
                            val atlasAndRegion = atlasState.atlases.firstNotNullOfOrNull { atlas ->
                                atlas.regions[media.uri]?.let { region -> atlas to region }
                            }

                            if (atlasAndRegion != null && !atlasAndRegion.first.bitmap.isRecycled) {
                                val (foundAtlas, foundRegion) = atlasAndRegion

                                // Calculate aspect-aware bounds to avoid stretching
                                val srcAspectRatio = foundRegion.atlasRect.width / foundRegion.atlasRect.height
                                val dstAspectRatio = bounds.width / bounds.height

                                val aspectAwareBounds = if (srcAspectRatio > dstAspectRatio) {
                                    // Source is wider, fit height and center horizontally
                                    val newWidth = bounds.height * srcAspectRatio
                                    val offsetX = (bounds.width - newWidth) / 2
                                    androidx.compose.ui.geometry.Rect(
                                        left = bounds.left + offsetX,
                                        top = bounds.top,
                                        right = bounds.left + offsetX + newWidth,
                                        bottom = bounds.bottom
                                    )
                                } else {
                                    // Source is taller, fit width and center vertically
                                    val newHeight = bounds.width / srcAspectRatio
                                    val offsetY = (bounds.height - newHeight) / 2
                                    androidx.compose.ui.geometry.Rect(
                                        left = bounds.left,
                                        top = bounds.top + offsetY,
                                        right = bounds.right,
                                        bottom = bounds.top + offsetY + newHeight
                                    )
                                }

                                drawStyledPhoto(
                                    image = foundAtlas.bitmap.asImageBitmap(),
                                    srcOffset = androidx.compose.ui.unit.IntOffset(
                                        foundRegion.atlasRect.left.toInt(),
                                        foundRegion.atlasRect.top.toInt()
                                    ),
                                    srcSize = androidx.compose.ui.unit.IntSize(
                                        foundRegion.atlasRect.width.toInt(),
                                        foundRegion.atlasRect.height.toInt()
                                    ),
                                    bounds = aspectAwareBounds,
                                    zoom = 1f,
                                    alpha = 1f,
                                    drawBorder = false // No border for small previews
                                )

                            } else {
                                // Fallback: placeholder when photo not in atlas
                                drawPlaceholderRect(media, bounds, zoom = 1f, alpha = 1f)
                            }
                        }
                        else -> {
                            // Fallback: placeholder when atlas not available
                            drawPlaceholderRect(media, bounds, zoom = 1f, alpha = 1f)
                        }
                    }
                }
            },
    )
}
