package dev.serhiiyaremych.lumina.ui.components.focusedcell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.ui.drawPlaceholderRect
import dev.serhiiyaremych.lumina.ui.drawStyledPhoto
import kotlinx.coroutines.delay

/**
 * Material 3 Expressive staggered animation wrapper for PhotoPreviewItem.
 * Uses simple alpha/scale animation that doesn't interfere with LazyRow's built-in animations.
 */
@Composable
internal fun AnimatedPhotoPreview(
    itemIndex: Int,
    media: Media,
    level0Atlases: List<TextureAtlas>?,
    isSelected: Boolean,
    onClick: () -> Unit,
    panelVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var hasStartedAnimation by remember(media.uri) { mutableStateOf(false) }

    val delayMs = (itemIndex * 60L).coerceAtMost(300L)

    LaunchedEffect(panelVisible, media.uri) {
        if (panelVisible && !hasStartedAnimation) {
            delay(delayMs)
            hasStartedAnimation = true
        }
    }

    val targetAlpha = if (hasStartedAnimation || !panelVisible) 1f else 0f
    val targetScale = if (hasStartedAnimation || !panelVisible) 1f else 0.8f

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 500f
        ),
        label = "staggeredAlpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 500f
        ),
        label = "staggeredScale"
    )

    PhotoPreviewItem(
        media = media,
        level0Atlases = level0Atlases,
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            }
    )
}

/**
 * Photo preview item displaying actual photo from L0 atlas texture.
 * Uses only L0 atlases (32px thumbnails) which are always cached and contain all photos.
 */
@Composable
internal fun PhotoPreviewItem(
    media: Media,
    level0Atlases: List<TextureAtlas>?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val targetScale = if (isSelected) 1.15f else 1.0f
    val morphProgress = if (isSelected) 1.0f else 0.0f

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 900f
        ),
        label = "photoScale"
    )

    val animatedMorphProgress by animateFloatAsState(
        targetValue = morphProgress,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 800f
        ),
        label = "shapeMorph"
    )

    val morphShape = remember(animatedMorphProgress) {
        MorphPolygonShape(SHAPE_MORPH, animatedMorphProgress)
    }

    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val outlineWidth = 0.5.dp

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .scale(animatedScale)
            .border(
                width = outlineWidth,
                color = outlineColor,
                shape = morphShape
            )
            .clip(morphShape)
            .drawWithCache {
                val bounds = Rect(Offset.Zero, size)
                val photoId = media.uri
                val atlasAndRegion = level0Atlases?.firstNotNullOfOrNull { atlas ->
                    atlas.getRegion(photoId)?.let { region -> atlas to region }
                }

                onDrawBehind {
                    when {
                        level0Atlases != null && level0Atlases.isNotEmpty() -> {
                            if (atlasAndRegion != null && !atlasAndRegion.first.bitmap.isRecycled) {
                                val (foundAtlas, foundRegion) = atlasAndRegion

                                val srcAspectRatio = foundRegion.atlasRect.width / foundRegion.atlasRect.height
                                val dstAspectRatio = bounds.width / bounds.height

                                val aspectAwareBounds = if (srcAspectRatio > dstAspectRatio) {
                                    val newWidth = bounds.height * srcAspectRatio
                                    val offsetX = (bounds.width - newWidth) / 2
                                    Rect(
                                        left = bounds.left + offsetX,
                                        top = bounds.top,
                                        right = bounds.left + offsetX + newWidth,
                                        bottom = bounds.bottom
                                    )
                                } else {
                                    val newHeight = bounds.width / srcAspectRatio
                                    val offsetY = (bounds.height - newHeight) / 2
                                    Rect(
                                        left = bounds.left,
                                        top = bounds.top + offsetY,
                                        right = bounds.right,
                                        bottom = bounds.top + offsetY + newHeight
                                    )
                                }

                                drawStyledPhoto(
                                    image = foundAtlas.bitmap.asImageBitmap(),
                                    srcOffset = IntOffset(
                                        foundRegion.atlasRect.left.toInt(),
                                        foundRegion.atlasRect.top.toInt()
                                    ),
                                    srcSize = IntSize(
                                        foundRegion.atlasRect.width.toInt(),
                                        foundRegion.atlasRect.height.toInt()
                                    ),
                                    bounds = aspectAwareBounds,
                                    zoom = 1f,
                                    alpha = 1f,
                                    drawBorder = false
                                )
                            } else {
                                drawPlaceholderRect(bounds, placeholderColor, zoom = 1f, alpha = 1f)
                            }
                        }
                        else -> {
                            drawPlaceholderRect(bounds, placeholderColor, zoom = 1f, alpha = 1f)
                        }
                    }
                }
            }
    )
}
