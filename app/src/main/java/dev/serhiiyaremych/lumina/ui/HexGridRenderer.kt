package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.HexGrid

class HexGridRenderer {
    fun drawHexGrid(
        drawScope: DrawScope,
        hexGrid: HexGrid,
        zoom: Float,
        offset: Offset,
        showVertices: Boolean = true,
        showCenters: Boolean = true,
        showConnections: Boolean = true,
        gridColor: Color = Color.Red,
        centerColor: Color = Color.Blue,
        vertexColor: Color = Color.Green
    ) {
        with(drawScope) {
            // Apply zoom and offset transformations
            val transformedCells = hexGrid.cells.map { cell ->
                val transformedCenter = Offset(
                    cell.center.x * zoom + offset.x,
                    cell.center.y * zoom + offset.y
                )
                val transformedVertices = cell.vertices.map { vertex ->
                    Offset(
                        vertex.x * zoom + offset.x,
                        vertex.y * zoom + offset.y
                    )
                }
                cell.copy(center = transformedCenter, vertices = transformedVertices)
            }

            // Draw hex cell outlines
            if (showConnections) {
                transformedCells.forEach { cell ->
                    val path = Path().apply {
                        moveTo(cell.vertices[0].x, cell.vertices[0].y)
                        for (i in 1 until cell.vertices.size) {
                            lineTo(cell.vertices[i].x, cell.vertices[i].y)
                        }
                        close()
                    }
                    drawPath(
                        path = path,
                        color = gridColor,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Draw center points
            if (showCenters) {
                transformedCells.forEach { cell ->
                    drawCircle(
                        color = centerColor,
                        radius = 4.dp.toPx(),
                        center = cell.center
                    )
                }
            }

            // Draw vertices
            if (showVertices) {
                transformedCells.forEach { cell ->
                    cell.vertices.forEach { vertex ->
                        drawCircle(
                            color = vertexColor,
                            radius = 2.dp.toPx(),
                            center = vertex
                        )
                    }
                }
            }
        }
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
