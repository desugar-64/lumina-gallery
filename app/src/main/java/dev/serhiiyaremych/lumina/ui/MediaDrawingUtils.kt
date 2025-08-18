package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem

/**
 * Draws a styled placeholder for media with same physical photo appearance.
 * Uses Material 3 theme colors for light and muted appearance.
 */
fun DrawScope.drawPlaceholderRect(
    bounds: Rect,
    placeholderColor: Color,
    zoom: Float = 1f,
    alpha: Float = 1f
) {
    val color = placeholderColor

    val borderWidth = 2.dp.toPx()
    val innerCornerRadius = 0.1.dp.toPx() // Increased for smoother corners
    val outerCornerRadius = innerCornerRadius + borderWidth

    drawRoundRect(
        color = Color.White.copy(alpha = alpha),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(outerCornerRadius)
    )

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
    drawBorder: Boolean = true
) {
    val borderWidth = if (drawBorder) 2.dp.toPx() else 0f
    val innerCornerRadius = if (drawBorder) 0.1.dp.toPx() else 0f // Increased for smoother corners
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
            cornerRadius = CornerRadius(outerCornerRadius)
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

/**
 * Draws animatable media from streaming atlas system or falls back to colored rectangle.
 */
fun DrawScope.drawAnimatableMediaFromStreamingAtlas(
    animatableItem: AnimatableMediaItem,
    streamingAtlases: Map<dev.serhiiyaremych.lumina.domain.model.LODLevel, List<dev.serhiiyaremych.lumina.domain.model.TextureAtlas>>?,
    placeholderColor: Color,
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
    drawMediaFromStreamingAtlas(media, scaledBounds, rotationAngle, streamingAtlases, placeholderColor, zoom, alpha)
}

/**
 * Draws media from streaming atlas system or falls back to colored rectangle.
 */
fun DrawScope.drawMediaFromStreamingAtlas(
    media: Media,
    bounds: Rect,
    rotationAngle: Float,
    streamingAtlases: Map<dev.serhiiyaremych.lumina.domain.model.LODLevel, List<dev.serhiiyaremych.lumina.domain.model.TextureAtlas>>?,
    placeholderColor: Color,
    zoom: Float,
    alpha: Float = 1f
) {
    // Calculate rotation center (center of the media bounds)
    val center = bounds.center

    // Apply rotation transformation around the center
    withTransform({
        rotate(rotationAngle, center)
    }) {
        // Use the provided Material 3 placeholder color
        when {
            streamingAtlases != null && streamingAtlases.isNotEmpty() -> {
                // Search across all LOD levels for this photo (highest LOD first)
                val photoId = media.uri
                var atlasAndRegion: Pair<dev.serhiiyaremych.lumina.domain.model.TextureAtlas, dev.serhiiyaremych.lumina.domain.model.AtlasRegion>? = null

                // Debug: Log available LOD levels
                android.util.Log.d("MediaDrawing", "Available LOD levels for ${media.displayName}: ${streamingAtlases.keys.map { "${it.name}(${streamingAtlases[it]?.size ?: 0} atlases)" }}")

                // Search from highest to lowest LOD for best quality
                val sortedLODs = streamingAtlases.keys.sortedByDescending { it.level }
                for (lodLevel in sortedLODs) {
                    val atlases = streamingAtlases[lodLevel] ?: continue
                    android.util.Log.d("MediaDrawing", "Searching ${lodLevel.name}: ${atlases.size} atlases, ${atlases.sumOf { it.totalPhotoSlots }} total slots, ${atlases.sumOf { it.photoCount }} available")

                    atlasAndRegion = atlases.firstNotNullOfOrNull { atlas ->
                        // Check reactive regions (single source of truth)
                        atlas.reactiveRegions[photoId]?.value?.let { region ->
                            android.util.Log.d("MediaDrawing", "🟢 DRAWING photo ${media.displayName} from reactive region in ${lodLevel.name}")
                            atlas to region
                        }
                    }
                    if (atlasAndRegion != null) break
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
                        drawPlaceholderRect(bounds, placeholderColor, zoom, alpha)
                    }
                } else {
                    // Fallback: No atlas contains this photo
                    drawPlaceholderRect(bounds, placeholderColor, zoom, alpha)
                }
            }

            else -> {
                // Fallback: No atlas available, draw colored rectangle
                drawPlaceholderRect(bounds, placeholderColor, zoom, alpha)
            }
        }
    }
}
