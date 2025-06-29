package dev.serhiiyaremych.lumina.domain.usecase

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexGrid
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GenerateHexGridUseCase @Inject constructor() {
    /**
     * Generate hex grid with exact cell count using ring expansion pattern.
     * More efficient than square grid - generates only needed cells in natural hex pattern.
     */
    operator fun invoke(
        cellCount: Int,
        cellSizeDp: Dp,
        density: Density,
        canvasSize: Size
    ): HexGrid {
        val cellSizePx = with(density) { cellSizeDp.toPx() }
        val radius = cellSizePx / 2f

        // Hex dimensions
        val hexWidth = radius * 2f
        val hexHeight = radius * sqrt(3f)
        val horizontalSpacing = hexWidth * 3f / 4f
        val verticalSpacing = hexHeight

        // Calculate approximate grid bounds for centering
        val estimatedRings = kotlin.math.ceil(kotlin.math.sqrt(cellCount.toDouble() / 3.5)).toInt()
        val gridWidth = estimatedRings * 2 * horizontalSpacing + hexWidth
        val gridHeight = estimatedRings * 2 * verticalSpacing + hexHeight
        val startX = (canvasSize.width - gridWidth) / 2f
        val startY = (canvasSize.height - gridHeight) / 2f

        val cells = generateHexCellsInRings(
            cellCount = cellCount,
            startX = startX,
            startY = startY,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            hexWidth = hexWidth,
            hexHeight = hexHeight,
            radius = radius
        )

        return HexGrid(
            size = kotlin.math.ceil(kotlin.math.sqrt(cellCount.toDouble())).toInt(),
            cellSizeDp = cellSizeDp,
            cells = cells,
            cellSizePx = cellSizePx
        )
    }

    /**
     * Generate hex cells in expanding rings from center (0,0).
     * This creates a more natural hex pattern than square grid.
     */
    private fun generateHexCellsInRings(
        cellCount: Int,
        startX: Float,
        startY: Float,
        horizontalSpacing: Float,
        verticalSpacing: Float,
        hexWidth: Float,
        hexHeight: Float,
        radius: Float
    ): List<HexCell> {
        val cells = mutableListOf<HexCell>()
        
        if (cellCount <= 0) return cells

        // Start with center cell (0,0)
        val centerCell = createHexCell(
            q = 0, r = 0,
            startX = startX, startY = startY,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            hexWidth = hexWidth,
            hexHeight = hexHeight,
            radius = radius
        )
        cells.add(centerCell)

        if (cellCount == 1) return cells

        // Generate rings expanding outward
        var currentRing = 1
        while (cells.size < cellCount) {
            val ringCells = generateHexRing(
                ring = currentRing,
                startX = startX,
                startY = startY,
                horizontalSpacing = horizontalSpacing,
                verticalSpacing = verticalSpacing,
                hexWidth = hexWidth,
                hexHeight = hexHeight,
                radius = radius
            )
            
            // Add cells from this ring until we reach the target count
            for (cell in ringCells) {
                if (cells.size < cellCount) {
                    cells.add(cell)
                } else {
                    break
                }
            }
            
            currentRing++
        }

        return cells
    }

    /**
     * Generate all cells in a specific ring around center (0,0).
     * Ring 0 = center cell, Ring 1 = 6 cells around center, Ring 2 = 12 cells, etc.
     */
    private fun generateHexRing(
        ring: Int,
        startX: Float,
        startY: Float,
        horizontalSpacing: Float,
        verticalSpacing: Float,
        hexWidth: Float,
        hexHeight: Float,
        radius: Float
    ): List<HexCell> {
        if (ring == 0) return emptyList() // Center handled separately
        
        val cells = mutableListOf<HexCell>()
        
        // Hex ring coordinates using axial coordinates
        // Start at top-right and move counter-clockwise
        var q = ring
        var r = -ring
        
        // Six directions for hexagon neighbors
        val directions = listOf(
            Pair(0, 1),   // SE
            Pair(-1, 1),  // S
            Pair(-1, 0),  // SW
            Pair(0, -1),  // NW
            Pair(1, -1),  // N
            Pair(1, 0)    // NE
        )
        
        // Generate ring by walking around the perimeter
        for (direction in directions) {
            repeat(ring) {
                cells.add(createHexCell(
                    q = q, r = r,
                    startX = startX, startY = startY,
                    horizontalSpacing = horizontalSpacing,
                    verticalSpacing = verticalSpacing,
                    hexWidth = hexWidth,
                    hexHeight = hexHeight,
                    radius = radius
                ))
                
                // Move to next position in current direction
                q += direction.first
                r += direction.second
            }
        }
        
        return cells
    }

    /**
     * Create a single hex cell at the given axial coordinates (q,r).
     */
    private fun createHexCell(
        q: Int,
        r: Int,
        startX: Float,
        startY: Float,
        horizontalSpacing: Float,
        verticalSpacing: Float,
        hexWidth: Float,
        hexHeight: Float,
        radius: Float
    ): HexCell {
        // Convert axial coordinates (q,r) to pixel position
        val centerX = startX + q * horizontalSpacing + hexWidth / 2f
        val centerY = startY + (r + q * 0.5f) * verticalSpacing + hexHeight / 2f

        val center = Offset(centerX, centerY)
        val vertices = calculateHexVertices(center, radius)

        return HexCell(
            q = q,
            r = r,
            center = center,
            vertices = vertices
        )
    }

    private fun calculateHexVertices(center: Offset, radius: Float): List<Offset> {
        val vertices = mutableListOf<Offset>()

        // Calculate 6 vertices of hexagon (pointy-top orientation)
        for (i in 0 until 6) {
            val angle = PI / 3.0 * i // 60 degrees per vertex
            val x = center.x + radius * cos(angle).toFloat()
            val y = center.y + radius * sin(angle).toFloat()
            vertices.add(Offset(x, y))
        }

        return vertices
    }
}
