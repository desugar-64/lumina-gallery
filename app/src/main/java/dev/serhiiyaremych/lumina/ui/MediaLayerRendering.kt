package dev.serhiiyaremych.lumina.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.MultiAtlasUpdateResult
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
    
    return remember {
        MediaLayerManager(
            contentLayer = contentLayer,
            selectedLayer = selectedLayer,
            density = density,
            layoutDirection = layoutDirection
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
    private val layoutDirection: androidx.compose.ui.unit.LayoutDirection
) {
    
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
                config.hexGridLayout.hexCellsWithMedia.forEach { hexCellWithMedia ->
                    hexCellWithMedia.mediaItems.forEach { mediaWithPosition ->
                        val animatableItem = config.animationManager.getOrCreateAnimatable(mediaWithPosition)
                        val isSelected = animatableItem.mediaWithPosition.media == config.selectedMedia

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
}