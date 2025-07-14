package dev.serhiiyaremych.lumina.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.layer.CompositingStrategy
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem
import dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaManager

/**
 * Configuration for media layer rendering.
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

    return remember {
        MediaLayerManager(
            contentLayer = contentLayer,
            selectedLayer = selectedLayer,
            density = density,
            layoutDirection = layoutDirection,
            desaturationAnimatable = desaturationAnimatable
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
    private val desaturationAnimatable: Animatable<Float, *>
) {

    // Pre-allocated Android ColorMatrix for desaturation effect to avoid allocations
    private val desaturationMatrix = android.graphics.ColorMatrix()

    companion object {
        private const val DESATURATION_ANIMATION_DURATION = 300 // milliseconds
        private const val DESATURATION_STRENGTH = 0.9f // 50% desaturation
    }

    /**
     * Triggers the desaturation animation based on selection state.
     */
    suspend fun animateDesaturation(hasSelection: Boolean) {
        val targetValue = if (hasSelection) DESATURATION_STRENGTH else 0f
        desaturationAnimatable.animateTo(
            targetValue = targetValue,
            animationSpec = tween(durationMillis = DESATURATION_ANIMATION_DURATION)
        )
    }

    /**
     * Records and draws both content and selected layers with proper layering.
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
        val hasSelection = config.selectedMedia != null

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

        // Draw both layers in order: content first, then selected on top
        drawLayer(contentLayer)
        drawLayer(selectedLayer)
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
                    zoom = 1f,
                    offset = Offset.Zero
                )

                // Draw all non-selected media items
                var selectedMedia: AnimatableMediaItem? = null
                config.hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                    hexCellWithMedia.mediaItems.forEach { mediaWithPosition ->
                        val animatableItem = config.animationManager.getOrCreateAnimatable(mediaWithPosition)
                        val isSelected = animatableItem.mediaWithPosition.media == config.selectedMedia

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

                selectedMedia?.let { selected -> drawSelectionGradient(selected) }
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
                config.selectedMedia?.let { media ->
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
     * Draws a circular gradient that creates a smooth spotlight effect around the selected media item.
     * Uses alpha channel to create smooth fade from transparent center to opaque edges.
     * Radius is 30% bigger than the largest side of the selected item.
     */
    private fun DrawScope.drawSelectionGradient(animatableItem: dev.serhiiyaremych.lumina.ui.animation.AnimatableMediaItem) {
        val bounds = animatableItem.mediaWithPosition.absoluteBounds
        val center = bounds.center

        // Calculate radius as 30% bigger than the largest side of the item
        val maxSide = max(bounds.width, bounds.height)
        val gradientRadius = maxSide * 1.7f / 2f

        // Create radial gradient with alpha channel: transparent center to opaque black edges
        // This creates a smooth spotlight effect rather than a hard cutoff
        val gradient = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0f),    // Center: fully transparent (spotlight effect)
                Color.Black.copy(alpha = 0.9f),
                Color.Black.copy(alpha = 1f),    // Edges: fully opaque (normal content)
                Color.Black.copy(alpha = 1f),    // Edges: fully opaque (normal content)
                Color.Black.copy(alpha = 1f),    // Edges: fully opaque (normal content)
                Color.Black.copy(alpha = 1f),    // Edges: fully opaque (normal content)
                Color.Black.copy(alpha = 1f),    // Edges: fully opaque (normal content)
            ).reversed(),
            center = center,
            radius = gradientRadius,
        )

        // Draw the circular gradient with normal blend mode using alpha channel
        drawCircle(
            brush = gradient,
            radius = gradientRadius,
            center = center,
            blendMode = BlendMode.DstOut
        )
    }
}
