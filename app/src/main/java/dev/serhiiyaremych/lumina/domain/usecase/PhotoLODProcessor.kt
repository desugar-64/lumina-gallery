package dev.serhiiyaremych.lumina.domain.usecase

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale

/**
 * Processes photos for Level-of-Detail (LOD) atlas system.
 * Handles loading and scaling photos to appropriate resolutions.
 */
@Singleton
class PhotoLODProcessor @Inject constructor(
    private val contentResolver: ContentResolver
) {

    /**
     * Loads and scales a photo to the specified LOD level
     */
    suspend fun processPhotoForLOD(
        photoUri: Uri,
        lodLevel: LODLevel,
        resizeStrategy: ResizeStrategy = ResizeStrategy.FIT_CENTER
    ): ProcessedPhoto? {
        return try {
            // Load original photo with efficient memory usage
            val originalBitmap = loadOriginalPhoto(photoUri) ?: return null

            // Calculate scaled dimensions
            val scaledSize = calculateScaledSize(
                originalSize = IntSize(originalBitmap.width, originalBitmap.height),
                targetSize = lodLevel.resolution,
                strategy = resizeStrategy
            )

            // Scale bitmap to LOD resolution
            val scaledBitmap = scaleToLOD(originalBitmap, scaledSize)

            // Clean up original bitmap if different from scaled
            if (scaledBitmap != originalBitmap) {
                originalBitmap.recycle()
            }

            ProcessedPhoto(
                bitmap = scaledBitmap,
                originalSize = IntSize(originalBitmap.width, originalBitmap.height),
                scaledSize = scaledSize,
                aspectRatio = scaledSize.width.toFloat() / scaledSize.height,
                lodLevel = lodLevel.level
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads original photo from URI with memory optimization
     */
    private fun loadOriginalPhoto(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // First, decode bounds to get dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // Reset stream and decode with calculated sample size
                contentResolver.openInputStream(uri)?.use { inputStream2 ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inSampleSize = calculateInSampleSize(options, 2048, 2048) // Max 2048px
                    }
                    BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
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
     * Calculates scaled dimensions based on LOD level and resize strategy
     */
    private fun calculateScaledSize(
        originalSize: IntSize,
        targetSize: Int,
        strategy: ResizeStrategy
    ): IntSize {
        val aspectRatio = originalSize.width.toFloat() / originalSize.height

        return when (strategy) {
            ResizeStrategy.FIT_CENTER -> {
                // Scale to fit within target size while preserving aspect ratio
                if (originalSize.width > originalSize.height) {
                    IntSize(
                        width = targetSize,
                        height = (targetSize / aspectRatio).toInt()
                    )
                } else {
                    IntSize(
                        width = (targetSize * aspectRatio).toInt(),
                        height = targetSize
                    )
                }
            }
            ResizeStrategy.CENTER_CROP -> {
                // Scale to fill target size, may crop edges
                IntSize(targetSize, targetSize)
            }
        }
    }

    /**
     * Scales bitmap to target size using high-quality filtering
     */
    private fun scaleToLOD(original: Bitmap, targetSize: IntSize): Bitmap {
        return if (original.width == targetSize.width && original.height == targetSize.height) {
            original
        } else {
            original.scale(targetSize.width, targetSize.height)
        }
    }
}

/**
 * Resize strategy for photo scaling
 */
enum class ResizeStrategy {
    /**
     * Maintain aspect ratio, may have empty space
     */
    FIT_CENTER,

    /**
     * Fill target size, may crop edges
     */
    CENTER_CROP
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
