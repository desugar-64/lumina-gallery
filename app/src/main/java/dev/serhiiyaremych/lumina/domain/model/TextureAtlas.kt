package dev.serhiiyaremych.lumina.domain.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.IntSize

/**
 * Represents a texture atlas containing multiple packed photo regions.
 * Used for efficient batch rendering with Canvas.drawBitmap().
 *
 * Uses reactive region system for progressive loading - photos start as null
 * and become available as they are processed and drawn to the atlas bitmap.
 */
data class TextureAtlas(
    /**
     * The atlas bitmap containing packed photos
     */
    val bitmap: Bitmap,

    /**
     * Reactive map of photo ID to atlas region states for immediate UI updates.
     * Each MutableState starts as null and gets populated during atlas generation.
     * UI reads from these states for automatic recomposition when photos become ready.
     */
    val reactiveRegions: MutableMap<Uri, MutableState<AtlasRegion?>> = mutableMapOf(),

    /**
     * LOD level this atlas represents
     */
    val lodLevel: LODLevel,

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
     * Calculated from currently available reactive regions
     */
    val utilization: Float get() {
        val availableRegions = reactiveRegions.values.mapNotNull { it.value }
        val usedPixels = availableRegions.sumOf { region ->
            region.scaledSize.width * region.scaledSize.height
        }
        val totalPixels = size.width * size.height
        return if (totalPixels > 0) usedPixels.toFloat() / totalPixels else 0f
    }

    /**
     * Number of photos packed in this atlas (reactive regions that are currently available)
     */
    val photoCount: Int get() = reactiveRegions.values.count { it.value != null }

    /**
     * Total number of photos that will be in this atlas when fully loaded
     */
    val totalPhotoSlots: Int get() = reactiveRegions.size

    /**
     * Get atlas region for a specific photo from reactive regions
     */
    fun getRegion(photoId: Uri): AtlasRegion? = reactiveRegions[photoId]?.value

    /**
     * Check if this atlas contains a specific photo (has slot for it)
     */
    fun containsPhoto(photoId: Uri): Boolean = reactiveRegions.containsKey(photoId)

    /**
     * Check if a specific photo is loaded and available
     */
    fun isPhotoAvailable(photoId: Uri): Boolean = reactiveRegions[photoId]?.value != null
}
