package dev.serhiiyaremych.lumina.domain.model

/**
 * Defines Level-of-Detail (LOD) configuration for atlas texture system.
 * Enhanced 8-level system for optimal gallery experience from overview to near-fullscreen.
 * Each LOD level corresponds to a specific resolution and zoom range for optimal performance.
 */
enum class LODLevel(
    val level: Int,
    val resolution: Int,
    val zoomRange: ClosedFloatingPointRange<Float>
) {
    /**
     * Ultra-tiny previews - very distant view, entire photo array visible
     * Photos appear as tiny dots, just enough to identify presence
     * Memory per photo: ~4KB, Atlas capacity: ~1,000 photos per 2K atlas
     */
    LEVEL_0(level = 0, resolution = 32, zoomRange = 0.0f..0.3f),
    
    /**
     * Tiny thumbnails - start zooming in, can recognize general photo picture
     * Begin to distinguish photo content and basic composition
     * Memory per photo: ~16KB, Atlas capacity: ~250 photos per 2K atlas
     */
    LEVEL_1(level = 1, resolution = 64, zoomRange = 0.3f..0.8f),
    
    /**
     * Small thumbnails - standard gallery browsing level
     * Clear photo content recognition, good for general browsing
     * Memory per photo: ~64KB, Atlas capacity: ~64 photos per 2K atlas
     */
    LEVEL_2(level = 2, resolution = 128, zoomRange = 0.8f..1.5f),
    
    /**
     * Medium thumbnails - enhanced detail level
     * Better quality for closer inspection, smooth transition
     * Memory per photo: ~147KB, Atlas capacity: ~28 photos per 2K atlas
     */
    LEVEL_3(level = 3, resolution = 192, zoomRange = 1.5f..2.5f),
    
    /**
     * Large thumbnails - face recognition level
     * Can recognize faces and fine details clearly
     * Memory per photo: ~256KB, Atlas capacity: ~16 photos per 2K atlas
     */
    LEVEL_4(level = 4, resolution = 256, zoomRange = 2.5f..4.0f),
    
    /**
     * High-quality thumbnails - focused examination
     * High quality for detailed photo inspection
     * Memory per photo: ~590KB, Atlas capacity: ~7 photos per 2K atlas
     */
    LEVEL_5(level = 5, resolution = 384, zoomRange = 4.0f..6.5f),
    
    /**
     * Focused view - photos covering significant screen area
     * High quality for focused photo examination
     * Memory per photo: ~1MB, Atlas capacity: ~4 photos per 2K atlas
     */
    LEVEL_6(level = 6, resolution = 512, zoomRange = 6.5f..10.0f),
    
    /**
     * Near-fullscreen - fullscreen-like preview experience
     * Photo covers significant portion of screen, optimal quality
     * Memory per photo: ~2.3MB, Atlas capacity: ~1-2 photos per 2K atlas
     */
    LEVEL_7(level = 7, resolution = 768, zoomRange = 10.0f..16.0f);
    
    companion object {
        /**
         * Determines the appropriate LOD level for a given zoom factor
         */
        fun forZoom(zoom: Float): LODLevel {
            return values().firstOrNull { zoom in it.zoomRange } ?: LEVEL_7
        }
        
        /**
         * Get LOD level by numeric level value
         */
        fun fromLevel(level: Int): LODLevel? {
            return values().firstOrNull { it.level == level }
        }
        
        /**
         * Get all LOD levels in order of increasing quality
         */
        fun getAllLevels(): List<LODLevel> {
            return values().sortedBy { it.level }
        }
        
        /**
         * Get memory usage per photo for a given LOD level (in KB)
         * 
         * Calculation based on:
         * - Android default bitmap configuration: ARGB_8888 (4 bytes per pixel)
         * - Square bitmap dimensions: resolution × resolution × 4 bytes
         * - No compression applied
         * 
         * Examples:
         * - LEVEL_0 (32px): 32 × 32 × 4 = 4,096 bytes = 4KB
         * - LEVEL_3 (192px): 192 × 192 × 4 = 147,456 bytes = 147KB
         * - LEVEL_4 (256px): 256 × 256 × 4 = 262,144 bytes = 256KB
         * - LEVEL_7 (768px): 768 × 768 × 4 = 2,359,296 bytes = 2.3MB
         */
        fun getMemoryUsageKB(lodLevel: LODLevel): Int {
            val resolution = lodLevel.resolution
            val bytesPerPixel = 4 // ARGB_8888
            val totalBytes = resolution * resolution * bytesPerPixel
            return totalBytes / 1024 // Convert to KB
        }
        
        /**
         * Get estimated atlas capacity for a given LOD level in a 2K atlas (2048×2048)
         * 
         * Calculation based on:
         * - Atlas size: 2048 × 2048 pixels
         * - Packing efficiency: ~80% (accounting for padding and shelf packing algorithm)
         * - Photo dimensions: LOD resolution × LOD resolution
         * 
         * Formula: (atlas_area × packing_efficiency) / photo_area
         * Examples:
         * - LEVEL_2 (128px): (2048² × 0.8) / 128² ≈ 64 photos
         * - LEVEL_3 (192px): (2048² × 0.8) / 192² ≈ 28 photos
         * - LEVEL_5 (384px): (2048² × 0.8) / 384² ≈ 7 photos
         */
        fun getAtlasCapacity2K(lodLevel: LODLevel): Int {
            val atlasSize = 2048
            val packingEfficiency = 0.8f
            val photoArea = lodLevel.resolution * lodLevel.resolution
            val atlasArea = atlasSize * atlasSize
            return ((atlasArea * packingEfficiency) / photoArea).toInt()
        }
    }
}