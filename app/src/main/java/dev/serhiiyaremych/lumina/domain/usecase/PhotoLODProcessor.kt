package dev.serhiiyaremych.lumina.domain.usecase

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.PHOTO_LOD_DISK_OPEN_INPUT_STREAM
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.PHOTO_LOD_MEMORY_DECODE_BOUNDS
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.PHOTO_LOD_MEMORY_DECODE_BITMAP
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.PHOTO_LOD_MEMORY_SAMPLE_SIZE_CALC
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.ATLAS_MEMORY_BITMAP_ALLOCATE
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.ATLAS_MEMORY_BITMAP_RECYCLE
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import javax.inject.Inject
import javax.inject.Singleton
import dev.serhiiyaremych.lumina.data.PhotoScaler
import dev.serhiiyaremych.lumina.data.ScaleStrategy

/**
 * Processes photos for Level-of-Detail (LOD) atlas system.
 * Handles loading and scaling photos to appropriate resolutions.
 */
@Singleton
class PhotoLODProcessor @Inject constructor(
    private val contentResolver: ContentResolver,
    private val photoScaler: PhotoScaler
) {

    /**
     * Loads and scales a photo to the specified LOD level
     */
    suspend fun processPhotoForLOD(
        photoUri: Uri,
        lodLevel: LODLevel,
        scaleStrategy: ScaleStrategy = ScaleStrategy.FIT_CENTER
    ): ProcessedPhoto? {
        return trace(BenchmarkLabels.PHOTO_LOD_PROCESS_PHOTO) {
            try {
                // Load original photo with efficient memory usage
                val originalBitmap = trace(BenchmarkLabels.PHOTO_LOD_LOAD_BITMAP) {
                    loadOriginalPhoto(photoUri)
                } ?: return@trace null

                // Store original dimensions before any processing
                val originalSize = IntSize(originalBitmap.width, originalBitmap.height)
                
                // Scale bitmap to LOD resolution using PhotoScaler
                val targetSize = calculateTargetSize(
                    originalSize = originalSize,
                    lodResolution = lodLevel.resolution,
                    strategy = scaleStrategy
                )
                
                val scaledBitmap = trace(BenchmarkLabels.PHOTO_LOD_SCALE_BITMAP) {
                    photoScaler.scale(
                        source = originalBitmap,
                        targetSize = targetSize,
                        strategy = scaleStrategy
                    )
                }

                // Clean up original bitmap if different from scaled
                if (scaledBitmap != originalBitmap) {
                    trace(ATLAS_MEMORY_BITMAP_RECYCLE) {
                        originalBitmap.recycle()
                    }
                }

                ProcessedPhoto(
                    bitmap = scaledBitmap,
                    originalSize = originalSize,
                    scaledSize = targetSize,
                    aspectRatio = targetSize.width.toFloat() / targetSize.height,
                    lodLevel = lodLevel.level
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Loads original photo from URI with memory optimization
     */
    private fun loadOriginalPhoto(uri: Uri): Bitmap? {
        return try {
            // DISK I/O: File system access via ContentResolver
            trace(PHOTO_LOD_DISK_OPEN_INPUT_STREAM) {
                contentResolver.openInputStream(uri)
            }?.use { inputStream ->
                // MEMORY I/O: Decode image header for dimensions (no full decode)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                trace(PHOTO_LOD_MEMORY_DECODE_BOUNDS) {
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                // MEMORY I/O: Calculate optimal sample size for memory efficiency
                val sampleSize = trace(PHOTO_LOD_MEMORY_SAMPLE_SIZE_CALC) {
                    calculateInSampleSize(options, 2048, 2048) // Max 2048px
                }

                // DISK I/O: Reset stream and read again for full decode
                trace(PHOTO_LOD_DISK_OPEN_INPUT_STREAM) {
                    contentResolver.openInputStream(uri)
                }?.use { inputStream2 ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inSampleSize = sampleSize
                    }
                    // MEMORY I/O: Full bitmap decode and memory allocation
                    trace(PHOTO_LOD_MEMORY_DECODE_BITMAP) {
                        trace(ATLAS_MEMORY_BITMAP_ALLOCATE) {
                            BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calculates optimal sample size for memory efficiency
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Calculates target size based on LOD resolution and scaling strategy
     */
    private fun calculateTargetSize(
        originalSize: IntSize,
        lodResolution: Int,
        strategy: ScaleStrategy
    ): IntSize {
        val aspectRatio = originalSize.width.toFloat() / originalSize.height

        return when (strategy) {
            ScaleStrategy.FIT_CENTER -> {
                // Scale to fit within LOD resolution while preserving aspect ratio
                if (originalSize.width > originalSize.height) {
                    IntSize(
                        width = lodResolution,
                        height = (lodResolution / aspectRatio).toInt()
                    )
                } else {
                    IntSize(
                        width = (lodResolution * aspectRatio).toInt(),
                        height = lodResolution
                    )
                }
            }
            ScaleStrategy.CENTER_CROP -> {
                // Scale to fill exact LOD resolution, may crop edges
                IntSize(lodResolution, lodResolution)
            }
        }
    }
}


/**
 * Result of photo processing for LOD level
 */
data class ProcessedPhoto(
    val bitmap: Bitmap,
    val originalSize: IntSize,
    val scaledSize: IntSize,
    val aspectRatio: Float,
    val lodLevel: Int
)
