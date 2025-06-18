package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.max

// Grid system constants
const val BASE_GRID_SPACING_DP = 56f // Base unit for grid spacing
const val MIN_VISUAL_SPACING_DP = 5f // Minimum spacing before grid disappears
const val OPTIMAL_SPACING_DP = 25f // Target spacing for consistent density
const val MAX_VISUAL_SPACING_DP = 80f // Maximum spacing before adding subdivisions

// Grid visual constants
const val MAJOR_GRID_RADIUS_DP = 2f
const val MINOR_GRID_RADIUS_DP = 1f

data class GridLevel(
    val spacing: Float,
    val isMajor: Boolean
)

data class GridCacheKey(
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float
) {
    fun isCompatibleWith(other: GridCacheKey, tolerance: Float = 1f): Boolean = kotlin.math.abs(zoom - other.zoom) < tolerance * 0.01f &&
        kotlin.math.abs(offsetX - other.offsetX) < tolerance &&
        kotlin.math.abs(offsetY - other.offsetY) < tolerance &&
        width == other.width &&
        height == other.height
}

data class GridPoint(
    val offset: Offset,
    val alpha: Float
)

data class GridRenderData(
    val majorPoints: List<GridPoint>,
    val minorPoints: List<GridPoint>,
    val majorStrokeWidth: Float,
    val minorStrokeWidth: Float
)

class GridRenderer {
    private var cachedKey: GridCacheKey? = null
    private var cachedRenderData: GridRenderData? = null

    fun drawGrid(
        drawScope: DrawScope,
        zoom: Float,
        offset: Offset,
        density: Density,
        majorGridSpacing: androidx.compose.ui.unit.Dp = 56f.dp,
        majorGridColor: Color = Color(0xFF808080),
        minorGridColor: Color = Color(0xFF808080)
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height
        val currentKey = GridCacheKey(zoom, offset.x, offset.y, width, height)

        // Check if we can reuse cached data
        val renderData = if (cachedKey?.isCompatibleWith(currentKey) == true) {
            cachedRenderData!!
        } else {
            // Calculate new grid data and cache it
            val newRenderData = calculateGridPoints(zoom, offset, width, height, density, majorGridSpacing)
            cachedKey = currentKey
            cachedRenderData = newRenderData
            newRenderData
        }

        // Calculate zoom-based alpha reduction
        val zoomAlpha = when {
            zoom < 0.3f -> zoom * 2f // Linear fade starting at 0.3, reaching 60% at min zoom
            else -> 1f // Full opacity above 0.3 zoom
        }

        // Draw with alpha blending for edge fade and zoom-based transparency
        with(drawScope) {
            // Draw minor points first (so major points appear on top)
            renderData.minorPoints.forEach { gridPoint ->
                drawCircle(
                    color = minorGridColor.copy(alpha = minorGridColor.alpha * gridPoint.alpha * zoomAlpha),
                    radius = renderData.minorStrokeWidth / 2f,
                    center = gridPoint.offset
                )
            }

            // Draw major points
            renderData.majorPoints.forEach { gridPoint ->
                drawCircle(
                    color = majorGridColor.copy(alpha = majorGridColor.alpha * gridPoint.alpha * zoomAlpha),
                    radius = renderData.majorStrokeWidth / 2f,
                    center = gridPoint.offset
                )
            }
        }
    }

    private fun calculateGridPoints(
        zoom: Float,
        offset: Offset,
        width: Float,
        height: Float,
        density: Density,
        majorGridSpacing: androidx.compose.ui.unit.Dp
    ): GridRenderData {
        val gridLevels = calculateGridLevels(zoom, density, majorGridSpacing)
        val majorStrokeWidth = with(density) { MAJOR_GRID_RADIUS_DP.dp.toPx() * 2f }
        val minorStrokeWidth = with(density) { MINOR_GRID_RADIUS_DP.dp.toPx() * 2f }

        val majorPoints = mutableListOf<GridPoint>()
        val minorPoints = mutableListOf<GridPoint>()

        // Sort levels by spacing (largest first) to handle overlaps
        val sortedLevels = gridLevels.sortedByDescending { it.spacing }

        for (level in sortedLevels) {
            val spacing = level.spacing
            val radius = if (level.isMajor) majorStrokeWidth / 2f else minorStrokeWidth / 2f

            // Calculate visible range (allow negative indices for endless grid)
            val minI = ((-offset.x - radius) / spacing).toInt()
            val maxI = ((width + radius - offset.x) / spacing).toInt()
            val minJ = ((-offset.y - radius) / spacing).toInt()
            val maxJ = ((height + radius - offset.y) / spacing).toInt()

            // Track overlapping positions with coarser grids
            val overlappingPositions = mutableSetOf<Pair<Int, Int>>()

            for (higherLevel in sortedLevels) {
                if (higherLevel.spacing > spacing) {
                    val ratio = (higherLevel.spacing / spacing).toInt()
                    if (higherLevel.spacing % spacing == 0f) {
                        for (i in minI..maxI) {
                            for (j in minJ..maxJ) {
                                if (i % ratio == 0 && j % ratio == 0) {
                                    overlappingPositions.add(Pair(i, j))
                                }
                            }
                        }
                    }
                }
            }

            // Add non-overlapping points to appropriate list
            val targetList = if (level.isMajor) majorPoints else minorPoints

            for (i in minI..maxI) {
                val x = i * spacing + offset.x
                for (j in minJ..maxJ) {
                    val y = j * spacing + offset.y

                    if (!overlappingPositions.contains(Pair(i, j))) {
                        // For endless grid, no edge fade needed
                        targetList.add(GridPoint(Offset(x, y), 1f))
                    }
                }
            }
        }

        return GridRenderData(
            majorPoints = majorPoints,
            minorPoints = minorPoints,
            majorStrokeWidth = majorStrokeWidth,
            minorStrokeWidth = minorStrokeWidth
        )
    }

    fun invalidateCache() {
        cachedKey = null
        cachedRenderData = null
    }
}

fun calculateGridLevels(zoom: Float, density: Density, majorGridSpacing: androidx.compose.ui.unit.Dp = BASE_GRID_SPACING_DP.dp): List<GridLevel> {
    val levels = mutableListOf<GridLevel>()
    val minSpacingPx = with(density) { MIN_VISUAL_SPACING_DP.dp.toPx() }
    val maxSpacingPx = with(density) { MAX_VISUAL_SPACING_DP.dp.toPx() }

    // Special handling for zoom < 1.0: use normal base spacing scaled by zoom
    if (zoom < 1.0f) {
        // Calculate density reduction factor based on zoom level
        val densityFactor = when {
            zoom < 0.2f -> 4 // Show every 4th point
            zoom < 0.4f -> 2 // Show every 2nd point
            else -> 1 // Show all points
        }

        // Use normal base spacing scaled by zoom and density factor
        val baseSpacingPx = with(density) { majorGridSpacing.toPx() * zoom * densityFactor }

        // Ensure minimum spacing for visibility
        val effectiveSpacing = kotlin.math.max(baseSpacingPx, minSpacingPx * 1.2f)

        levels.add(
            GridLevel(
                spacing = effectiveSpacing,
                isMajor = true
            )
        )
        return levels
    }

    // Normal handling for zoom >= 1.0
    val baseSpacingPx = with(density) { majorGridSpacing.toPx() * zoom }

    // Find the appropriate subdivision level to maintain density
    var currentSpacing = baseSpacingPx
    var subdivisionLevel = 1

    // If base spacing is too large, add subdivisions
    while (currentSpacing > maxSpacingPx && subdivisionLevel < 16) {
        subdivisionLevel *= 2
        currentSpacing = baseSpacingPx / subdivisionLevel
    }

    // If spacing is still too small, reduce subdivision
    while (currentSpacing < minSpacingPx && subdivisionLevel > 1) {
        subdivisionLevel /= 2
        currentSpacing = baseSpacingPx / subdivisionLevel
    }

    // Add grid levels based on subdivision
    if (currentSpacing >= minSpacingPx) {
        // Major grid at current subdivision level
        levels.add(
            GridLevel(
                spacing = currentSpacing,
                isMajor = true
            )
        )

        // Add minor grid if there's room for further subdivision
        val minorSpacing = currentSpacing / 2f
        if (minorSpacing >= minSpacingPx && currentSpacing <= maxSpacingPx) {
            levels.add(
                GridLevel(
                    spacing = minorSpacing,
                    isMajor = false
                )
            )
        }
    }

    return levels
}
