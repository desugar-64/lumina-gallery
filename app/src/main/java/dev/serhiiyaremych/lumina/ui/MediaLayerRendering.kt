package dev.serhiiyaremych.lumina.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.layer.CompositingStrategy
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaManager

/**
 * Configuration for media layer rendering (legacy).
 */
data class MediaLayerConfig(
    val hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    val hexGridRenderer: HexGridRenderer,
    val animationManager: AnimatableMediaManager,
    val geometryReader: GeometryReader,
    val selectedMedia: Media?,
    val atlasState: MultiAtlasUpdateResult?,
    val zoom: Float
)

/**
 * Configuration for streaming media layer rendering.
 */
data class StreamingMediaLayerConfig(
    val hexGridLayout: dev.serhiiyaremych.lumina.domain.model.HexGridLayout,
    val hexGridRenderer: HexGridRenderer,
    val animationManager: AnimatableMediaManager,
    val geometryReader: GeometryReader,
    val selectedMedia: Media?,
    val streamingAtlases: Map<dev.serhiiyaremych.lumina.domain.model.LODLevel, List<dev.serhiiyaremych.lumina.domain.model.TextureAtlas>>?,
    val zoom: Float
)

/**
 * Manager for GraphicsLayers used in media hex visualization.
 * Handles layer creation, recording, and drawing coordination.
 */
@Composable
fun rememberMediaLayers(): MediaLayerManager {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val contentLayer = rememberGraphicsLayer()
    val selectedLayer = rememberGraphicsLayer()

    // Animation state for desaturation effect
    val desaturationAnimatable = remember { Animatable(0f) }

    // Animation state for gradient alpha effect
    val gradientAlphaAnimatable = remember { Animatable(0f) }

    return remember {
        MediaLayerManager(
            contentLayer = contentLayer,
            selectedLayer = selectedLayer,
            density = density,
            layoutDirection = layoutDirection,
            desaturationAnimatable = desaturationAnimatable,
            gradientAlphaAnimatable = gradientAlphaAnimatable
        )
    }
}

/**
 * Manages the two GraphicsLayers for content and selected media rendering.
 */
class MediaLayerManager(
    private val contentLayer: GraphicsLayer,
    private val selectedLayer: GraphicsLayer,
    private val density: androidx.compose.ui.unit.Density,
    private val layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    private val desaturationAnimatable: Animatable<Float, *>,
    private val gradientAlphaAnimatable: Animatable<Float, *>
) {

    // Pre-allocated Android ColorMatrix for desaturation effect to avoid allocations
    private val desaturationMatrix = android.graphics.ColorMatrix()

    // Track the item that's currently animating out (for smooth deselection)
    private var pendingDeselectionItem: AnimatableMediaItem? = null
    private var delayedSelectedMedia: Media? = null

    companion object {
        private const val DESATURATION_ANIMATION_DURATION = 300 // milliseconds
        private const val DESATURATION_STRENGTH = 0.9f // 50% desaturation
    }

    /**
     * Triggers both desaturation and gradient alpha animations based on selection state.
     * Implements delayed deselection to ensure smooth fade-out animation without abrupt disappearance.
     *
     * ## How Delayed Deselection Works:
     *
     * ### Problem:
     * When a user deselects an item, `selectedMedia` becomes `null` immediately, causing the
     * gradient to disappear abruptly instead of smoothly fading out.
     *
     * ### Solution:
     * This function maintains a `delayedSelectedMedia` state that keeps the previously selected
     * item alive during the fade-out animation, ensuring smooth visual transitions for the
     * gradient effect.
     *
     * ### Animation Lifecycle:
     *
     * **1. Selection (selectedMedia = SomeMedia)**
     * - `delayedSelectedMedia` = SomeMedia (immediate update)
     * - `gradientAlphaTarget` = 1f (fade in gradient)
     * - `desaturationTarget` = 0.9f (background dims)
     * - Result: Circle grows from center outward over 300ms
     *
     * **2. Deselection (selectedMedia = null)**
     * - `delayedSelectedMedia` = SomeMedia (kept alive for animation)
     * - `gradientAlphaTarget` = 0f (fade out gradient)
     * - `desaturationTarget` = 0f (background restores)
     * - Result: Circle shrinks to center inward over 300ms
     *
     * **3. After Animation Completes**
     * - `delayedSelectedMedia` = null (cleared after fade-out)
     * - Gradient stops rendering naturally
     * - No abrupt disappearance!
     *
     * **4. Selection Switch (selectedMedia = AnotherMedia)**
     * - `delayedSelectedMedia` = AnotherMedia (immediate update)
     * - Previous selection is cleared immediately
     * - Smooth transition between different items
     *
     * ### Technical Implementation:
     * - Uses `coroutineScope` to run desaturation and gradient animations simultaneously
     * - Both animations use the same 300ms duration for perfect synchronization
     * - `getEffectiveSelectedMedia()` returns `selectedMedia ?: delayedSelectedMedia` during rendering
     * - Cleanup happens in the gradient animation's completion callback
     *
     * @param selectedMedia The currently selected media item (null when deselecting)
     */
    suspend fun animateDesaturation(selectedMedia: Media?) {
        val hasSelection = selectedMedia != null
        val desaturationTarget = if (hasSelection) DESATURATION_STRENGTH else 0f
        val gradientAlphaTarget = if (hasSelection) 1f else 0f

        // Update delayed selection state for smooth deselection
        if (hasSelection) {
            // New selection - update immediately and clear any pending deselection
            delayedSelectedMedia = selectedMedia
            pendingDeselectionItem = null
        } else {
            // Deselection - keep the current selection alive for animation
            // Don't clear delayedSelectedMedia until animation completes
        }

        // Animate both effects simultaneously for synchronized transitions
        coroutineScope {
            launch {
                desaturationAnimatable.animateTo(
                    targetValue = desaturationTarget,
                    animationSpec = tween(durationMillis = DESATURATION_ANIMATION_DURATION)
                )
            }
            launch {
                gradientAlphaAnimatable.animateTo(
                    targetValue = gradientAlphaTarget,
                    animationSpec = tween(durationMillis = DESATURATION_ANIMATION_DURATION)
                )

                // Clear delayed selection after fade-out animation completes
                if (!hasSelection) {
                    delayedSelectedMedia = null
                    pendingDeselectionItem = null
                }
            }
        }
    }

    /**
     * Get the media that should be used for rendering (either current or delayed for animation).
     *
     * This function is the key to the delayed deselection mechanism. It ensures that:
     *
     * - **During Selection**: Returns the actual selected media immediately
     * - **During Deselection**: Returns the delayed media to keep gradient visible during fade-out
     * - **After Animation**: Returns null once the fade-out completes
     *
     * ## Usage in Rendering:
     * Both content and selected layers use this function to determine which item should be:
     * - Excluded from desaturation effects (appears bright)
     * - Drawn in the selected layer (appears on top)
     * - Used for gradient rendering (gets the spotlight effect)
     *
     * ## State Transitions:
     * ```
     * Select Item:   actualSelectedMedia = Item → returns Item
     * Deselect Item: actualSelectedMedia = null → returns delayedSelectedMedia (Item)
     * After Fade:    actualSelectedMedia = null → returns null (delayedSelectedMedia cleared)
     * ```
     *
     * @param actualSelectedMedia The current selection state from the UI
     * @return The media item that should be treated as selected for rendering purposes
     */
    fun getEffectiveSelectedMedia(actualSelectedMedia: Media?): Media? {
        return actualSelectedMedia ?: delayedSelectedMedia
    }

    /**
     * Records and draws both content and selected layers with proper layering (legacy).
     */
    fun DrawScope.recordAndDrawLayers(
        config: MediaLayerConfig,
        canvasSize: IntSize,
        zoom: Float,
        offset: Offset
    ) {
        val clampedZoom = zoom.coerceIn(0.01f, 100f)

        // Record content layer with all non-selected media
        recordContentLayer(config, canvasSize, clampedZoom, offset)

        // Record selected layer with only the selected media item
        recordSelectedLayer(config, canvasSize, clampedZoom, offset)

        // Configure content layer composition strategy and effects when there's a selection
        val currentDesaturation = desaturationAnimatable.value
        val currentGradientAlpha = gradientAlphaAnimatable.value
        val effectiveSelectedMedia = getEffectiveSelectedMedia(config.selectedMedia)
        val hasSelection = effectiveSelectedMedia != null

        if (hasSelection) {
            // Set content layer to offscreen compositing for blend mode effects
            contentLayer.compositingStrategy = CompositingStrategy.Offscreen

            // Apply desaturation if needed
            if (currentDesaturation > 0f) {
                applyDesaturationToContentLayer(currentDesaturation)
            }
        } else {
            // Reset to default compositing strategy when no selection
            contentLayer.compositingStrategy = CompositingStrategy.Auto
            contentLayer.colorFilter = null
        }

        // Selected layer always uses default settings (no alpha fade)
        selectedLayer.compositingStrategy = CompositingStrategy.Auto
        selectedLayer.alpha = 1f

        // Draw both layers in order: content first, then selected on top
        drawLayer(contentLayer)
        drawLayer(selectedLayer)
    }

    /**
     * Records and draws both content and selected layers with streaming atlases.
     */
    fun DrawScope.recordAndDrawLayers(
        config: StreamingMediaLayerConfig,
        canvasSize: IntSize,
        zoom: Float,
        offset: Offset
    ) {
        val clampedZoom = zoom.coerceIn(0.01f, 100f)

        // Record content layer with all non-selected media
        recordStreamingContentLayer(config, canvasSize, clampedZoom, offset)

        // Record selected layer with only the selected media item
        recordStreamingSelectedLayer(config, canvasSize, clampedZoom, offset)

        // Configure content layer composition strategy and effects when there's a selection
        val currentDesaturation = desaturationAnimatable.value
        val currentGradientAlpha = gradientAlphaAnimatable.value
        val effectiveSelectedMedia = getEffectiveSelectedMedia(config.selectedMedia)
        val hasSelection = effectiveSelectedMedia != null

        if (hasSelection) {
            // Set content layer to offscreen compositing for blend mode effects
            contentLayer.compositingStrategy = CompositingStrategy.Offscreen

            // Apply desaturation if needed
            if (currentDesaturation > 0f) {
                applyDesaturationToContentLayer(currentDesaturation)
            }
        } else {
            // Reset to default compositing strategy when no selection
            contentLayer.compositingStrategy = CompositingStrategy.Auto
            contentLayer.colorFilter = null
        }

        // Selected layer always uses default settings (no alpha fade)
        selectedLayer.compositingStrategy = CompositingStrategy.Auto
        selectedLayer.alpha = 1f

        // Draw both layers in order: content first, then selected on top
        drawLayer(contentLayer)
        drawLayer(selectedLayer)
    }

    /**
     * Records the streaming content layer with hex grid background and all non-selected media items.
     */
    private fun recordStreamingContentLayer(
        config: StreamingMediaLayerConfig,
        canvasSize: IntSize,
        clampedZoom: Float,
        offset: Offset
    ) {
        contentLayer.record(
            density = density,
            layoutDirection = layoutDirection,
            size = canvasSize
        ) {
            withTransform({
                scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
                translate(offset.x / clampedZoom, offset.y / clampedZoom)
            }) {
                // Store hex cell bounds for hit testing (world coordinates)
                config.hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                    config.geometryReader.storeHexCellBounds(hexCellWithMedia.hexCell)
                }

                // Draw hex grid background
                config.hexGridRenderer.drawHexGrid(
                    drawScope = this,
                    hexGrid = config.hexGridLayout.hexGrid,
                    config = HexRenderConfig(
                        baseStrokeWidth = 1.0.dp,
                        cellPadding = 8.dp,
                        cornerRadius = 8.dp, // Add subtle rounded corners
                        gridColor = Color.Gray.copy(alpha = 0.25f)
                    )
                )

                // Draw all non-selected media items
                var selectedMedia: AnimatableMediaItem? = null
                val effectiveSelectedMedia = getEffectiveSelectedMedia(config.selectedMedia)
                config.hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                    hexCellWithMedia.mediaItems.forEach { mediaWithPosition ->
                        val animatableItem = config.animationManager.getOrCreateAnimatable(mediaWithPosition)
                        val isSelected = animatableItem.mediaWithPosition.media == effectiveSelectedMedia

                        if (isSelected) selectedMedia = animatableItem

                        config.geometryReader.storeMediaBounds(
                            media = animatableItem.mediaWithPosition.media,
                            bounds = animatableItem.mediaWithPosition.absoluteBounds,
                            hexCell = hexCellWithMedia.hexCell
                        )

                        // Only draw non-selected items in content layer
                        if (!isSelected) {
                            drawAnimatableMediaFromStreamingAtlas(
                                animatableItem = animatableItem,
                                streamingAtlases = config.streamingAtlases,
                                zoom = config.zoom
                            )
                        }
                        config.geometryReader.debugDrawBounds(this, config.zoom)
                    }
                }

                // Draw gradient for effective selected media
                selectedMedia?.let { item -> drawSelectionGradient(item) }
            }
        }
    }

    /**
     * Records the streaming selected layer with only the selected media item.
     */
    private fun recordStreamingSelectedLayer(
        config: StreamingMediaLayerConfig,
        canvasSize: IntSize,
        clampedZoom: Float,
        offset: Offset
    ) {
        selectedLayer.record(
            density = density,
            layoutDirection = layoutDirection,
            size = canvasSize
        ) {
            withTransform({
                scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
                translate(offset.x / clampedZoom, offset.y / clampedZoom)
            }) {
                // Draw only the selected media item
                val effectiveSelectedMedia = getEffectiveSelectedMedia(config.selectedMedia)
                effectiveSelectedMedia?.let { media ->
                    config.hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                        hexCellWithMedia.mediaItems.forEach { mediaWithPosition ->
                            val animatableItem = config.animationManager.getOrCreateAnimatable(mediaWithPosition)
                            val isSelected = animatableItem.mediaWithPosition.media == media

                            if (isSelected) {
                                // Draw the selected media item (gradient is now in content layer)
                                drawAnimatableMediaFromStreamingAtlas(
                                    animatableItem = animatableItem,
                                    streamingAtlases = config.streamingAtlases,
                                    zoom = config.zoom
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Records the content layer with hex grid background and all non-selected media items.
     */
    private fun recordContentLayer(
        config: MediaLayerConfig,
        canvasSize: IntSize,
        clampedZoom: Float,
        offset: Offset
    ) {
        contentLayer.record(
            density = density,
            layoutDirection = layoutDirection,
            size = canvasSize
        ) {
            withTransform({
                scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
                translate(offset.x / clampedZoom, offset.y / clampedZoom)
            }) {
                // Store hex cell bounds for hit testing (world coordinates)
                config.hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                    config.geometryReader.storeHexCellBounds(hexCellWithMedia.hexCell)
                }

                // Draw hex grid background
                config.hexGridRenderer.drawHexGrid(
                    drawScope = this,
                    hexGrid = config.hexGridLayout.hexGrid,
                    config = HexRenderConfig(
                        baseStrokeWidth = 1.0.dp,
                        cellPadding = 8.dp,
                        cornerRadius = 8.dp, // Add subtle rounded corners
                        gridColor = Color.Gray.copy(alpha = 0.25f)
                    )
                )

                // Draw all non-selected media items
                var selectedMedia: AnimatableMediaItem? = null
                val effectiveSelectedMedia = getEffectiveSelectedMedia(config.selectedMedia)
                config.hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                    hexCellWithMedia.mediaItems.forEach { mediaWithPosition ->
                        val animatableItem = config.animationManager.getOrCreateAnimatable(mediaWithPosition)
                        val isSelected = animatableItem.mediaWithPosition.media == effectiveSelectedMedia

                        if (isSelected) selectedMedia = animatableItem

                        config.geometryReader.storeMediaBounds(
                            media = animatableItem.mediaWithPosition.media,
                            bounds = animatableItem.mediaWithPosition.absoluteBounds,
                            hexCell = hexCellWithMedia.hexCell
                        )

                        // Only draw non-selected items in content layer
                        if (!isSelected) {
                            drawAnimatableMediaFromAtlas(
                                animatableItem = animatableItem,
                                atlasState = config.atlasState,
                                zoom = config.zoom
                            )
                        }
                        config.geometryReader.debugDrawBounds(this, config.zoom)
                    }
                }

                // Draw gradient for effective selected media
                selectedMedia?.let { item -> drawSelectionGradient(item) }
            }
        }
    }

    /**
     * Records the selected layer with only the selected media item.
     */
    private fun recordSelectedLayer(
        config: MediaLayerConfig,
        canvasSize: IntSize,
        clampedZoom: Float,
        offset: Offset
    ) {
        selectedLayer.record(
            density = density,
            layoutDirection = layoutDirection,
            size = canvasSize
        ) {
            withTransform({
                scale(clampedZoom, clampedZoom, pivot = Offset.Zero)
                translate(offset.x / clampedZoom, offset.y / clampedZoom)
            }) {
                // Draw only the selected media item
                val effectiveSelectedMedia = getEffectiveSelectedMedia(config.selectedMedia)
                effectiveSelectedMedia?.let { media ->
                    config.hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                        hexCellWithMedia.mediaItems.forEach { mediaWithPosition ->
                            val animatableItem = config.animationManager.getOrCreateAnimatable(mediaWithPosition)
                            val isSelected = animatableItem.mediaWithPosition.media == media

                            if (isSelected) {
                                // Draw the selected media item (gradient is now in content layer)
                                drawAnimatableMediaFromAtlas(
                                    animatableItem = animatableItem,
                                    atlasState = config.atlasState,
                                    zoom = config.zoom
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies desaturation effect to the content layer using ColorMatrix.setSaturation().
     *
     * @param desaturationAmount 0f = fully saturated, 1f = fully desaturated
     */
    private fun applyDesaturationToContentLayer(desaturationAmount: Float) {
        // Calculate saturation level: 1f = normal, 0f = completely desaturated
        val saturationLevel = 1f - desaturationAmount

        // Use Android's built-in setSaturation method
        desaturationMatrix.setSaturation(saturationLevel)

        // Convert Android ColorMatrix to Compose ColorFilter
        val colorMatrixFilter = android.graphics.ColorMatrixColorFilter(desaturationMatrix)
        contentLayer.colorFilter = colorMatrixFilter.asComposeColorFilter()
    }

    /**
     * Draws an animated circular gradient that creates a smooth spotlight effect around the selected media item.
     * Uses animated alpha channel to create growing/shrinking circle effect.
     * Radius is 30% bigger than the largest side of the selected item.
     */
    private fun DrawScope.drawSelectionGradient(animatableItem: dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem) {
        // Get current animation progress for growing/shrinking effect
        val currentGradientAlpha = gradientAlphaAnimatable.value

        // Skip drawing if gradient is fully transparent (no selection or animation not started)
        if (currentGradientAlpha <= 0f) return

        val bounds = animatableItem.mediaWithPosition.absoluteBounds
        val center = bounds.center

        // Calculate radius as 30% bigger than the largest side of the item
        val maxSide = max(bounds.width, bounds.height)
        val gradientRadius = maxSide * 1.7f / 2f

        // Create radial gradient with animated alpha values: transparent center to opaque black edges
        // The animation creates a growing/shrinking circle effect as alpha increases/decreases uniformly
        val gradient = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0f),                                    // Center: always transparent (spotlight effect)
                Color.Black.copy(alpha = 0.9f * currentGradientAlpha),           // Animated transition
                Color.Black.copy(alpha = 1f * currentGradientAlpha),             // Edges: animated opacity
                Color.Black.copy(alpha = 1f * currentGradientAlpha),
                Color.Black.copy(alpha = 1f * currentGradientAlpha),
                Color.Black.copy(alpha = 1f * currentGradientAlpha),
                Color.Black.copy(alpha = 1f * currentGradientAlpha),
            ).reversed(),
            center = center,
            radius = gradientRadius,
        )

        // Draw the circular gradient with DstOut blend mode for hole effect
        drawCircle(
            brush = gradient,
            radius = gradientRadius,
            center = center,
            blendMode = BlendMode.DstOut
        )
    }
}
