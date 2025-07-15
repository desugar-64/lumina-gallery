package dev.serhiiyaremych.lumina.domain.usecase

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
import dev.serhiiyaremych.lumina.data.BitmapPool
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/**
 * Processes photos for Level-of-Detail (LOD) atlas system.
 * Handles loading and scaling photos to appropriate resolutions.
 */
@Singleton
class PhotoLODProcessor @Inject constructor(
    private val contentResolver: ContentResolver,
    private val photoScaler: PhotoScaler,
    private val bitmapPool: BitmapPool
) {

    /**
     * Loads and processes a photo to the specified LOD level
     * OPTIMIZED: LOD-aware loading eliminates redundant scaling operations
     */
    suspend fun processPhotoForLOD(
        photoUri: Uri,
        lodLevel: LODLevel,
        scaleStrategy: ScaleStrategy = ScaleStrategy.FIT_CENTER,
        priority: dev.serhiiyaremych.lumina.domain.model.PhotoPriority = dev.serhiiyaremych.lumina.domain.model.PhotoPriority.NORMAL
    ): ProcessedPhoto? {
        return trace(BenchmarkLabels.PHOTO_LOD_PROCESS_PHOTO) {
            // Load photo with LOD-aware sample size (already at target resolution)
            val (lodBitmap, originalSize) = trace(BenchmarkLabels.PHOTO_LOD_LOAD_BITMAP) {
                loadOriginalPhoto(photoUri, lodLevel)
            } ?: return@trace null

            // Store bitmap dimensions after LOD processing
            val processedSize = IntSize(lodBitmap.width, lodBitmap.height)

            ProcessedPhoto(
                id = photoUri,
                bitmap = lodBitmap,
                originalSize = originalSize,
                scaledSize = processedSize,
                aspectRatio = processedSize.width.toFloat() / processedSize.height,
                lodLevel = lodLevel.level,
                priority = priority
            )
        }
    }

    /**
     * Loads original photo from URI with memory optimization and timeout protection
     * Returns both the processed bitmap and the original image dimensions
     * OPTIMIZED: Reads EXIF data first for dimensions + orientation in single I/O operation
     */
    private suspend fun loadOriginalPhoto(uri: Uri, lodLevel: LODLevel): Pair<Bitmap, IntSize>? {
        return withTimeout(10_000) { // 10 second timeout per photo
            android.util.Log.d("PhotoLODProcessor", "Loading photo: $uri")

            // Check cancellation before starting
            currentCoroutineContext().ensureActive()

            // Step 1: Try to read dimensions and orientation from EXIF
            val (finalOriginalSize, finalExifOrientation) = readImageDimensionsAndOrientation(uri)
                ?: return@withTimeout null

            // Check cancellation after dimension reading
            currentCoroutineContext().ensureActive()

            // Step 2: Calculate sample size for LOD-aware loading
            val sampleSize = calculateSampleSizeForDimensions(finalOriginalSize, lodLevel)

            // Step 3: Load and decode the bitmap with calculated sample size
            val bitmap = loadBitmapWithSampleSize(uri, sampleSize)
                ?: return@withTimeout null

            // Step 4: Apply EXIF orientation transformation
            val orientedBitmap = applyExifOrientationWithValue(bitmap, finalExifOrientation)

            // Return both bitmap and original size
            Pair(orientedBitmap, finalOriginalSize)
        }
    }

    /**
     * Reads image dimensions and orientation, trying EXIF first, then falling back to bounds decode
     */
    private suspend fun readImageDimensionsAndOrientation(uri: Uri): Pair<IntSize, Int>? {
        // Try EXIF first
        val exifResult = readExifDimensionsAndOrientation(uri)
        if (exifResult != null) {
            return exifResult
        }

        // Fall back to bounds decode
        return readBoundsDimensionsAndOrientation(uri)
    }

    /**
     * Attempts to read dimensions and orientation from EXIF metadata
     */
    private suspend fun readExifDimensionsAndOrientation(uri: Uri): Pair<IntSize, Int>? {
        return trace(PHOTO_LOD_DISK_OPEN_INPUT_STREAM) {
            contentResolver.openInputStream(uri)
        }?.use { inputStream ->
            currentCoroutineContext().ensureActive()

            try {
                val exif = ExifInterface(inputStream)
                val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

                if (width > 0 && height > 0) {
                    android.util.Log.d("PhotoLODProcessor", "EXIF dimensions: ${width}x${height} for $uri")
                    Pair(IntSize(width, height), orientation)
                } else {
                    null // EXIF doesn't contain dimensions, fall back to bounds decode
                }
            } catch (e: Exception) {
                android.util.Log.d("PhotoLODProcessor", "EXIF reading failed for $uri: ${e.message}")
                null // Fall back to bounds decode
            }
        }
    }

    /**
     * Reads dimensions using bounds decode as fallback when EXIF fails
     */
    private suspend fun readBoundsDimensionsAndOrientation(uri: Uri): Pair<IntSize, Int>? {
        android.util.Log.d("PhotoLODProcessor", "Falling back to bounds decode for: $uri")

        return trace(PHOTO_LOD_DISK_OPEN_INPUT_STREAM) {
            contentResolver.openInputStream(uri)
        }?.use { inputStream ->
            currentCoroutineContext().ensureActive()

            // MEMORY I/O: Decode image header for dimensions (no full decode)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            android.util.Log.d("PhotoLODProcessor", "Decoding bounds for: $uri")
            trace(PHOTO_LOD_MEMORY_DECODE_BOUNDS) {
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            currentCoroutineContext().ensureActive()
            android.util.Log.d(
                "PhotoLODProcessor",
                "Photo dimensions: ${options.outWidth}x${options.outHeight} for $uri"
            )

            Pair(IntSize(options.outWidth, options.outHeight), ExifInterface.ORIENTATION_NORMAL)
        }
    }

    /**
     * Calculates sample size for the given dimensions and LOD level
     */
    private fun calculateSampleSizeForDimensions(originalSize: IntSize, lodLevel: LODLevel): Int {
        // Create BitmapFactory.Options with dimensions for sample size calculation
        val options = BitmapFactory.Options().apply {
            outWidth = originalSize.width
            outHeight = originalSize.height
        }

        // MEMORY I/O: Calculate LOD-aware sample size for better memory efficiency
        val sampleSize = trace(PHOTO_LOD_MEMORY_SAMPLE_SIZE_CALC) {
            calculateLODAwareSampleSize(options, lodLevel)
        }

        android.util.Log.d(
            "PhotoLODProcessor",
            "Sample size: $sampleSize for dimensions ${originalSize.width}x${originalSize.height}"
        )
        return sampleSize
    }

    /**
     * Loads bitmap with the specified sample size
     */
    private suspend fun loadBitmapWithSampleSize(
        uri: Uri,
        sampleSize: Int
    ): Bitmap? = trace(PHOTO_LOD_DISK_OPEN_INPUT_STREAM) {
        contentResolver.openInputStream(uri)
    }?.use { inputStream ->
        currentCoroutineContext().ensureActive()

        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize
        }

        android.util.Log.d("PhotoLODProcessor", "Starting full bitmap decode for: $uri")

        // MEMORY I/O: Full bitmap decode with direct allocation
        val bitmap = trace(PHOTO_LOD_MEMORY_DECODE_BITMAP) {
            trace(ATLAS_MEMORY_BITMAP_ALLOCATE) {
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        }

        android.util.Log.d(
            "PhotoLODProcessor",
            "Successfully loaded photo: $uri, bitmap: ${bitmap?.width}x${bitmap?.height}"
        )
        bitmap
    }

    /**
     * Calculates LOD-aware sample size for better memory efficiency
     * OPTIMIZED: Uses LOD resolution with 20% buffer for better quality
     */
    private fun calculateLODAwareSampleSize(
        options: BitmapFactory.Options,
        lodLevel: LODLevel
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        val maxDimension = maxOf(height, width)

        val targetDimension = lodLevel.resolution

        if (maxDimension <= targetDimension) {
            return 1 // No sampling needed
        }

        // Calculate optimal sample size - find the largest power of 2 that keeps quality
        return Integer.highestOneBit(maxDimension / targetDimension).coerceAtLeast(1)
    }

    /**
     * Apply EXIF orientation to bitmap using pre-read orientation value
     * OPTIMIZED: Uses pre-read orientation to avoid additional I/O operations
     */
    private suspend fun applyExifOrientationWithValue(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        return try {
            // Calculate rotation matrix based on EXIF orientation
            val matrix = when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
                ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
                ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }

                else -> null // ORIENTATION_NORMAL - no transformation needed
            }

            // Apply transformation if needed
            if (matrix != null) {
                android.util.Log.d("PhotoLODProcessor", "Applying EXIF orientation $exifOrientation")

                val orientedBitmap = trace(ATLAS_MEMORY_BITMAP_ALLOCATE) {
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

                // Recycle original bitmap since we created a new one
                if (orientedBitmap != bitmap) {
                    trace(ATLAS_MEMORY_BITMAP_RECYCLE) {
                        bitmap.recycle()
                    }
                }

                android.util.Log.d(
                    "PhotoLODProcessor",
                    "EXIF orientation applied: ${bitmap.width}x${bitmap.height} -> ${orientedBitmap.width}x${orientedBitmap.height}"
                )
                orientedBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            // If EXIF processing fails, return original bitmap
            android.util.Log.w("PhotoLODProcessor", "Failed to apply EXIF orientation: ${e.message}")
            bitmap
        }
    }

    /**
     * Apply EXIF orientation to bitmap for correct display
     * OPTIMIZED: Only applies rotation when needed, preserves original bitmap when possible
     * @deprecated Use applyExifOrientationWithValue for better performance
     */
    private suspend fun applyExifOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val exifOrientation = contentResolver.openInputStream(uri)?.use { inputStream: java.io.InputStream ->
                val exif = ExifInterface(inputStream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            applyExifOrientationWithValue(bitmap, exifOrientation)
        } catch (e: Exception) {
            // If EXIF processing fails, return original bitmap
            android.util.Log.w("PhotoLODProcessor", "Failed to apply EXIF orientation for $uri: ${e.message}")
            bitmap
        }
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
    val id: Uri,
    val bitmap: Bitmap,
    val originalSize: IntSize,
    val scaledSize: IntSize,
    val aspectRatio: Float,
    val lodLevel: Int,
    val priority: dev.serhiiyaremych.lumina.domain.model.PhotoPriority = dev.serhiiyaremych.lumina.domain.model.PhotoPriority.NORMAL
)
