package dev.serhiiyaremych.lumina.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.MediaWithPosition
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import dev.serhiiyaremych.lumina.ui.SelectionMode
import dev.serhiiyaremych.lumina.ui.drawPlaceholderRect
import dev.serhiiyaremych.lumina.ui.drawStyledPhoto
import kotlinx.coroutines.delay

/**
 * Responsive layout constants for FocusedCellPanel width behavior
 */
private object ResponsiveLayoutConstants {
    /** Breakpoint for small to medium device transition (phones to small tablets) */
    const val SMALL_TO_MEDIUM_BREAKPOINT_DP = 600

    /** Breakpoint for medium to large device transition (small tablets to large tablets/desktop) */
    const val MEDIUM_TO_LARGE_BREAKPOINT_DP = 840

    /** Panel width percentage for small tablets (600-839dp width) */
    const val SMALL_TABLET_WIDTH_FRACTION = 0.7f

    /** Panel width percentage for large tablets/desktop (≥840dp width) */
    const val LARGE_TABLET_WIDTH_FRACTION = 0.5f
}

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
 * Animation state and transitions for the FocusedCellPanel.
 * Encapsulates Material 3 Expressive entrance/exit animations.
 */
@Composable
private fun rememberPanelAnimations(): PanelAnimations {
    var isVisible by remember { mutableStateOf(false) }

    // Trigger entrance animation on first composition
    LaunchedEffect(Unit) {
        isVisible = true
    }

    return remember(isVisible) {
        PanelAnimations(
            isVisible = isVisible,
            enterTransition = fadeIn(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                )
            ) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 400f
                ),
                initialOffsetY = { it / 3 }
            ) + scaleIn(
                animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = 500f
                ),
                initialScale = 0.92f
            ),
            exitTransition = fadeOut(
                animationSpec = tween(200)
            ) + slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { it / 4 }
            ) + scaleOut(
                animationSpec = tween(200),
                targetScale = 0.96f
            )
        )
    }
}

/**
 * Data class holding panel animation state and transitions.
 */
private data class PanelAnimations(
    val isVisible: Boolean,
    val enterTransition: EnterTransition,
    val exitTransition: ExitTransition
)

/**
 * Extension function to apply panel transformation modifiers with responsive width.
 */
@Composable
private fun Modifier.panelTransformation(
    provideTranslationOffset: (panelSize: Size) -> Offset
): Modifier {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    val widthModifier = when {
        !windowSizeClass.isWidthAtLeastBreakpoint(ResponsiveLayoutConstants.SMALL_TO_MEDIUM_BREAKPOINT_DP) -> {
            Modifier.fillMaxWidth()
        }
        !windowSizeClass.isWidthAtLeastBreakpoint(ResponsiveLayoutConstants.MEDIUM_TO_LARGE_BREAKPOINT_DP) -> {
            Modifier.fillMaxWidth(ResponsiveLayoutConstants.SMALL_TABLET_WIDTH_FRACTION)
        }
        else -> {
            Modifier.fillMaxWidth(ResponsiveLayoutConstants.LARGE_TABLET_WIDTH_FRACTION)
        }
    }

    return this
        .then(widthModifier)
        .graphicsLayer {
            val offset = provideTranslationOffset(size)
            translationX = offset.x
            translationY = offset.y
        }
        .padding(16.dp)
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
    // Use same focused cell color as hex grid renderer for visual consistency - vibrant and energetic
    val cellColor = MaterialTheme.colorScheme.tertiary // Same as hex grid SELECTED cells

    // Create panel background with cell color tint using compositeOver for proper blending
    val panelColor = cellColor.copy(alpha = 0.12f)
        .compositeOver(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))

    val cornerShape = RoundedCornerShape(24.dp)

    Surface(
        modifier = modifier.border(0.5.dp, cellColor, cornerShape),
        shape = cornerShape,
        color = panelColor,
        shadowElevation = 0.dp
    ) {
        PhotoPreviewGrid(
            mediaItems = hexCellWithMedia.mediaItems,
            level0Atlases = level0Atlases,
            selectionMode = selectionMode,
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

/**
 * Creates a click handler for media items with proper focus behavior.
 */
private fun createMediaClickHandler(
    onMediaSelected: (Media) -> Unit,
    onFocusRequested: (Rect) -> Unit,
    getMediaBounds: (Media) -> Rect?,
    selectionMode: SelectionMode
): (Media) -> Unit = { media ->
    // Always update selection state first
    onMediaSelected(media)

    // Only trigger focus animation if we're in PHOTO_MODE
    if (selectionMode == SelectionMode.PHOTO_MODE) {
        getMediaBounds(media)?.let { bounds ->
            onFocusRequested(bounds)
        }
    }
    // In CELL_MODE, just update selection without focus animation
}

/**
 * Grid component displaying photo previews with gradient fade-out edges.
 * Handles the LazyRow layout and staggered animations.
 */
@Composable
private fun PhotoPreviewGrid(
    mediaItems: List<MediaWithPosition>,
    level0Atlases: List<TextureAtlas>?,
    selectionMode: SelectionMode,
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
                onDrawWithContent {
                    drawContent()

                    // Left fade-out
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = 0f,
                            endX = fadeWidth
                        ),
                        size = Size(fadeWidth, size.height),
                        blendMode = BlendMode.DstOut
                    )

                    // Right fade-out
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startX = size.width - fadeWidth,
                            endX = size.width
                        ),
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
            items(
                items = mediaItems,
                key = { it.media.uri }
            ) { mediaWithPosition ->
                val isSelected = selectedMedia == mediaWithPosition.media
                val itemIndex = mediaItems.indexOf(mediaWithPosition)

                StaggeredPhotoPreviewItem(
                    itemIndex = itemIndex,
                    media = mediaWithPosition.media,
                    level0Atlases = level0Atlases,
                    isSelected = isSelected,
                    panelVisible = panelVisible,
                    onClick = { onMediaClick(mediaWithPosition.media) },
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

/**
 * Material 3 Expressive staggered animation wrapper for PhotoPreviewItem.
 * Uses simple alpha/scale animation that doesn't interfere with LazyRow's built-in animations.
 */
@Composable
private fun StaggeredPhotoPreviewItem(
    itemIndex: Int,
    media: Media,
    level0Atlases: List<TextureAtlas>?,
    isSelected: Boolean,
    onClick: () -> Unit,
    panelVisible: Boolean,
    modifier: Modifier = Modifier
) {
    // Use media URI as stable key to prevent animation state reset during scroll
    var hasStartedAnimation by remember(media.uri) { mutableStateOf(false) }

    // Calculate staggered delay based on item position
    val delayMs = (itemIndex * 60L).coerceAtMost(300L)

    // Trigger animation once when panel becomes visible
    LaunchedEffect(panelVisible, media.uri) {
        if (panelVisible && !hasStartedAnimation) {
            delay(delayMs)
            hasStartedAnimation = true
        }
    }

    // Animate alpha and scale for entrance
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
 * Custom Shape class for smooth polygon morphing using graphics-shapes library
 */
class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        matrix.reset()
        // Scale to fit the size, centered
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)

        val path = morph.toPath(progress = progress).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Photo preview item displaying actual photo from L0 atlas texture.
 * Uses only L0 atlases (32px thumbnails) which are always cached and contain all photos.
 */
@Composable
private fun PhotoPreviewItem(
    media: dev.serhiiyaremych.lumina.domain.model.Media,
    level0Atlases: List<TextureAtlas>?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get placeholder color from Material theme
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    // Material 3 Expressive shape morphing animation - Pentagon for selected state
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

    // Create polygons for morphing: rounded rectangle to hexagon
    val startShape = remember {
        RoundedPolygon(
            numVertices = 4, // Rectangle (4 vertices)
            radius = 1f,
            rounding = CornerRounding(0.3f) // Rounded corners
        )
    }

    val endShape = remember {
        RoundedPolygon(
            numVertices = 6, // Hexagon (6 vertices) - matches app's design language
            radius = 1f,
            rounding = CornerRounding(0.2f) // ~1dp equivalent corner rounding for softer hexagon
        )
    }

    val morph = remember(startShape, endShape) {
        Morph(start = startShape, end = endShape)
    }

    val morphShape = remember(morph, animatedMorphProgress) {
        MorphPolygonShape(morph, animatedMorphProgress)
    }

    // Use custom interaction source with no indication - shape morphing provides feedback
    val interactionSource = remember { MutableInteractionSource() }

    // Material 3 outline for photo visibility protection
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) // Subtle outline
    val outlineWidth = 0.5.dp // Thin border for minimal visual impact

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null // Disable ripple - shape morphing provides visual feedback
            ) { onClick() }
            .scale(animatedScale)
            .border(
                width = outlineWidth,
                color = outlineColor,
                shape = morphShape
            )
            .clip(morphShape)
            .drawWithCache {
                val bounds = androidx.compose.ui.geometry.Rect(Offset.Zero, size)

                onDrawBehind {
                    when {
                        level0Atlases != null && level0Atlases.isNotEmpty() -> {
                            // Search in L0 atlases for this photo (simple and direct)
                            val photoId = media.uri
                            val atlasAndRegion = level0Atlases.firstNotNullOfOrNull { atlas ->
                                atlas.regions[photoId]?.let { region ->
                                    atlas to region
                                }
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
                                // Fallback: placeholder when photo not in L0 atlases
                                drawPlaceholderRect(bounds, placeholderColor, zoom = 1f, alpha = 1f)
                            }
                        }
                        else -> {
                            // Fallback: placeholder when no L0 atlases available
                            drawPlaceholderRect(bounds, placeholderColor, zoom = 1f, alpha = 1f)
                        }
                    }
                }
            }
    )
}
