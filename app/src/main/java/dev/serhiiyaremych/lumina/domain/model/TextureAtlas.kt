package dev.serhiiyaremych.lumina.domain.model

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize

/**
 * Represents a texture atlas containing multiple packed photo regions.
 * Used for efficient batch rendering with Canvas.drawBitmap().
 */
data class TextureAtlas(
    /**
     * The atlas bitmap containing packed photos
     */
    val bitmap: Bitmap,
    
    /**
     * Map of photo ID to atlas region information
     */
    val regions: Map<String, AtlasRegion>,
    
    /**
     * LOD level this atlas represents
     */
    val lodLevel: Int,
    
    /**
     * Size of the atlas bitmap
     */
    val size: IntSize,
    
    /**
     * Timestamp when this atlas was created
     */
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Memory usage of this atlas in bytes (ARGB_8888)
     */
    val memoryUsage: Long get() = size.width.toLong() * size.height * 4
    
    /**
     * Atlas utilization efficiency (used pixels / total pixels)
     */
    val utilization: Float by lazy {
        val usedPixels = regions.values.sumOf { region ->
            region.scaledSize.width * region.scaledSize.height
        }
        val totalPixels = size.width * size.height
        if (totalPixels > 0) usedPixels.toFloat() / totalPixels else 0f
    }
    
    /**
     * Number of photos packed in this atlas
     */
    val photoCount: Int get() = regions.size
    
    /**
     * Get atlas region for a specific photo
     */
    fun getRegion(photoId: String): AtlasRegion? = regions[photoId]
    
    /**
     * Check if this atlas contains a specific photo
     */
    fun containsPhoto(photoId: String): Boolean = regions.containsKey(photoId)
}