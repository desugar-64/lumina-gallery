package dev.serhiiyaremych.lumina.ui

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Grid system constants
const val BASE_GRID_SPACING_DP = 56f // Base unit for grid spacing
const val MIN_VISUAL_SPACING_DP = 5f // Minimum spacing before grid disappears
const val OPTIMAL_SPACING_DP = 25f // Target spacing for consistent density
const val MAX_VISUAL_SPACING_DP = 80f // Maximum spacing before adding subdivisions

// Grid visual constants
const val MAJOR_GRID_RADIUS_DP = 2f
const val MINOR_GRID_RADIUS_DP = 1f

// Shimmer cascade animation constants
private const val MILLISECONDS_TO_SECONDS = 1000f

// Wave configuration constants
private const val CASCADE_1_SPEED = 1.0f
private const val CASCADE_1_FREQUENCY = 0.006f
private const val CASCADE_1_ANGLE = 45f
private const val CASCADE_1_WEIGHT = 0.4f

private const val CASCADE_2_SPEED = 1.4f
private const val CASCADE_2_FREQUENCY = 0.004f
private const val CASCADE_2_ANGLE = -30f
private const val CASCADE_2_PHASE_OFFSET = 2.0f
private const val CASCADE_2_WEIGHT = 0.35f

private const val CASCADE_3_SPEED = 0.8f
private const val CASCADE_3_FREQUENCY = 0.008f
private const val CASCADE_3_ANGLE = 75f
private const val CASCADE_3_PHASE_OFFSET = 4.0f
private const val CASCADE_3_WEIGHT = 0.25f

// Tint range constants
private const val TINT_FACTOR_MIN = 0.7f
private const val TINT_FACTOR_RANGE = 0.3f
private const val WAVE_NORMALIZE_OFFSET = 1f

// Default wave parameters
private const val DEFAULT_WAVE_ANGLE = 45f
private const val DEFAULT_PHASE_OFFSET = 0f

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

    private var cachedAngles: Map<Float, Pair<Float, Float>>? = null

    private var reusableColor = Color.Unspecified

    fun drawGrid(
        drawScope: DrawScope,
        zoom: Float,
        offset: Offset,
        density: Density,
        majorGridSpacing: androidx.compose.ui.unit.Dp = 56f.dp,
        majorGridColor: Color,
        minorGridColor: Color,
        isLoading: Boolean = false,
        animationTime: Long = 0L,
        returnTransitionProgress: Float = 1f
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

        with(drawScope) {
            // Draw minor points first (so major points appear on top)
            renderData.minorPoints.forEach { gridPoint ->
                val tintFactor = if (isLoading || returnTransitionProgress > 0f) {
                    val shimmerTint = calculateShimmerCascadeTint(gridPoint.offset, animationTime, width, height)
                    shimmerTint * returnTransitionProgress + 1f * (1f - returnTransitionProgress)
                } else {
                    1f
                }

                reusableColor = Color(
                    red = (minorGridColor.red * tintFactor).coerceIn(0f, 1f),
                    green = (minorGridColor.green * tintFactor).coerceIn(0f, 1f),
                    blue = (minorGridColor.blue * tintFactor).coerceIn(0f, 1f),
                    alpha = minorGridColor.alpha * gridPoint.alpha * zoomAlpha
                )

                drawCircle(
                    color = reusableColor,
                    radius = renderData.minorStrokeWidth / 2f,
                    center = gridPoint.offset
                )
            }

            // Draw major points
            renderData.majorPoints.forEach { gridPoint ->
                val tintFactor = if (isLoading || returnTransitionProgress > 0f) {
                    val shimmerTint = calculateShimmerCascadeTint(gridPoint.offset, animationTime, width, height)
                    shimmerTint * returnTransitionProgress + 1f * (1f - returnTransitionProgress)
                } else {
                    1f
                }

                reusableColor = Color(
                    red = (majorGridColor.red * tintFactor).coerceIn(0f, 1f),
                    green = (majorGridColor.green * tintFactor).coerceIn(0f, 1f),
                    blue = (majorGridColor.blue * tintFactor).coerceIn(0f, 1f),
                    alpha = majorGridColor.alpha * gridPoint.alpha * zoomAlpha
                )

                drawCircle(
                    color = reusableColor,
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
        cachedAngles = null
    }

    /**
     * Get cached trigonometric values for an angle to avoid repeated calculations
     */
    private fun getCachedTrigValues(angle: Float): Pair<Float, Float> {
        if (cachedAngles == null) {
            // Pre-calculate commonly used angles
            cachedAngles = mapOf(
                CASCADE_1_ANGLE to Pair(cos(Math.toRadians(CASCADE_1_ANGLE.toDouble())).toFloat(), sin(Math.toRadians(CASCADE_1_ANGLE.toDouble())).toFloat()),
                CASCADE_2_ANGLE to Pair(cos(Math.toRadians(CASCADE_2_ANGLE.toDouble())).toFloat(), sin(Math.toRadians(CASCADE_2_ANGLE.toDouble())).toFloat()),
                CASCADE_3_ANGLE to Pair(cos(Math.toRadians(CASCADE_3_ANGLE.toDouble())).toFloat(), sin(Math.toRadians(CASCADE_3_ANGLE.toDouble())).toFloat()),
                DEFAULT_WAVE_ANGLE to Pair(cos(Math.toRadians(DEFAULT_WAVE_ANGLE.toDouble())).toFloat(), sin(Math.toRadians(DEFAULT_WAVE_ANGLE.toDouble())).toFloat())
            )
        }

        return cachedAngles?.get(angle) ?: run {
            val angleRad = Math.toRadians(angle.toDouble())
            Pair(cos(angleRad).toFloat(), sin(angleRad).toFloat())
        }
    }

    /**
     * Calculates shimmer cascade tint factor for grid point animation during loading.
     * Creates gentle diagonal waves of color tinting that sweep across the grid like aurora.
     */
    private fun calculateShimmerCascadeTint(
        pointOffset: Offset,
        animationTime: Long,
        canvasWidth: Float,
        canvasHeight: Float
    ): Float {
        val time = animationTime / MILLISECONDS_TO_SECONDS

        // Create multiple cascading waves at different angles and speeds
        val cascade1 = createDiagonalWave(
            pointOffset,
            time,
            canvasWidth,
            canvasHeight,
            waveSpeed = CASCADE_1_SPEED,
            waveFrequency = CASCADE_1_FREQUENCY,
            angle = CASCADE_1_ANGLE
        )
        val cascade2 = createDiagonalWave(
            pointOffset,
            time,
            canvasWidth,
            canvasHeight,
            waveSpeed = CASCADE_2_SPEED,
            waveFrequency = CASCADE_2_FREQUENCY,
            angle = CASCADE_2_ANGLE,
            phaseOffset = CASCADE_2_PHASE_OFFSET
        )
        val cascade3 = createDiagonalWave(
            pointOffset,
            time,
            canvasWidth,
            canvasHeight,
            waveSpeed = CASCADE_3_SPEED,
            waveFrequency = CASCADE_3_FREQUENCY,
            angle = CASCADE_3_ANGLE,
            phaseOffset = CASCADE_3_PHASE_OFFSET
        )

        // Combine cascades with different weights for organic shimmer
        val combinedCascade = (cascade1 * CASCADE_1_WEIGHT) + (cascade2 * CASCADE_2_WEIGHT) + (cascade3 * CASCADE_3_WEIGHT)

        // Tint factor range for subtle darker-lighter color variations
        val tintFactor = TINT_FACTOR_MIN + (combinedCascade + WAVE_NORMALIZE_OFFSET) * TINT_FACTOR_RANGE

        return tintFactor
    }

    private fun createDiagonalWave(
        pointOffset: Offset,
        time: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        waveSpeed: Float,
        waveFrequency: Float,
        angle: Float = DEFAULT_WAVE_ANGLE,
        phaseOffset: Float = DEFAULT_PHASE_OFFSET
    ): Float {
        // Use cached trigonometric values for performance
        val (cosAngle, sinAngle) = getCachedTrigValues(angle)

        // Calculate distance along the wave direction using cached values
        val waveDistance = pointOffset.x * cosAngle + pointOffset.y * sinAngle

        // Normalize distance for consistent wave propagation using cached values
        val maxDistance = canvasWidth * cosAngle + canvasHeight * sinAngle
        val normalizedDistance = if (maxDistance != 0f) waveDistance / maxDistance else 0f

        // Create wave with time-based movement and spatial frequency
        val wavePhase = (normalizedDistance / waveFrequency) - (time * waveSpeed) + phaseOffset

        return sin(wavePhase).toFloat()
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
