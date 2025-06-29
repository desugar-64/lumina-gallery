package dev.serhiiyaremych.lumina.domain.usecase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import dev.serhiiyaremych.lumina.data.ImageToPack
import dev.serhiiyaremych.lumina.data.PackResult
import dev.serhiiyaremych.lumina.data.ScaleStrategy
import dev.serhiiyaremych.lumina.data.ShelfTexturePacker
import dev.serhiiyaremych.lumina.domain.model.AtlasRegion
import dev.serhiiyaremych.lumina.domain.model.LODLevel
import dev.serhiiyaremych.lumina.domain.model.TextureAtlas
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.createBitmap

/**
 * Generates texture atlases by coordinating photo processing and packing algorithms.
 * Bridges PhotoLODProcessor and TexturePacker to create complete atlas bitmaps.
 */
@Singleton
class AtlasGenerator @Inject constructor(
    private val photoLODProcessor: PhotoLODProcessor
) {

    companion object {
        private const val DEFAULT_ATLAS_SIZE = 2048
        private const val ATLAS_PADDING = 2
    }

    /**
     * Generates a texture atlas from a list of photo URIs
     */
    suspend fun generateAtlas(
        photoUris: List<Uri>,
        lodLevel: LODLevel,
        atlasSize: IntSize = IntSize(DEFAULT_ATLAS_SIZE, DEFAULT_ATLAS_SIZE),
        scaleStrategy: ScaleStrategy = ScaleStrategy.FIT_CENTER
    ): AtlasGenerationResult {
        if (photoUris.isEmpty()) {
            return AtlasGenerationResult(
                atlas = null,
                failed = emptyList(),
                packingUtilization = 0f,
                totalPhotos = 0,
                processedPhotos = 0
            )
        }

        val processedPhotos = mutableListOf<ProcessedPhoto>()
        val failed = mutableListOf<Uri>()

        // Step 1: Process all photos for the specified LOD level
        for (uri in photoUris) {
            try {
                val processed = photoLODProcessor.processPhotoForLOD(uri, lodLevel, scaleStrategy)
                if (processed != null) {
                    processedPhotos.add(processed)
                } else {
                    failed.add(uri)
                }
            } catch (e: Exception) {
                // Log error and add to failed list
                failed.add(uri)
            }
        }

        if (processedPhotos.isEmpty()) {
            return AtlasGenerationResult(
                atlas = null,
                failed = failed,
                packingUtilization = 0f,
                totalPhotos = photoUris.size,
                processedPhotos = 0
            )
        }

        // Step 2: Create packing input from processed photos
        val imagesToPack = processedPhotos.mapIndexed { index, processedPhoto ->
            ImageToPack(
                id = index.toString(), // Simple index-based ID
                size = processedPhoto.scaledSize
            )
        }

        // Step 3: Calculate optimal packing layout
        val texturePacker = ShelfTexturePacker(atlasSize, ATLAS_PADDING)
        val packResult = texturePacker.pack(imagesToPack)

        if (packResult.packedImages.isEmpty()) {
            // No images fit in atlas
            return AtlasGenerationResult(
                atlas = null,
                failed = photoUris,
                packingUtilization = 0f,
                totalPhotos = photoUris.size,
                processedPhotos = processedPhotos.size
            )
        }

        // Step 4: Create atlas bitmap and draw photos
        val atlasBitmap = createAtlasBitmap(processedPhotos, packResult, atlasSize)

        // Step 5: Create atlas regions from packed images
        val atlasRegions = createAtlasRegions(processedPhotos, packResult, lodLevel, photoUris)

        // Step 6: Clean up processed photo bitmaps
        processedPhotos.forEach { processedPhoto ->
            if (!processedPhoto.bitmap.isRecycled) {
                processedPhoto.bitmap.recycle()
            }
        }

        // Step 7: Add failed photos from packing stage
        val packingFailed = packResult.failed.mapNotNull { failedPack ->
            val index = failedPack.id.toIntOrNull()
            if (index != null && index < photoUris.size) {
                photoUris[index]
            } else {
                null
            }
        }

        val allFailed = failed + packingFailed

        val atlas = TextureAtlas(
            bitmap = atlasBitmap,
            regions = atlasRegions.associateBy { it.photoId },
            lodLevel = lodLevel.level,
            size = atlasSize
        )

        return AtlasGenerationResult(
            atlas = atlas,
            failed = allFailed,
            packingUtilization = packResult.utilization,
            totalPhotos = photoUris.size,
            processedPhotos = processedPhotos.size
        )
    }

    /**
     * Creates the atlas bitmap and draws all packed photos onto it
     */
    private fun createAtlasBitmap(
        processedPhotos: List<ProcessedPhoto>,
        packResult: PackResult,
        atlasSize: IntSize
    ): Bitmap {
        val atlasBitmap = createBitmap(atlasSize.width, atlasSize.height)

        val canvas = Canvas(atlasBitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true // Enable bilinear filtering
        }

        // Draw each packed photo onto the atlas
        packResult.packedImages.forEach { packedImage ->
            val index = packedImage.id.toIntOrNull()
            if (index != null && index < processedPhotos.size) {
                val processedPhoto = processedPhotos[index]

                if (!processedPhoto.bitmap.isRecycled) {
                    canvas.drawBitmap(
                        processedPhoto.bitmap,
                        null, // source rect (entire bitmap)
                        android.graphics.RectF(
                            packedImage.rect.left,
                            packedImage.rect.top,
                            packedImage.rect.right,
                            packedImage.rect.bottom
                        ),
                        paint
                    )
                }
            }
        }

        return atlasBitmap
    }

    /**
     * Creates AtlasRegion objects from packed images and processed photos
     */
    private fun createAtlasRegions(
        processedPhotos: List<ProcessedPhoto>,
        packResult: PackResult,
        lodLevel: LODLevel,
        originalUris: List<Uri>
    ): List<AtlasRegion> {
        return packResult.packedImages.mapNotNull { packedImage ->
            val index = packedImage.id.toIntOrNull()
            if (index != null && index < processedPhotos.size && index < originalUris.size) {
                val processedPhoto = processedPhotos[index]
                val originalUri = originalUris[index]

                AtlasRegion(
                    photoId = originalUri.toString(), // Use URI as photo ID
                    atlasRect = packedImage.rect,
                    originalSize = processedPhoto.originalSize,
                    scaledSize = processedPhoto.scaledSize,
                    aspectRatio = processedPhoto.aspectRatio,
                    lodLevel = lodLevel.level
                )
            } else {
                null
            }
        }
    }
}

/**
 * Result of atlas generation operation
 */
data class AtlasGenerationResult(
    /**
     * Generated texture atlas, null if generation failed completely
     */
    val atlas: TextureAtlas?,

    /**
     * List of photo URIs that failed to process or fit in atlas
     */
    val failed: List<Uri>,

    /**
     * Packing efficiency (used area / total atlas area)
     */
    val packingUtilization: Float,

    /**
     * Total number of photos attempted
     */
    val totalPhotos: Int,

    /**
     * Number of photos successfully processed (may be more than packed)
     */
    val processedPhotos: Int
) {
    /**
     * Number of photos successfully packed into atlas
     */
    val packedPhotos: Int get() = atlas?.regions?.size ?: 0

    /**
     * Success rate of the operation
     */
    val successRate: Float get() = if (totalPhotos > 0) packedPhotos.toFloat() / totalPhotos else 0f
}
