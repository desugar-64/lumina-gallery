package dev.serhiiyaremych.lumina.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

/**
 * Represents a single cell in a hexagonal grid using offset coordinates.
 *
 * @property q Column index in the grid (axial coordinate)
 * @property r Row index in the grid (axial coordinate)
 * @property center Pixel position of the cell's center
 * @property vertices List of 6 vertices defining the hexagon shape (pointy-top orientation)
 */
data class HexCell(
    val q: Int, // Column coordinate
    val r: Int, // Row coordinate
    val center: Offset,
    val vertices: List<Offset>
) {
    init {
        require(vertices.size == 6) { "HexCell must have exactly 6 vertices" }
    }
}

/**
 * Complete hexagonal grid structure containing all cells.
 *
 * @property size Approximate diameter of the hexagonal grid (for layout calculations)
 * @property cellSizeDp Design-time cell size in density-independent pixels
 * @property cells List of all hex cells in the grid
 * @property cellSizePx Computed cell size in pixels at current density
 */
data class HexGrid(
    val size: Int, // Approximate grid diameter
    val cellSizeDp: Dp,
    val cells: List<HexCell>,
    val cellSizePx: Float
) {
    init {
        require(size > 0) { "Grid size must be positive" }
        require(cells.isNotEmpty()) { "Grid must contain at least one cell" }
        // Note: For hexagonal grids, cell count doesn't follow size*size pattern
        // Validation removed to support variable hex ring patterns
    }

    /**
     * Hexagonal grid coordinate system notes:
     * - Uses axial coordinate system (q,r) for grid positioning
     * - Pointy-top orientation (vertices at top/bottom)
     * - Odd-q vertical layout (alternating column offset)
     * - Vertex ordering: Starts at top-right, proceeds clockwise
     */
}

/**
 * Generates a debug string representation of the grid
 */
fun HexGrid.debugString(): String = buildString {
    appendLine("HexGrid(size=$size)")
    appendLine("Cell size: ${cellSizeDp.value}dp (${cellSizePx}px)")
    cells.take(3).forEach { cell ->
        appendLine("Cell(${cell.q},${cell.r}) @ ${cell.center}")
    }
    if (cells.size > 3) appendLine("...and ${cells.size - 3} more")
}
