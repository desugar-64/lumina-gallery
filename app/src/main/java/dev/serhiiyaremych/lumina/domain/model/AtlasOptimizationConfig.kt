package dev.serhiiyaremych.lumina.domain.model

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize

/**
 * Configuration for atlas optimization based on zoom levels.
 * 
 * This configuration allows customization of atlas texture sizes and bitmap configurations
 * based on zoom level thresholds to optimize memory usage and performance.
 */
data class AtlasOptimizationConfig(
    /**
     * Zoom level threshold below which lower quality atlas settings are used.
     * Default: 1.5f (matches LEVEL_2 upper bound)
     */
    val lowQualityZoomThreshold: Float = 1.5f,
    
    /**
     * Atlas size to use for zoom levels below the threshold.
     * Default: 2048 (2K) for memory efficiency
     */
    val lowQualityAtlasSize: Int = 2048,
    
    /**
     * Bitmap configuration for lower zoom levels.
     * RGB565 uses 2 bytes per pixel vs ARGB_8888's 4 bytes, reducing memory by 50%
     */
    val lowQualityBitmapConfig: Bitmap.Config = Bitmap.Config.RGB_565,
    
    /**
     * Atlas size to use for zoom levels above the threshold.
     * Default: Based on device capabilities
     */
    val highQualityAtlasSize: Int? = null, // null means use device capability-based sizing
    
    /**
     * Bitmap configuration for higher zoom levels.
     * ARGB_8888 provides full quality with alpha channel support
     */
    val highQualityBitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
) {
    
    /**
     * Determines if the given zoom level should use low quality settings
     */
    fun shouldUseLowQuality(zoomLevel: Float): Boolean {
        return zoomLevel <= lowQualityZoomThreshold
    }
    
    /**
     * Gets the appropriate atlas size for the given zoom level
     */
    fun getAtlasSize(zoomLevel: Float, deviceMaxSize: IntSize): IntSize {
        return if (shouldUseLowQuality(zoomLevel)) {
            IntSize(lowQualityAtlasSize, lowQualityAtlasSize)
        } else {
            val size = highQualityAtlasSize ?: deviceMaxSize.width
            IntSize(size, size)
        }
    }
    
    /**
     * Gets the appropriate bitmap configuration for the given zoom level
     */
    fun getBitmapConfig(zoomLevel: Float): Bitmap.Config {
        return if (shouldUseLowQuality(zoomLevel)) {
            lowQualityBitmapConfig
        } else {
            highQualityBitmapConfig
        }
    }
    
    companion object {
        /**
         * Default configuration with 1.5x zoom threshold
         */
        fun default(): AtlasOptimizationConfig = AtlasOptimizationConfig()
        
    }
}