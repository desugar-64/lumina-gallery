package dev.serhiiyaremych.lumina.domain.model

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/**
 * Represents a photo's region within a texture atlas.
 * Contains all information needed to render the photo from the atlas using Canvas.drawBitmap().
 */
data class AtlasRegion(
    /**
     * Unique identifier for the photo
     */
    val photoId: String,
    
    /**
     * Rectangle coordinates within the atlas bitmap (in pixels)
     * Used as source rect for Canvas.drawBitmap()
     */
    val atlasRect: Rect,
    
    /**
     * Original photo dimensions before scaling
     */
    val originalSize: IntSize,
    
    /**
     * Actual size of the photo within the atlas (after scaling)
     */
    val scaledSize: IntSize,
    
    /**
     * Aspect ratio of the photo (width / height)
     * Preserved across all LOD levels
     */
    val aspectRatio: Float,
    
    /**
     * LOD level this region belongs to
     */
    val lodLevel: Int
)