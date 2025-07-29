package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexGrid
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class HexCellState {
    NORMAL,
    FOCUSED,
    SELECTED
}

data class HexRenderConfig(
    val baseStrokeWidth: Dp = 1.5.dp,
    val cellPadding: Dp = 0.dp,
    val cornerRadius: Dp = 0.dp,
    val gridColor: Color = Color.Gray.copy(alpha = 0.3f),
    val focusedColor: Color = Color.Blue.copy(alpha = 0.6f),
    val selectedColor: Color = Color.Green.copy(alpha = 0.8f)
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
        cellStates: Map<HexCell, HexCellState> = emptyMap()
    ) {
        with(drawScope) {
            hexGrid.cells.forEach { cell ->
                val cellState = cellStates[cell] ?: HexCellState.NORMAL
                drawHexCell(
                    cell = cell,
                    config = config,
                    state = cellState
                )
            }
        }
    }

    /**
     * Draw individual hex cell with state-based styling and smooth corners
     */
    private fun DrawScope.drawHexCell(
        cell: HexCell,
        config: HexRenderConfig,
        state: HexCellState
    ) {
        val path = createHexPath(cell, config)
        val color = when (state) {
            HexCellState.NORMAL -> config.gridColor
            HexCellState.FOCUSED -> config.focusedColor
            HexCellState.SELECTED -> config.selectedColor
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = config.baseStrokeWidth.toPx())
        )
    }

    /**
     * Create or reuse hex path with padding and smooth corners.
     * Assumes we're in a transform context that handles zoom/offset.
     */
    private fun DrawScope.createHexPath(
        cell: HexCell,
        config: HexRenderConfig
    ): Path {
        // Convert Dp to pixels for cache key and calculations
        val cornerRadiusPx = config.cornerRadius.toPx()
        val cellPaddingPx = config.cellPadding.toPx()
        
        // Create cache key for this configuration
        val cacheKey = "${cell.hashCode()}_${cellPaddingPx}_${cornerRadiusPx}"
        
        // Return cached path if available
        pathCache[cacheKey]?.let { return it }
        
        // Create new path with padding
        reusablePath.reset()
        
        val vertices = if (cellPaddingPx > 0f) {
            createPaddedVertices(cell, cellPaddingPx)
        } else {
            cell.vertices
        }
        
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
     * Create vertices with dynamic padding around the cell
     */
    private fun createPaddedVertices(cell: HexCell, padding: Float): List<Offset> {
        val center = cell.center
        return cell.vertices.map { vertex ->
            val direction = (vertex - center)
            val length = kotlin.math.sqrt(direction.x * direction.x + direction.y * direction.y)
            val normalizedDirection = if (length > 0) direction / length else Offset.Zero
            vertex - (normalizedDirection * padding)
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
