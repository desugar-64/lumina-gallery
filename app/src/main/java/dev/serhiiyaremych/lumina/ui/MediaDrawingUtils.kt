package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem

/**
 * Drawing utilities for media hex visualization.
 * Contains pure functions for rendering media items, placeholders, and styled photos.
 */

/**
 * Draws animatable media from atlas texture or falls back to colored rectangle.
 */
fun DrawScope.drawAnimatableMediaFromAtlas(
    animatableItem: AnimatableMediaItem,
    atlasState: MultiAtlasUpdateResult?,
    zoom: Float
) {
    val media = animatableItem.mediaWithPosition.media
    val originalBounds = animatableItem.mediaWithPosition.absoluteBounds
    val rotationAngle = animatableItem.currentRotation

    // Apply reveal animation transforms
    val slideOffset = animatableItem.currentSlideOffset
    val breathingScale = animatableItem.currentBreathingScale
    val alpha = animatableItem.currentAlpha

    // Calculate transformed bounds with slide offset
    val transformedBounds = Rect(
        left = originalBounds.left + slideOffset.x,
        top = originalBounds.top + slideOffset.y,
        right = originalBounds.right + slideOffset.x,
        bottom = originalBounds.bottom + slideOffset.y
    )

    // Apply breathing scale around the center
    val scaledBounds = if (breathingScale != 1f) {
        val center = transformedBounds.center
        val scaledWidth = transformedBounds.width * breathingScale
        val scaledHeight = transformedBounds.height * breathingScale
        Rect(
            left = center.x - scaledWidth / 2,
            top = center.y - scaledHeight / 2,
            right = center.x + scaledWidth / 2,
            bottom = center.y + scaledHeight / 2
        )
    } else {
        transformedBounds
    }

    // Apply alpha and draw
    drawMediaFromAtlas(media, scaledBounds, rotationAngle, atlasState, zoom, alpha)
}

/**
 * Draws media from atlas texture or falls back to colored rectangle.
 */
fun DrawScope.drawMediaFromAtlas(
    media: Media,
    bounds: Rect,
    rotationAngle: Float,
    atlasState: MultiAtlasUpdateResult?,
    zoom: Float,
    alpha: Float = 1f
) {
    // Calculate rotation center (center of the media bounds)
    val center = bounds.center

    // Apply rotation transformation around the center
    withTransform({
        rotate(rotationAngle, center)
    }) {
        when (atlasState) {
            is MultiAtlasUpdateResult.Success -> {
                // Search across all atlases for this photo
                val photoId = media.uri
                val atlasAndRegion = atlasState.atlases.firstNotNullOfOrNull { atlas ->
                    atlas.regions[photoId]?.let { region ->
                        atlas to region
                    }
                }

                if (atlasAndRegion != null) {
                    val (foundAtlas, foundRegion) = atlasAndRegion
                    if (!foundAtlas.bitmap.isRecycled) {
                        // Draw from atlas texture with photo styling
                        val atlasBitmap = foundAtlas.bitmap.asImageBitmap()

                        drawStyledPhoto(
                            image = atlasBitmap,
                            srcOffset = IntOffset(
                                foundRegion.atlasRect.left.toInt(),
                                foundRegion.atlasRect.top.toInt()
                            ),
                            srcSize = IntSize(
                                foundRegion.atlasRect.width.toInt(),
                                foundRegion.atlasRect.height.toInt()
                            ),
                            bounds = bounds,
                            zoom = zoom,
                            alpha = alpha
                        )
                    } else {
                        // Bitmap was recycled, fall back to placeholder
                        drawPlaceholderRect(media, bounds, Color.Gray, zoom, alpha)
                    }
                } else {
                    // Fallback: No atlas contains this photo
                    drawPlaceholderRect(media, bounds, Color.Gray, zoom, alpha)
                }
            }

            else -> {
                // Fallback: No atlas available, draw colored rectangle
                drawPlaceholderRect(media, bounds, zoom = zoom, alpha = alpha)
            }
        }
    }
}

/**
 * Draws a styled placeholder for media with same physical photo appearance.
 */
fun DrawScope.drawPlaceholderRect(
    media: Media,
    bounds: Rect,
    overrideColor: Color? = null,
    zoom: Float = 1f,
    alpha: Float = 1f
) {
    val color = overrideColor ?: when (media) {
        is Media.Image -> Color(0xFF2196F3)
        is Media.Video -> Color(0xFF4CAF50)
    }

    val borderWidth = 2.dp.toPx()
    val innerCornerRadius = 4.dp.toPx()  // Increased for smoother corners
    val outerCornerRadius = innerCornerRadius + borderWidth
    val shadowOffset = 1.dp.toPx()

    // 1. Draw subtle contact shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.15f * alpha),
        topLeft = bounds.topLeft + Offset(shadowOffset, shadowOffset),
        size = bounds.size,
        cornerRadius = CornerRadius(outerCornerRadius)
    )

    // 2. Draw white border (photo background)
    drawRoundRect(
        color = Color.White.copy(alpha = alpha),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(outerCornerRadius)
    )

    // 3. Draw the colored placeholder within the border
    val imageArea = Rect(
        left = bounds.left + borderWidth,
        top = bounds.top + borderWidth,
        right = bounds.right - borderWidth,
        bottom = bounds.bottom - borderWidth
    )

    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = imageArea.topLeft,
        size = imageArea.size,
        cornerRadius = CornerRadius(innerCornerRadius)
    )

    // 4. Draw hairline dark gray border around the entire photo
    drawRoundRect(
        color = Color(0xFF666666).copy(alpha = alpha),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(outerCornerRadius),
        style = Stroke(width = Stroke.HairlineWidth / zoom)
    )
}

/**
 * Draws a styled photo with realistic physical photo appearance:
 * - Subtle contact shadow
 * - White border
 * - Rounded corners
 */
fun DrawScope.drawStyledPhoto(
    image: ImageBitmap,
    srcOffset: IntOffset,
    srcSize: IntSize,
    bounds: Rect,
    zoom: Float,
    alpha: Float = 1f,
    drawBorder: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit
) {
    val borderWidth = if (drawBorder) 2.dp.toPx() else 0f
    val innerCornerRadius = if (drawBorder) 0.1.dp.toPx() else 0f  // Increased for smoother corners
    val outerCornerRadius = innerCornerRadius + borderWidth
    val shadowOffset = if (drawBorder) 0.1.dp.toPx() else 0f

    if (drawBorder) {
        // 1. Draw subtle contact shadow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.4f * alpha),
            topLeft = bounds.topLeft + Offset(shadowOffset, shadowOffset),
            size = bounds.size,
            cornerRadius = CornerRadius(outerCornerRadius)
        )

        // 2. Draw white border (photo background)
        drawRoundRect(
            color = Color.White.copy(alpha = alpha),
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = CornerRadius(outerCornerRadius),
        )
    }

    // 3. Draw the actual image within the border (or full bounds if no border)
    val imageArea = Rect(
        left = bounds.left + borderWidth,
        top = bounds.top + borderWidth,
        right = bounds.right - borderWidth,
        bottom = bounds.bottom - borderWidth
    )

    drawImage(
        image = image,
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstOffset = IntOffset(
            imageArea.left.toInt(),
            imageArea.top.toInt()
        ),
        dstSize = IntSize(
            imageArea.width.toInt(),
            imageArea.height.toInt()
        ),
        alpha = alpha
    )

    if (drawBorder) {
        // 4. Draw hairline dark gray border around the entire photo
        drawRoundRect(
            color = Color(0xFF666666).copy(alpha = alpha),
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = CornerRadius(outerCornerRadius),
            style = Stroke(width = Stroke.HairlineWidth / zoom)
        )
    }
}