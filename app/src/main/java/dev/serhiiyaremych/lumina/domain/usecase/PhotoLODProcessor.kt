package dev.serhiiyaremych.lumina.domain.usecase

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.unit.IntSize
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
        return try {
            // Load original photo with efficient memory usage
            val originalBitmap = loadOriginalPhoto(photoUri) ?: return null

            // Scale bitmap to LOD resolution using PhotoScaler
            val targetSize = calculateTargetSize(
                originalSize = IntSize(originalBitmap.width, originalBitmap.height),
                lodResolution = lodLevel.resolution,
                strategy = scaleStrategy
            )
            
            val scaledBitmap = photoScaler.scale(
                source = originalBitmap,
                targetSize = targetSize,
                strategy = scaleStrategy
            )

            // Clean up original bitmap if different from scaled
            if (scaledBitmap != originalBitmap) {
                originalBitmap.recycle()
            }

            ProcessedPhoto(
                bitmap = scaledBitmap,
                originalSize = IntSize(originalBitmap.width, originalBitmap.height),
                scaledSize = targetSize,
                aspectRatio = targetSize.width.toFloat() / targetSize.height,
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
