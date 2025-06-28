package dev.serhiiyaremych.lumina.domain.model

/**
 * Defines Level-of-Detail (LOD) configuration for atlas texture system.
 * Each LOD level corresponds to a specific resolution and zoom range for optimal performance.
 */
enum class LODLevel(
    val level: Int,
    val resolution: Int,
    val zoomRange: ClosedFloatingPointRange<Float>
) {
    /**
     * Farthest zoom level - tiny thumbnails for overview
     * Used when photos appear as small dots on screen
     */
    LEVEL_0(level = 0, resolution = 32, zoomRange = 0.0f..0.5f),
    
    /**
     * Medium zoom level - standard thumbnails
     * Used for normal gallery browsing
     */
    LEVEL_2(level = 2, resolution = 128, zoomRange = 0.5f..2.0f),
    
    /**
     * Close zoom level - high quality previews
     * Used when examining photo details
     */
    LEVEL_4(level = 4, resolution = 512, zoomRange = 2.0f..10.0f);
    
    companion object {
        /**
         * Determines the appropriate LOD level for a given zoom factor
         */
        fun forZoom(zoom: Float): LODLevel {
            return values().firstOrNull { zoom in it.zoomRange } ?: LEVEL_4
        }
        
        /**
         * Get LOD level by numeric level value
         */
        fun fromLevel(level: Int): LODLevel? {
            return values().firstOrNull { it.level == level }
        }
    }
}