package dev.serhiiyaremych.lumina.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import kotlinx.coroutines.launch

/**
 * Wrapper class for media items with animation capabilities.
 * Manages selection state and provides explicit animation functions.
 */
@Stable
class AnimatableMediaItem(
    val mediaWithPosition: dev.serhiiyaremych.lumina.domain.model.MediaWithPosition
) {
    private val rotationAnimatable = Animatable(mediaWithPosition.rotationAngle)

    // Pure state - no side effects
    var isSelected: Boolean by mutableStateOf(false)
        private set

    // Read-only animation state
    val currentRotation: Float get() = rotationAnimatable.value
    val isAnimating: Boolean get() = rotationAnimatable.isRunning
    val originalRotation: Float = mediaWithPosition.rotationAngle

    // Pure state update (no side effects)
    fun updateSelection(selected: Boolean) {
        isSelected = selected
    }

    // Explicit animation function (called from composable)
    suspend fun animateToSelectionState(
        animationSpec: AnimationSpec<Float> = tween(350)
    ) {
        val targetRotation = if (isSelected) 0f else originalRotation
        rotationAnimatable.animateTo(targetRotation, animationSpec)
    }
}

/**
 * Manager for AnimatableMediaItem instances.
 * Handles creation, cleanup, and retrieval of animated media items.
 */
@Stable
class AnimatableMediaManager {
    private val animatableItems = mutableMapOf<Media, AnimatableMediaItem>()

    fun getOrCreateAnimatable(mediaWithPosition: dev.serhiiyaremych.lumina.domain.model.MediaWithPosition): AnimatableMediaItem {
        return animatableItems.getOrPut(mediaWithPosition.media) {
            AnimatableMediaItem(mediaWithPosition)
        }
    }

    fun getAnimatable(media: Media): AnimatableMediaItem? = animatableItems[media]

    fun cleanupUnused(currentMedia: Set<Media>) {
        animatableItems.keys.retainAll(currentMedia)
    }

    fun hasAnimations(): Boolean = animatableItems.values.any { it.isAnimating }

    fun updateSelection(selectedMedia: Media?) {
        animatableItems.values.forEach { item ->
            item.updateSelection(item.mediaWithPosition.media == selectedMedia)
        }
    }
}

@Composable
fun MediaHexVisualization(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    hexGridRenderer: HexGridRenderer,
    provideZoom: () -> Float,
    provideOffset: () -> Offset,
    atlasState: MultiAtlasUpdateResult? = null,
    selectedMedia: Media? = null,
    onMediaClicked: (Media) -> Unit = {},
    onHexCellClicked: (HexCell) -> Unit = {},
    /**
     * Callback when content requests programmatic focus.
     * @param bounds The [Rect] bounds of the content to focus on, in content coordinates.
     *               The transformation system will smoothly center and zoom to these bounds.
     */
    onFocusRequested: (Rect) -> Unit = {},
    /**
     * Callback when visible cells change due to zoom/pan operations.
     * Reports cells that are actually being rendered on screen.
     */
    onVisibleCellsChanged: (List<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia>) -> Unit = {}
) {
    val geometryReader = remember { GeometryReader() }
    var clickedMedia by remember { mutableStateOf<Media?>(null) }
    var clickedHexCell by remember { mutableStateOf<HexCell?>(null) }
    var ripplePosition by remember { mutableStateOf<Offset?>(null) }

    // Capture latest callback to avoid restarting effect when callback changes
    val currentOnVisibleCellsChanged by rememberUpdatedState(onVisibleCellsChanged)

    if (hexGridLayout.hexCellsWithMedia.isEmpty()) return

    // Animation manager for media items
    val animationManager = remember { AnimatableMediaManager() }

    // Clean up unused animations when layout changes
    LaunchedEffect(hexGridLayout) {
        val currentMediaSet = hexGridLayout.hexCellsWithMedia.flatMap { it.mediaItems.map { it.media } }.toSet()
        animationManager.cleanupUnused(currentMediaSet)
    }

    // Track current and previous selected media for targeted animations
    var currentSelectedItem by remember { mutableStateOf<AnimatableMediaItem?>(null) }
    var previousSelectedItem by remember { mutableStateOf<AnimatableMediaItem?>(null) }

    // Handle selection changes - only animate affected items
    LaunchedEffect(selectedMedia) {
        // Store previous selection
        previousSelectedItem = currentSelectedItem

        // Update current selection using the animation manager
        currentSelectedItem = selectedMedia?.let { media ->
            animationManager.getAnimatable(media)?.apply {
                updateSelection(true)
            }
        }

        // Update previous selection state
        previousSelectedItem?.updateSelection(false)

        // Animate both items concurrently
        listOf(currentSelectedItem, previousSelectedItem).forEach { item ->
            item?.let {
                launch { it.animateToSelectionState() }
            }
        }
    }


    LaunchedEffect(hexGridLayout) {
        geometryReader.clear()
        clickedMedia = null
        clickedHexCell = null
    }

    // Monitor zoom/offset changes and report visible cells
    // Use currentOnVisibleCellsChanged as key to restart when callback logic changes
    LaunchedEffect(hexGridLayout, currentOnVisibleCellsChanged) {
        snapshotFlow {
            provideZoom() to provideOffset()
        }.collect { (zoom, offset) ->
            val visibleCells = calculateVisibleCells(
                hexGridLayout = hexGridLayout,
                zoom = zoom,
                offset = offset
            )
            currentOnVisibleCellsChanged(visibleCells)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit, provideZoom, provideOffset) {
                detectTapGestures { tapOffset ->
                    val zoom = provideZoom()
                    val offset = provideOffset()
                    val clampedZoom = zoom.coerceIn(0.01f, 100f)
                    val transformedPos = Offset(
                        (tapOffset.x - offset.x) / clampedZoom,
                        (tapOffset.y - offset.y) / clampedZoom
                    )

                    ripplePosition = tapOffset


                    // Check for media hits with rotation-aware hit testing
                    val hitAnimatableMedia = findAnimatableMediaAtPosition(transformedPos, hexGridLayout, animationManager)
                    if (hitAnimatableMedia != null) {
                        val media = hitAnimatableMedia.mediaWithPosition.media
                        onMediaClicked(media)
                        clickedMedia = media
                        clickedHexCell = null
                        // Trigger focus request with unrotated bounds for selected media
                        geometryReader.getMediaBounds(media)?.let { bounds ->
                            onFocusRequested(bounds)
                        }
                    } else {
                        geometryReader.getHexCellAtPosition(transformedPos)?.let { cell ->
                            onHexCellClicked(cell)
                            clickedHexCell = cell
                            clickedMedia = null
                            // Trigger focus request
                            geometryReader.getHexCellBounds(cell)?.let { bounds ->
                                onFocusRequested(bounds)
                            }
                        }
                    }
                }
            }
    ) {
        val zoom = provideZoom()
        val offset = provideOffset()
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        withTransform({
            scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
            translate(offset.x / clampedZoom, offset.y / clampedZoom)
        }) {
            // Store hex cell bounds for hit testing (world coordinates, same as before)
            hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                geometryReader.storeHexCellBounds(hexCellWithMedia.hexCell)
            }

            hexGridRenderer.drawHexGrid(
                drawScope = this,
                hexGrid = hexGridLayout.hexGrid,
                zoom = 1f,
                offset = Offset.Zero
            )

            hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                hexCellWithMedia.mediaItems.forEach { mediaWithPosition ->
                    // Get or create animatable item - this becomes our primary canvas item
                    val animatableItem = animationManager.getOrCreateAnimatable(mediaWithPosition)

                    geometryReader.storeMediaBounds(
                        media = animatableItem.mediaWithPosition.media,
                        bounds = animatableItem.mediaWithPosition.absoluteBounds, // World coordinates
                        hexCell = hexCellWithMedia.hexCell
                    )

                    drawAnimatableMediaFromAtlas(
                        animatableItem = animatableItem,
                        atlasState = atlasState,
                        zoom = zoom
                    )
                    geometryReader.debugDrawBounds(this, zoom)
                }
            }

            clickedMedia?.let { media ->
                geometryReader.getMediaBounds(media)?.let { bounds ->
                    drawRect(
                        color = Color.Red,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        style = Stroke(width = 1.dp.toPx() / zoom)
                    )
                }
            }

            clickedHexCell?.let { hexCell ->
                geometryReader.getHexCellBounds(hexCell)?.let { bounds ->
                    drawPath(
                        path = Path().apply {
                            hexCell.vertices.firstOrNull()?.let { first ->
                                moveTo(first.x, first.y)
                            }
                            hexCell.vertices.forEach { vertex ->
                                lineTo(vertex.x, vertex.y)
                            }
                            close()
                        },
                        color = Color.Green,
                        style = Stroke(width = 1.dp.toPx() / zoom)
                    )
                }
            }
        }

        ripplePosition?.let {
            val color = when {
                clickedMedia != null -> Color.Yellow
                clickedHexCell != null -> Color.Green
                else -> Color.Gray
            }
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = 30f,
                center = it,
                style = Stroke(width = 3f)
            )
            ripplePosition = null
        }
    }
}

/**
 * Calculates which hex cells are visible in the current viewport.
 * This is called from LaunchedEffect and has access to current zoom/offset/viewport state.
 */
private fun calculateVisibleCells(
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    zoom: Float,
    offset: Offset
): List<dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia> {
    // Simple approach: return all cells for now
    // TODO: Implement actual viewport intersection based on zoom/offset
    return hexGridLayout.hexCellsWithMedia
}

/**
 * Draws animatable media from atlas texture or falls back to colored rectangle.
 */
private fun DrawScope.drawAnimatableMediaFromAtlas(
    animatableItem: AnimatableMediaItem,
    atlasState: MultiAtlasUpdateResult?,
    zoom: Float
) {
    val media = animatableItem.mediaWithPosition.media
    val bounds = animatableItem.mediaWithPosition.absoluteBounds
    val rotationAngle = animatableItem.currentRotation

    drawMediaFromAtlas(media, bounds, rotationAngle, atlasState, zoom)
}

/**
 * Draws media from atlas texture or falls back to colored rectangle.
 */
private fun DrawScope.drawMediaFromAtlas(
    media: Media,
    bounds: Rect,
    rotationAngle: Float,
    atlasState: MultiAtlasUpdateResult?,
    zoom: Float
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
                            srcOffset = androidx.compose.ui.unit.IntOffset(
                                foundRegion.atlasRect.left.toInt(),
                                foundRegion.atlasRect.top.toInt()
                            ),
                            srcSize = androidx.compose.ui.unit.IntSize(
                                foundRegion.atlasRect.width.toInt(),
                                foundRegion.atlasRect.height.toInt()
                            ),
                            bounds = bounds,
                            zoom = zoom
                        )
                    } else {
                        // Bitmap was recycled, fall back to placeholder
                        drawPlaceholderRect(media, bounds, Color.Gray, zoom)
                    }
                } else {
                    // Fallback: No atlas contains this photo
                    drawPlaceholderRect(media, bounds, Color.Gray, zoom)
                }
            }

            else -> {
                // Fallback: No atlas available, draw colored rectangle
                drawPlaceholderRect(media, bounds, zoom = zoom)
            }
        }
    }
}

/**
 * Draws a styled placeholder for media with same physical photo appearance.
 */
private fun DrawScope.drawPlaceholderRect(
    media: Media,
    bounds: Rect,
    overrideColor: Color? = null,
    zoom: Float = 1f
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
        color = Color.Black.copy(alpha = 0.15f),
        topLeft = bounds.topLeft + Offset(shadowOffset, shadowOffset),
        size = bounds.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCornerRadius)
    )

    // 2. Draw white border (photo background)
    drawRoundRect(
        color = Color.White,
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCornerRadius)
    )

    // 3. Draw the colored placeholder within the border
    val imageArea = Rect(
        left = bounds.left + borderWidth,
        top = bounds.top + borderWidth,
        right = bounds.right - borderWidth,
        bottom = bounds.bottom - borderWidth
    )

    drawRoundRect(
        color = color,
        topLeft = imageArea.topLeft,
        size = imageArea.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(innerCornerRadius)
    )

    // 4. Draw hairline dark gray border around the entire photo
    drawRoundRect(
        color = Color(0xFF666666),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCornerRadius),
        style = Stroke(width = Stroke.HairlineWidth / zoom)
    )
}

/**
 * Draws a styled photo with realistic physical photo appearance:
 * - Subtle contact shadow
 * - White border
 * - Rounded corners
 */
private fun DrawScope.drawStyledPhoto(
    image: androidx.compose.ui.graphics.ImageBitmap,
    srcOffset: androidx.compose.ui.unit.IntOffset,
    srcSize: androidx.compose.ui.unit.IntSize,
    bounds: Rect,
    zoom: Float
) {
    val borderWidth = 2.dp.toPx()
    val innerCornerRadius = 0.1.dp.toPx()  // Increased for smoother corners
    val outerCornerRadius = innerCornerRadius + borderWidth
    val shadowOffset = 0.1.dp.toPx()

    // 1. Draw subtle contact shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.4f),
        topLeft = bounds.topLeft + Offset(shadowOffset, shadowOffset),
        size = bounds.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCornerRadius)
    )

    // 2. Draw white border (photo background)
    drawRoundRect(
        color = Color.White,
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCornerRadius),
    )

    // 3. Draw the actual image within the border with rounded corners
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
        dstOffset = androidx.compose.ui.unit.IntOffset(
            imageArea.left.toInt(),
            imageArea.top.toInt()
        ),
        dstSize = androidx.compose.ui.unit.IntSize(
            imageArea.width.toInt(),
            imageArea.height.toInt()
        )
    )

    // 4. Draw hairline dark gray border around the entire photo
    drawRoundRect(
        color = Color(0xFF666666),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCornerRadius),
        style = Stroke(width = Stroke.HairlineWidth / zoom)
    )
}

private fun findAnimatableMediaAtPosition(
    position: Offset,
    hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    animationManager: AnimatableMediaManager
): AnimatableMediaItem? {
    // Iterate in reverse order to check topmost (last drawn) items first
    for (hexCellWithMedia in hexGridLayout.hexCellsWithMedia.reversed()) {
        for (mediaWithPosition in hexCellWithMedia.mediaItems.reversed()) {
            val animatableItem = animationManager.getOrCreateAnimatable(mediaWithPosition)
            val bounds = animatableItem.mediaWithPosition.absoluteBounds
            val rotationAngle = animatableItem.currentRotation // Use current animated rotation

            if (rotationAngle == 0f) {
                if (bounds.contains(position)) {
                    return animatableItem
                }
            } else {
                val center = bounds.center
                val transformedPosition = transformPointWithInverseRotation(
                    point = position,
                    center = center,
                    rotationDegrees = rotationAngle
                )

                if (bounds.contains(transformedPosition)) {
                    return animatableItem
                }
            }
        }
    }
    return null
}

private fun transformPointWithInverseRotation(
    point: Offset,
    center: Offset,
    rotationDegrees: Float
): Offset {
    return with(Matrix()) {
        translate(center.x, center.y)
        rotateZ(-rotationDegrees)
        translate(-center.x, -center.y)
        map(point)
    }
}
