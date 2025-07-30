package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexGrid
import kotlin.math.min

enum class HexCellState {
    NORMAL,
    FOCUSED,
    SELECTED
}

data class HexRenderConfig(
    val baseStrokeWidth: Dp = 1.5.dp,
    val cellPadding: Dp = 0.dp, // Cell spacing is now handled at grid generation level
    val cornerRadius: Dp = 0.dp,
    val gridColor: Color = Color.Gray.copy(alpha = 0.3f),
    val focusedColor: Color = Color.Blue.copy(alpha = 0.6f),
    val selectedColor: Color = Color.Green.copy(alpha = 0.8f),
    // Material 3 Expressive selection enhancements
    val mutedColorAlpha: Float = 0.4f, // Reduced alpha for non-selected cells
    val selectedStrokeWidth: Dp = 2.5.dp // Thicker stroke for selected cells
)

class HexGridRenderer {
    private val pathCache = mutableMapOf<String, Path>()
    private val reusablePath = Path()
    /**
     * Main hex grid rendering function with performance optimizations.
     * Should be called within a transform context that handles zoom/offset.
     */
    fun drawHexGrid(
        drawScope: DrawScope,
        hexGrid: HexGrid,
        config: HexRenderConfig = HexRenderConfig(),
        cellStates: Map<HexCell, HexCellState> = emptyMap(),
        cellScales: Map<HexCell, Float> = emptyMap()
    ) {
        with(drawScope) {
            hexGrid.cells.forEach { cell ->
                val cellState = cellStates[cell] ?: HexCellState.NORMAL
                val cellScale = cellScales[cell] ?: 1.0f
                drawHexCell(
                    cell = cell,
                    config = config,
                    state = cellState,
                    scale = cellScale
                )
            }
        }
    }

    /**
     * Draw individual hex cell with state-based styling, smooth corners, and Material 3 bounce animation.
     * Enhanced with Material 3 Expressive selection features including springy bounce scaling.
     */
    private fun DrawScope.drawHexCell(
        cell: HexCell,
        config: HexRenderConfig,
        state: HexCellState,
        scale: Float = 1.0f
    ) {
        val path = createHexPath(cell, config, state)
        val (color, strokeWidth) = when (state) {
            HexCellState.NORMAL -> config.gridColor.copy(alpha = config.gridColor.alpha * config.mutedColorAlpha) to config.baseStrokeWidth.toPx()
            HexCellState.FOCUSED -> config.focusedColor to config.baseStrokeWidth.toPx()
            HexCellState.SELECTED -> config.selectedColor to config.selectedStrokeWidth.toPx()
        }

        // Draw radial gradient behind selected cells
        if (state == HexCellState.SELECTED) {
            // Calculate cell radius from center to first vertex
            val cellRadius = kotlin.math.sqrt(
                (cell.vertices[0].x - cell.center.x).let { it * it } + 
                (cell.vertices[0].y - cell.center.y).let { it * it }
            )
            val gradientRadius = cellRadius * 1.3f // 30% bigger than cell radius
            val radialGradient = Brush.radialGradient(
                colors = listOf(
                    Color.Blue.copy(alpha = 0.4f), // Blue center
                    Color.Blue.copy(alpha = 0.1f), // Fade to lighter blue
                    Color.Transparent // Transparent at edges
                ),
                center = cell.center,
                radius = gradientRadius
            )
            
            drawCircle(
                brush = radialGradient,
                radius = gradientRadius,
                center = cell.center
            )
        }

        // Apply bounce animation scaling if needed
        if (scale != 1.0f) {
            withTransform({
                // Scale around the center of the hex cell
                scale(scale, scale, pivot = cell.center)
            }) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth)
                )
            }
        } else {
            // No scaling - draw normally for better performance
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth)
            )
        }
    }

    /**
     * Create or reuse hex path with smooth corners.
     * Assumes we're in a transform context that handles zoom/offset.
     * No padding applied - cells maintain their original size for proper photo positioning.
     */
    private fun DrawScope.createHexPath(
        cell: HexCell,
        config: HexRenderConfig,
        state: HexCellState
    ): Path {
        // Convert Dp to pixels for cache key and calculations
        val cornerRadiusPx = config.cornerRadius.toPx()

        // Create cache key for this configuration
        val cacheKey = "${cell.hashCode()}_${cornerRadiusPx}_${state.name}_original"

        // Return cached path if available
        pathCache[cacheKey]?.let { return it }

        // Create new path using original cell vertices (no padding modifications)
        reusablePath.reset()

        // Always use original vertices to maintain proper cell size for photos
        val vertices = cell.vertices

        if (cornerRadiusPx > 0f) {
            createRoundedHexPath(reusablePath, vertices, cornerRadiusPx)
        } else {
            createSharpHexPath(reusablePath, vertices)
        }

        // Cache the path
        val cachedPath = Path().apply { addPath(reusablePath) }
        pathCache[cacheKey] = cachedPath

        return cachedPath
    }

    /**
     * Create vertices with dynamic padding around the cell.
     * Positive padding moves vertices inward, creating gaps between cells by shrinking each cell.
     * This is the correct approach - shrinking cells creates natural gaps without overlaps.
     */
    private fun createPaddedVertices(cell: HexCell, padding: Float): List<Offset> {
        val center = cell.center
        return cell.vertices.map { vertex ->
            val direction = (vertex - center)
            val length = kotlin.math.sqrt(direction.x * direction.x + direction.y * direction.y)
            val normalizedDirection = if (length > 0) direction / length else Offset.Zero
            vertex - (normalizedDirection * padding) // Inward shrinking creates gaps between cells
        }
    }

    /**
     * Create sharp-cornered hex path
     */
    private fun createSharpHexPath(path: Path, vertices: List<Offset>) {
        if (vertices.isEmpty()) return

        path.moveTo(vertices[0].x, vertices[0].y)
        for (i in 1 until vertices.size) {
            path.lineTo(vertices[i].x, vertices[i].y)
        }
        path.close()
    }

    /**
     * Create smooth-cornered hex path using rounded corners
     */
    private fun createRoundedHexPath(path: Path, vertices: List<Offset>, cornerRadius: Float) {
        if (vertices.size < 3) return

        val roundedVertices = mutableListOf<Offset>()

        // Calculate rounded corner points
        for (i in vertices.indices) {
            val current = vertices[i]
            val prev = vertices[(i - 1 + vertices.size) % vertices.size]
            val next = vertices[(i + 1) % vertices.size]

            // Calculate directions to previous and next vertices
            val toPrev = (prev - current).let {
                val length = kotlin.math.sqrt(it.x * it.x + it.y * it.y)
                if (length > 0) it / length else Offset.Zero
            }
            val toNext = (next - current).let {
                val length = kotlin.math.sqrt(it.x * it.x + it.y * it.y)
                if (length > 0) it / length else Offset.Zero
            }

            // Calculate corner points
            val effectiveRadius = min(cornerRadius,
                min(
                    kotlin.math.sqrt((prev - current).getDistanceSquared()) / 2f,
                    kotlin.math.sqrt((next - current).getDistanceSquared()) / 2f
                )
            )

            val cornerStart = current + toPrev * effectiveRadius
            val cornerEnd = current + toNext * effectiveRadius

            roundedVertices.add(cornerStart)
            roundedVertices.add(cornerEnd)
        }

        // Draw path with rounded corners
        if (roundedVertices.size >= 4) {
            path.moveTo(roundedVertices[0].x, roundedVertices[0].y)

            for (i in 0 until roundedVertices.size step 2) {
                val cornerStart = roundedVertices[i]
                val cornerEnd = roundedVertices[(i + 1) % roundedVertices.size]
                val vertex = vertices[i / 2]

                path.lineTo(cornerStart.x, cornerStart.y)
                path.quadraticBezierTo(vertex.x, vertex.y, cornerEnd.x, cornerEnd.y)

                val nextCornerStart = roundedVertices[(i + 2) % roundedVertices.size]
                path.lineTo(nextCornerStart.x, nextCornerStart.y)
            }

            path.close()
        }
    }

    /**
     * Clear path cache (call when zoom/offset changes significantly)
     */
    fun clearCache() {
        pathCache.clear()
    }

    fun getGridAtPosition(
        hexGrid: HexGrid,
        position: Offset,
        zoom: Float,
        offset: Offset
    ): HexCell? {
        // Transform position back to grid coordinates
        val gridX = (position.x - offset.x) / zoom
        val gridY = (position.y - offset.y) / zoom
        val gridPosition = Offset(gridX, gridY)

        return hexGrid.cells.find { cell ->
            isPointInHex(gridPosition, cell)
        }
    }

    private fun isPointInHex(point: Offset, hexCell: HexCell): Boolean {
        val vertices = hexCell.vertices
        var isInside = false
        var j = vertices.size - 1

        for (i in vertices.indices) {
            val xi = vertices[i].x
            val yi = vertices[i].y
            val xj = vertices[j].x
            val yj = vertices[j].y

            if (((yi > point.y) != (yj > point.y)) &&
                (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
            ) {
                isInside = !isInside
            }
            j = i
        }
        return isInside
    }
}
