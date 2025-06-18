package dev.serhiiyaremych.lumina.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class HexGridGenerator {
    fun generateGrid(
        gridSize: Int,
        cellSizeDp: Dp,
        density: Density
    ): HexGrid {
        val cellSizePx = with(density) { cellSizeDp.toPx() }
        val radius = cellSizePx / 2f

        // Hex dimensions
        val hexWidth = radius * 2f
        val hexHeight = radius * sqrt(3f)
        val horizontalSpacing = hexWidth * 3f / 4f
        val verticalSpacing = hexHeight

        val cells = mutableListOf<HexCell>()

        for (q in 0 until gridSize) {
            for (r in 0 until gridSize) {
                // Calculate center position in grid coordinates
                val centerX = q * horizontalSpacing + hexWidth / 2f
                val centerY = r * verticalSpacing + hexHeight / 2f +
                    if (q % 2 == 1) hexHeight / 2f else 0f

                val center = Offset(centerX, centerY)
                val vertices = calculateHexVertices(center, radius)

                cells.add(
                    HexCell(
                        q = q,
                        r = r,
                        center = center,
                        vertices = vertices
                    )
                )
            }
        }

        return HexGrid(
            size = gridSize,
            cellSizeDp = cellSizeDp,
            cells = cells,
            cellSizePx = cellSizePx
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
