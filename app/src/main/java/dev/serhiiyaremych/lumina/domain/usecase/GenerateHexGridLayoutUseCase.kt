package dev.serhiiyaremych.lumina.domain.usecase

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.HexGrid
import dev.serhiiyaremych.lumina.domain.model.HexGridLayout
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.MediaWithPosition
import dev.serhiiyaremych.lumina.domain.model.*
import dev.serhiiyaremych.lumina.domain.usecase.shape.*
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Composed use case that orchestrates hex grid layout generation.
 *
 * This use case demonstrates the composition pattern by injecting and coordinating
 * other use cases to create a complete hex grid layout with positioned media items.
 *
 * Architecture Benefits:
 * - Single Responsibility: Each injected use case has one clear purpose
 * - Testability: Can mock individual use cases for unit testing
 * - Reusability: Individual use cases can be reused elsewhere
 * - Maintainability: Changes to individual steps don't affect the others
 */
class GenerateHexGridLayoutUseCase @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val groupMediaUseCase: GroupMediaUseCase,
    private val generateHexGridUseCase: GenerateHexGridUseCase,
    private val getHexGridParametersUseCase: GetHexGridParametersUseCase
) {

    /**
     * Generates a complete hex grid layout by orchestrating other use cases.
     *
     * Flow:
     * 1. Get hex grid parameters (size, cell size)
     * 2. Fetch all media items
     * 3. Group media by date/period
     * 4. Generate hex grid geometry
     * 5. Position media items within hex cells
     *
     * @param density UI density for pixel calculations
     * @param canvasSize Canvas dimensions for grid centering
     * @param groupingPeriod How to group media (daily, weekly, monthly)
     * @param thumbnailSizeFactor Size factor for thumbnails within hex cells
     * @return Complete layout ready for rendering
     */
    suspend fun execute(
        density: Density,
        canvasSize: Size,
        groupingPeriod: GroupingPeriod = GroupingPeriod.DAILY,
        thumbnailSizeFactor: Float = 0.45f,
        cellSpacingDp: Dp = 8.dp // Cell spacing between hex cells
    ): HexGridLayout {
        // Step 1: Fetch all media items
        val allMedia = getMediaUseCase()

        // Step 2: Group media by time period
        val groupedMedia = groupMediaUseCase(allMedia, groupingPeriod)

        // Step 3: Get hex grid configuration based on group count
        val (_, cellSizeDp) = getHexGridParametersUseCase(groupedMedia.size)

        // Step 4: Generate hex grid geometry with exact cell count and spacing
        val hexGrid = generateHexGridUseCase(
            cellCount = groupedMedia.size,
            cellSizeDp = cellSizeDp,
            density = density,
            canvasSize = canvasSize,
            cellSpacingDp = cellSpacingDp
        )

        // Step 5: Position media items within hex cells
        return generateLayoutFromGroupedData(
            hexGrid = hexGrid,
            groupedMedia = groupedMedia,
            thumbnailSizeFactor = thumbnailSizeFactor
        )
    }

    /**
     * Alternative execute method for when you already have grouped media.
     * Useful for custom grouping or when media is already processed.
     */
    suspend fun execute(
        hexGrid: HexGrid,
        groupedMedia: Map<LocalDate, List<Media>>,
        thumbnailSizeFactor: Float = 0.4f
    ): HexGridLayout {
        return generateLayoutFromGroupedData(hexGrid, groupedMedia, thumbnailSizeFactor)
    }

    /**
     * Core layout generation from pre-grouped data.
     * This encapsulates all positioning logic previously in the UI layer.
     */
    private fun generateLayoutFromGroupedData(
        hexGrid: HexGrid,
        groupedMedia: Map<LocalDate, List<Media>>,
        thumbnailSizeFactor: Float
    ): HexGridLayout {
        if (groupedMedia.isEmpty()) {
            return HexGridLayout(
                hexGrid = hexGrid,
                hexCellsWithMedia = emptyList()
            )
        }

        val sortedGroups = groupedMedia.keys.sorted()
        val hexCellsWithMedia = mutableListOf<HexCellWithMedia>()

        // Process each date group and assign to hex cells
        sortedGroups.forEachIndexed { index, date ->
            val mediaList = groupedMedia[date] ?: return@forEachIndexed

            if (index < hexGrid.cells.size) {
                val hexCell = hexGrid.cells[index]
                val hexCellWithMedia = generateHexCellWithMedia(
                    hexCell = hexCell,
                    mediaList = mediaList,
                    thumbnailSizeFactor = thumbnailSizeFactor
                )
                hexCellsWithMedia.add(hexCellWithMedia)
            }
        }

        return HexGridLayout(
            hexGrid = hexGrid,
            hexCellsWithMedia = hexCellsWithMedia
        )
    }

    /**
     * Generates a hex cell with positioned media items using organic shape patterns.
     * This encapsulates all the positioning logic previously in the UI layer.
     */
    private fun generateHexCellWithMedia(
        hexCell: dev.serhiiyaremych.lumina.domain.model.HexCell,
        mediaList: List<Media>,
        thumbnailSizeFactor: Float
    ): HexCellWithMedia {
        val hexBounds = calculateHexBounds(hexCell)
        val minDimension = min(hexBounds.width, hexBounds.height)
        val thumbnailMaxSize = minDimension * thumbnailSizeFactor

        val mediaWithPositions = if (mediaList.size <= 1) {
            // Single media item - use existing positioning logic
            mediaList.map { media ->
                generateMediaWithPosition(
                    media = media,
                    hexCell = hexCell,
                    hexBounds = hexBounds,
                    thumbnailMaxSize = thumbnailMaxSize
                )
            }
        } else {
            // Multiple media items - use shape patterns with breathing room
            generateMediaWithShapePattern(
                mediaList = mediaList,
                hexCell = hexCell,
                hexBounds = hexBounds,
                thumbnailMaxSize = thumbnailMaxSize
            )
        }

        return HexCellWithMedia(
            hexCell = hexCell,
            mediaItems = mediaWithPositions
        )
    }

    /**
     * Generates a positioned media item within a hex cell.
     * This is the core positioning logic moved from generateScaledPositionWithOffset.
     */
    private fun generateMediaWithPosition(
        media: Media,
        hexCell: dev.serhiiyaremych.lumina.domain.model.HexCell,
        hexBounds: Rect,
        thumbnailMaxSize: Float
    ): MediaWithPosition {
        // Calculate aspect ratio and size
        val aspectRatio = if (media.height != 0) {
            media.width.toFloat() / media.height.toFloat()
        } else 1f

        val (width, height) = if (aspectRatio >= 1f) {
            thumbnailMaxSize to thumbnailMaxSize / aspectRatio
        } else {
            thumbnailMaxSize * aspectRatio to thumbnailMaxSize
        }

        val size = Size(width, height)

        // Generate consistent random position using seed
        val seed = (media.id + hexCell.q * 1000000 + hexCell.r * 1000).toInt()
        val random = Random(seed)

        // Generate realistic rotation angle based on aspect ratio
        val rotationAngle = calculateRealisticRotation(random, aspectRatio)

        val availableWidth = hexBounds.width - width
        val availableHeight = hexBounds.height - height

        val initialRelativePosition = if (availableWidth > 0 && availableHeight > 0) {
            Offset(
                x = random.nextFloat() * availableWidth,
                y = random.nextFloat() * availableHeight
            )
        } else {
            // Center if no room for random positioning
            Offset(
                x = (hexBounds.width - width) / 2,
                y = (hexBounds.height - height) / 2
            )
        }

        // Calculate hex cell center and radius for circular bounds checking
        val hexCenter = Offset(
            x = hexBounds.left + hexBounds.width / 2,
            y = hexBounds.top + hexBounds.height / 2
        )
        val hexRadius = min(hexBounds.width, hexBounds.height) / 2

        // Clamp position to ensure media rectangle stays within circular bounds
        val relativePosition = clampPositionToCircle(
            position = initialRelativePosition,
            hexBounds = hexBounds,
            hexCenter = hexCenter,
            hexRadius = hexRadius,
            mediaSize = size
        )

        // Calculate absolute position in world coordinates
        val absolutePosition = Offset(
            x = hexBounds.left + relativePosition.x,
            y = hexBounds.top + relativePosition.y
        )

        val absoluteBounds = Rect(
            offset = absolutePosition,
            size = size
        )

        return MediaWithPosition(
            media = media,
            relativePosition = relativePosition,
            size = size,
            absoluteBounds = absoluteBounds,
            seed = seed,
            rotationAngle = rotationAngle
        )
    }

    /**
     * Generates positioned media items using organic shape patterns with breathing room constraints.
     * This method creates visually appealing arrangements while ensuring all photos remain partially visible.
     */
    private fun generateMediaWithShapePattern(
        mediaList: List<Media>,
        hexCell: dev.serhiiyaremych.lumina.domain.model.HexCell,
        hexBounds: Rect,
        thumbnailMaxSize: Float
    ): List<MediaWithPosition> {
        if (mediaList.isEmpty()) return emptyList()

        // Generate consistent seed for this cell
        val cellSeed = (hexCell.q * 1000000 + hexCell.r * 1000).toInt()
        val random = Random(cellSeed)

        // Select shape pattern based on media count
        val shapePattern = selectShapePattern(
            mediaCount = mediaList.size,
            random = random
        )

        // Generate shape pattern configuration
        val shapeConfig = ShapeGenerationConfig(
            pattern = shapePattern,
            intensity = 0.7f,
            breathingRoomFactor = 0.5f
        )

        // Generate base positions using shape pattern
        val patternGenerator = ShapePatternGeneratorFactory.createGenerator(shapePattern)
        val patternResult = patternGenerator.generatePositions(
            mediaCount = mediaList.size,
            hexBounds = hexBounds,
            random = random,
            config = shapeConfig
        )

        // Calculate media sizes and create positioned media items
        val mediaWithPositions = mutableListOf<MediaWithPosition>()

        for (i in mediaList.indices) {
            val media = mediaList[i]
            val basePosition = patternResult.basePositions[i]

            // Calculate size maintaining aspect ratio
            val aspectRatio = if (media.height != 0) {
                media.width.toFloat() / media.height.toFloat()
            } else 1f

            val (width, height) = if (aspectRatio >= 1f) {
                thumbnailMaxSize to thumbnailMaxSize / aspectRatio
            } else {
                thumbnailMaxSize * aspectRatio to thumbnailMaxSize
            }

            val size = Size(width, height)

            // Generate consistent seed for this specific media item
            val mediaSeed = (media.id + hexCell.q * 1000000 + hexCell.r * 1000 + i).toInt()
            val mediaRandom = Random(mediaSeed)

            // Generate realistic rotation angle
            val rotationAngle = calculateRealisticRotation(mediaRandom, aspectRatio)

            // Apply breathing room constraints
            val adjustedPosition = applyBreathingRoomConstraints(
                basePosition = basePosition,
                currentMediaIndex = i,
                mediaSize = size,
                allMediaSizes = mediaList.mapIndexed { index, media ->
                    val aspectRatio = if (media.height != 0) {
                        media.width.toFloat() / media.height.toFloat()
                    } else 1f

                    val (w, h) = if (aspectRatio >= 1f) {
                        thumbnailMaxSize to thumbnailMaxSize / aspectRatio
                    } else {
                        thumbnailMaxSize * aspectRatio to thumbnailMaxSize
                    }

                    Size(w, h)
                },
                allBasePositions = patternResult.basePositions,
                hexBounds = hexBounds,
                config = shapeConfig
            )

            // Calculate hex cell center and radius for circular bounds checking
            val hexCenter = Offset(
                x = hexBounds.left + hexBounds.width / 2,
                y = hexBounds.top + hexBounds.height / 2
            )
            val hexRadius = min(hexBounds.width, hexBounds.height) / 2

            // Clamp to circular bounds (existing logic)
            val finalRelativePosition = clampPositionToCircle(
                position = adjustedPosition,
                hexBounds = hexBounds,
                hexCenter = hexCenter,
                hexRadius = hexRadius,
                mediaSize = size
            )

            // Calculate absolute position
            val absolutePosition = Offset(
                x = hexBounds.left + finalRelativePosition.x,
                y = hexBounds.top + finalRelativePosition.y
            )

            val absoluteBounds = Rect(
                offset = absolutePosition,
                size = size
            )

            mediaWithPositions.add(
                MediaWithPosition(
                    media = media,
                    relativePosition = finalRelativePosition,
                    size = size,
                    absoluteBounds = absoluteBounds,
                    seed = mediaSeed,
                    rotationAngle = rotationAngle
                )
            )
        }

        return mediaWithPositions
    }

    /**
     * Applies breathing room constraints to ensure media items don't completely obscure each other.
     * Adjusts positions minimally to maintain at least 25-30% visibility for each photo.
     */
    private fun applyBreathingRoomConstraints(
        basePosition: Offset,
        currentMediaIndex: Int,
        mediaSize: Size,
        allMediaSizes: List<Size>,
        allBasePositions: List<Offset>,
        hexBounds: Rect,
        config: ShapeGenerationConfig
    ): Offset {
        var adjustedPosition = basePosition
        var currentRect = Rect(offset = adjustedPosition, size = mediaSize)

        // Check overlap with previously positioned media items
        for (attempt in 0 until config.maxAdjustmentAttempts) {
            var hasSignificantOverlap = false

            // Check against all other media items
            for (i in allBasePositions.indices) {
                if (i == currentMediaIndex) continue

                val otherPosition = allBasePositions[i]
                val otherSize = allMediaSizes[i]
                val otherRect = Rect(offset = otherPosition, size = otherSize)

                val overlap = currentRect.intersect(otherRect)
                if (!overlap.isEmpty) {
                    // Calculate overlap percentage relative to current media
                    val currentArea = currentRect.width * currentRect.height
                    val overlapArea = overlap.width * overlap.height
                    val overlapPercentage = overlapArea / currentArea

                    // If overlap exceeds breathing room threshold, adjust position
                    if (overlapPercentage > (1 - config.breathingRoomFactor)) {
                        hasSignificantOverlap = true

                        // Calculate adjustment vector to reduce overlap
                        val adjustmentVector = calculateAdjustmentVector(
                            currentRect = currentRect,
                            otherRect = otherRect
                        )

                        adjustedPosition = Offset(
                            x = (adjustedPosition.x + adjustmentVector.x).coerceIn(0f, hexBounds.width - mediaSize.width),
                            y = (adjustedPosition.y + adjustmentVector.y).coerceIn(0f, hexBounds.height - mediaSize.height)
                        )

                        // Update current rect for next iteration
                        currentRect = Rect(offset = adjustedPosition, size = mediaSize)
                        break
                    }
                }
            }

            // If no significant overlap found, we're done
            if (!hasSignificantOverlap) {
                break
            }
        }

        return adjustedPosition
    }

    /**
     * Calculates adjustment vector to reduce overlap between two rectangles.
     * Tries to maintain the original shape pattern as much as possible.
     */
    private fun calculateAdjustmentVector(
        currentRect: Rect,
        otherRect: Rect
    ): Offset {
        val overlap = currentRect.intersect(otherRect)

        // Calculate the direction to move away from the overlap
        val currentCenterX = currentRect.left + currentRect.width / 2
        val currentCenterY = currentRect.top + currentRect.height / 2
        val otherCenterX = otherRect.left + otherRect.width / 2
        val otherCenterY = otherRect.top + otherRect.height / 2

        val directionX = if (currentCenterX < otherCenterX) -1f else 1f
        val directionY = if (currentCenterY < otherCenterY) -1f else 1f

        // Calculate minimum adjustment needed
        val adjustmentX = if (overlap.width > 0) {
            directionX * (overlap.width / 2 + 5f) // 5px padding
        } else 0f

        val adjustmentY = if (overlap.height > 0) {
            directionY * (overlap.height / 2 + 5f) // 5px padding
        } else 0f

        return Offset(adjustmentX, adjustmentY)
    }

    /**
     * Clamps media position to ensure it stays within circular bounds of hex cell.
     * If the media rectangle extends outside the circle, moves it by the exact difference.
     */
    private fun clampPositionToCircle(
        position: Offset,
        hexBounds: Rect,
        hexCenter: Offset,
        hexRadius: Float,
        mediaSize: Size
    ): Offset {
        val mediaRect = Rect(
            offset = Offset(
                x = hexBounds.left + position.x,
                y = hexBounds.top + position.y
            ),
            size = mediaSize
        )

        // Check all corners and find the one that exceeds the circle the most
        val corners = listOf(
            Offset(mediaRect.left, mediaRect.top),
            Offset(mediaRect.right, mediaRect.top),
            Offset(mediaRect.left, mediaRect.bottom),
            Offset(mediaRect.right, mediaRect.bottom)
        )

        var maxExcess = 0f
        var excessDirection = Offset.Zero

        corners.forEach { corner ->
            val distance = sqrt(
                (corner.x - hexCenter.x) * (corner.x - hexCenter.x) +
                (corner.y - hexCenter.y) * (corner.y - hexCenter.y)
            )

            if (distance > hexRadius) {
                val excess = distance - hexRadius
                if (excess > maxExcess) {
                    maxExcess = excess
                    excessDirection = Offset(
                        x = (hexCenter.x - corner.x) / distance,
                        y = (hexCenter.y - corner.y) / distance
                    )
                }
            }
        }

        // If no corner exceeds the circle, return original position
        if (maxExcess == 0f) {
            return position
        }

        // Move the media rectangle by the exact excess amount toward the center
        val adjustedPosition = Offset(
            x = position.x + excessDirection.x * maxExcess,
            y = position.y + excessDirection.y * maxExcess
        )

        return adjustedPosition
    }

    /**
     * Calculates realistic rotation angle based on aspect ratio with added randomness.
     * Portrait photos tend to fall more upright, landscape photos can rotate more.
     *
     * @param random Random generator with consistent seed
     * @param aspectRatio Aspect ratio of the media (width/height)
     * @return Rotation angle in degrees (±15° to ±30° based on aspect ratio)
     */
    private fun calculateRealisticRotation(random: Random, aspectRatio: Float): Float {
        val baseRotationRange = 20f // Base ±20° rotation

        val rotationVariation = when {
            aspectRatio < 0.8f -> 0.75f + random.nextFloat() * 0.5f  // Portrait: ±15° to ±25°
            aspectRatio > 1.3f -> 1.0f + random.nextFloat() * 0.5f   // Landscape: ±20° to ±30°
            else -> 0.8f + random.nextFloat() * 0.6f                 // Square: ±16° to ±28°
        }

        val maxRotation = baseRotationRange * rotationVariation
        return (random.nextFloat() * 2 - 1) * maxRotation
    }

    /**
     * Calculate hex cell bounding rectangle.
     * Moved from UI layer to domain layer.
     */
    private fun calculateHexBounds(hexCell: dev.serhiiyaremych.lumina.domain.model.HexCell): Rect {
        val vertices = hexCell.vertices
        val minX = vertices.minOf { it.x }
        val maxX = vertices.maxOf { it.x }
        val minY = vertices.minOf { it.y }
        val maxY = vertices.maxOf { it.y }

        return Rect(
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY
        )
    }
}

/**
 * Configuration for hex grid layout generation.
 * Encapsulates layout parameters for easy testing and customization.
 */
data class HexLayoutConfig(
    val thumbnailSizeFactor: Float = 0.4f,
    val groupingPeriod: GroupingPeriod = GroupingPeriod.DAILY,
    val enableRandomPositioning: Boolean = true,
    val maxMediaPerCell: Int = Int.MAX_VALUE
)
