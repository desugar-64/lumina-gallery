package dev.serhiiyaremych.lumina.data

import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.tracing.trace
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.TEXTURE_PACKER_PACK_ALGORITHM
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.TEXTURE_PACKER_SORT_IMAGES
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.TEXTURE_PACKER_PACK_SINGLE_IMAGE
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.TEXTURE_PACKER_FIND_SHELF_FIT
import dev.serhiiyaremych.lumina.common.BenchmarkLabels.TEXTURE_PACKER_CREATE_NEW_SHELF
import dev.serhiiyaremych.lumina.domain.usecase.ProcessedPhoto

/**
 * Shelf packing algorithm for texture atlas generation.
 * Efficiently packs rectangular images into a larger atlas texture.
 */
class ShelfTexturePacker(
    private val atlasSize: IntSize,
    private val padding: Int = 2
) {

    /**
     * Pack images into atlas using shelf packing algorithm
     */
    fun pack(images: List<ImageToPack>): PackResult {
        return trace(TEXTURE_PACKER_PACK_ALGORITHM) {
            val shelves = mutableListOf<Shelf>()
            val packedImages = mutableListOf<PackedImage>()
            val failedImages = mutableListOf<ImageToPack>()

            // Sort images by height (descending) for better packing
            val sortedImages = trace(TEXTURE_PACKER_SORT_IMAGES) {
                images.sortedByDescending { it.size.height }
            }

            for (image in sortedImages) {
                val packedImage = trace(TEXTURE_PACKER_PACK_SINGLE_IMAGE) {
                    packImage(image, shelves)
                }
                if (packedImage != null) {
                    packedImages.add(packedImage)
                } else {
                    failedImages.add(image)
                }
            }

            val utilization = calculateUtilization(packedImages)

            PackResult(
                packedImages = packedImages,
                utilization = utilization,
                failed = failedImages
            )
        }
    }

    /**
     * Try to pack a single image into existing shelves or create a new shelf
     */
    private fun packImage(image: ImageToPack, shelves: MutableList<Shelf>): PackedImage? {
        val imageWithPadding = IntSize(
            width = image.size.width + padding * 2,
            height = image.size.height + padding * 2
        )

        // Check if image fits in atlas at all
        if (imageWithPadding.width > atlasSize.width || imageWithPadding.height > atlasSize.height) {
            return null
        }

        // Try to fit in existing shelves
        for (shelf in shelves) {
            val position = trace(TEXTURE_PACKER_FIND_SHELF_FIT) {
                shelf.tryFit(imageWithPadding)
            }
            if (position != null) {
                return PackedImage(
                    id = image.id,
                    rect = Rect(
                        left = position.x + padding.toFloat(),
                        top = position.y + padding.toFloat(),
                        right = position.x + image.size.width.toFloat() + padding,
                        bottom = position.y + image.size.height.toFloat() + padding
                    ),
                    originalSize = image.size
                )
            }
        }

        // Create new shelf if possible
        val nextShelfY = shelves.sumOf { it.height }
        if (nextShelfY + imageWithPadding.height <= atlasSize.height) {
            val newShelf = trace(TEXTURE_PACKER_CREATE_NEW_SHELF) {
                Shelf(
                    y = nextShelfY,
                    height = imageWithPadding.height,
                    atlasWidth = atlasSize.width
                )
            }
            shelves.add(newShelf)

            val position = trace(TEXTURE_PACKER_FIND_SHELF_FIT) {
                newShelf.tryFit(imageWithPadding)
            }
            if (position != null) {
                return PackedImage(
                    id = image.id,
                    rect = Rect(
                        left = position.x + padding.toFloat(),
                        top = position.y + padding.toFloat(),
                        right = position.x + image.size.width.toFloat() + padding,
                        bottom = position.y + image.size.height.toFloat() + padding
                    ),
                    originalSize = image.size
                )
            }
        }

        return null
    }

    /**
     * Calculate atlas utilization efficiency
     */
    private fun calculateUtilization(packedImages: List<PackedImage>): Float {
        val usedArea = packedImages.sumOf { image ->
            (image.rect.width * image.rect.height).toDouble()
        }
        val totalArea = atlasSize.width.toDouble() * atlasSize.height
        return if (totalArea > 0) (usedArea / totalArea).toFloat() else 0f
    }

    /**
     * Represents a horizontal shelf in the atlas
     */
    private class Shelf(
        val y: Int,
        val height: Int,
        private val atlasWidth: Int
    ) {
        private var currentX = 0

        /**
         * Try to fit an image in this shelf
         */
        fun tryFit(imageSize: IntSize): Position? {
            if (imageSize.height > height) return null
            if (currentX + imageSize.width > atlasWidth) return null

            val position = Position(currentX, y)
            currentX += imageSize.width
            return position
        }
    }

    /**
     * Position within the atlas
     */
    private data class Position(val x: Int, val y: Int)
}

/**
 * Input image to be packed
 */
@JvmInline
value class ImageToPack(val media: ProcessedPhoto) {
    val id get() = media.id
    val size get() = media.scaledSize
}

/**
 * Result of packing operation
 */
data class PackResult(
    val packedImages: List<PackedImage>,
    val utilization: Float,
    val failed: List<ImageToPack> = emptyList()
)

/**
 * Successfully packed image with position in atlas
 */
data class PackedImage(
    val id: Uri,
    val rect: Rect,
    val originalSize: IntSize
)
