package dev.serhiiyaremych.lumina.data

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.PHOTO_SCALER_SCALE
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.PHOTO_SCALER_CREATE_SCALED_BITMAP
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.PHOTO_SCALER_CREATE_CROPPED_BITMAP
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.PHOTO_SCALER_CALCULATE_DIMENSIONS
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.ATLAS_MEMORY_BITMAP_ALLOCATE
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.ATLAS_MEMORY_BITMAP_RECYCLE
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance photo scaling using Android's hardware-accelerated bilinear filtering.
 *
 * Design Decision: Uses Bitmap.createScaledBitmap with filter=true for hardware-accelerated
 * bilinear filtering instead of Lanczos. While Lanczos provides higher theoretical quality,
 * it is computationally expensive and impractical for pure Kotlin implementation.
 * Android's native bilinear filtering provides excellent quality with optimal performance
 * for thumbnail generation in the atlas system.
 */
@Singleton
class PhotoScaler @Inject constructor() {

    /**
     * Scales a bitmap to the target size using hardware-accelerated bilinear filtering
     */
    fun scale(
        source: Bitmap,
        targetSize: IntSize,
        strategy: ScaleStrategy = ScaleStrategy.FIT_CENTER
    ): Bitmap {
        return trace(PHOTO_SCALER_SCALE) {
            // Return original if already correct size
            if (source.width == targetSize.width && source.height == targetSize.height) {
                return@trace source
            }

            val scaledSize = trace(PHOTO_SCALER_CALCULATE_DIMENSIONS) {
                calculateScaledSize(
                    originalSize = IntSize(source.width, source.height),
                    targetSize = targetSize,
                    strategy = strategy
                )
            }

            when (strategy) {
                ScaleStrategy.FIT_CENTER -> scaleWithAspectRatio(source, scaledSize)
                ScaleStrategy.CENTER_CROP -> scaleAndCrop(source, targetSize)
            }
        }
    }

    private fun scaleWithAspectRatio(source: Bitmap, targetSize: IntSize): Bitmap {
        return trace(PHOTO_SCALER_CREATE_SCALED_BITMAP) {
            trace(ATLAS_MEMORY_BITMAP_ALLOCATE) {
                Bitmap.createScaledBitmap(
                    source,
                    targetSize.width,
                    targetSize.height,
                    true // Enable bilinear filtering for high quality
                )
            }
        }
    }

    /**
     * Scales and crops bitmap to fill target size completely
     */
    private fun scaleAndCrop(source: Bitmap, targetSize: IntSize): Bitmap {
        val sourceAspectRatio = source.width.toFloat() / source.height
        val targetAspectRatio = targetSize.width.toFloat() / targetSize.height

        // Calculate intermediate size to fill target completely
        val intermediateSize = if (sourceAspectRatio > targetAspectRatio) {
            // Source is wider - scale to target height, then crop width
            IntSize(
                width = (targetSize.height * sourceAspectRatio).toInt(),
                height = targetSize.height
            )
        } else {
            // Source is taller - scale to target width, then crop height
            IntSize(
                width = targetSize.width,
                height = (targetSize.width / sourceAspectRatio).toInt()
            )
        }

        // First scale to intermediate size
        val scaledBitmap = trace(PHOTO_SCALER_CREATE_SCALED_BITMAP) {
            trace(ATLAS_MEMORY_BITMAP_ALLOCATE) {
                Bitmap.createScaledBitmap(
                    source,
                    intermediateSize.width,
                    intermediateSize.height,
                    true // Enable bilinear filtering
                )
            }
        }

        // Then crop to exact target size from center
        val cropX = (scaledBitmap.width - targetSize.width) / 2
        val cropY = (scaledBitmap.height - targetSize.height) / 2

        val croppedBitmap = trace(PHOTO_SCALER_CREATE_CROPPED_BITMAP) {
            trace(ATLAS_MEMORY_BITMAP_ALLOCATE) {
                Bitmap.createBitmap(
                    scaledBitmap,
                    cropX,
                    cropY,
                    targetSize.width,
                    targetSize.height
                )
            }
        }

        // Clean up intermediate bitmap if different from result
        if (scaledBitmap != croppedBitmap) {
            trace(ATLAS_MEMORY_BITMAP_RECYCLE) {
                scaledBitmap.recycle()
            }
        }

        return croppedBitmap
    }

    /**
     * Calculates scaled dimensions based on target size and scaling strategy
     */
    private fun calculateScaledSize(
        originalSize: IntSize,
        targetSize: IntSize,
        strategy: ScaleStrategy
    ): IntSize {
        return when (strategy) {
            ScaleStrategy.FIT_CENTER -> {
                // Scale to fit within target while preserving aspect ratio
                val aspectRatio = originalSize.width.toFloat() / originalSize.height
                val targetAspectRatio = targetSize.width.toFloat() / targetSize.height

                if (aspectRatio > targetAspectRatio) {
                    // Source is wider - scale to target width
                    IntSize(
                        width = targetSize.width,
                        height = (targetSize.width / aspectRatio).toInt()
                    )
                } else {
                    // Source is taller - scale to target height
                    IntSize(
                        width = (targetSize.height * aspectRatio).toInt(),
                        height = targetSize.height
                    )
                }
            }
            ScaleStrategy.CENTER_CROP -> {
                // Will be handled by scaleAndCrop method
                targetSize
            }
        }
    }

    /**
     * Downscales a bitmap by a specific factor for memory optimization
     */
    fun downscale(source: Bitmap, scaleFactor: Float): Bitmap {
        if (scaleFactor >= 1.0f) return source

        val targetWidth = (source.width * scaleFactor).toInt()
        val targetHeight = (source.height * scaleFactor).toInt()

        return trace(PHOTO_SCALER_CREATE_SCALED_BITMAP) {
            trace(ATLAS_MEMORY_BITMAP_ALLOCATE) {
                Bitmap.createScaledBitmap(
                    source,
                    targetWidth,
                    targetHeight,
                    true // Enable bilinear filtering
                )
            }
        }
    }

    /**
     * Creates a thumbnail with maximum dimension constraint
     */
    fun createThumbnail(
        source: Bitmap,
        maxDimension: Int,
        strategy: ScaleStrategy = ScaleStrategy.FIT_CENTER
    ): Bitmap {
        val maxCurrentDimension = maxOf(source.width, source.height)

        if (maxCurrentDimension <= maxDimension) {
            return source
        }

        val aspectRatio = source.width.toFloat() / source.height
        val targetSize = if (source.width > source.height) {
            IntSize(
                width = maxDimension,
                height = (maxDimension / aspectRatio).toInt()
            )
        } else {
            IntSize(
                width = (maxDimension * aspectRatio).toInt(),
                height = maxDimension
            )
        }

        return scale(source, targetSize, strategy)
    }
}

/**
 * Scaling strategy for photo resizing
 */
enum class ScaleStrategy {
    /**
     * Maintain aspect ratio, may have empty space.
     * Scales to fit entirely within target dimensions.
     */
    FIT_CENTER,

    /**
     * Fill target dimensions completely, may crop edges.
     * Scales to fill target size, cropping excess content.
     */
    CENTER_CROP
}
