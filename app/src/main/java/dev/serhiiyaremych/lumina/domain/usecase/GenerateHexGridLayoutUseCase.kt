package dev.serhiiyaremych.lumina.domain.usecase

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.HexGrid
import dev.serhiiyaremych.lumina.domain.model.HexGridLayout
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.model.MediaWithPosition
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.min
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
        thumbnailSizeFactor: Float = 0.4f
    ): HexGridLayout {
        // Step 1: Fetch all media items
        val allMedia = getMediaUseCase()

        // Step 2: Group media by time period
        val groupedMedia = groupMediaUseCase(allMedia, groupingPeriod)

        // Step 3: Get hex grid configuration based on group count
        val (gridSize, cellSizeDp) = getHexGridParametersUseCase(groupedMedia.size)

        // Step 4: Generate hex grid geometry
        val hexGrid = generateHexGridUseCase(
            gridSize = gridSize,
            cellSizeDp = cellSizeDp,
            density = density,
            canvasSize = canvasSize
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
     * Generates a hex cell with positioned media items.
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

        val mediaWithPositions = mediaList.map { media ->
            generateMediaWithPosition(
                media = media,
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

        val size = androidx.compose.ui.geometry.Size(width, height)

        // Generate consistent random position using seed
        val seed = (media.id + hexCell.q * 1000000 + hexCell.r * 1000).toInt()
        val random = Random(seed)

        val availableWidth = hexBounds.width - width
        val availableHeight = hexBounds.height - height

        val relativePosition = if (availableWidth > 0 && availableHeight > 0) {
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
            seed = seed
        )
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
