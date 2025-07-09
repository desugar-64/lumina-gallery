package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.Media

enum class DebugMode {
    NONE,
    BOUNDS,
    HIT_TESTING,
    PERFORMANCE
}

class GeometryReader {
    private val mediaBounds = mutableMapOf<Media, Rect>()
    private val hexCellBounds = mutableMapOf<HexCell, Rect>()
    private val visibleCells = mutableSetOf<HexCell>()
    var debugMode: DebugMode = DebugMode.NONE

    fun storeMediaBounds(media: Media, bounds: Rect, hexCell: HexCell) {
        mediaBounds[media] = bounds
    }

    fun storeHexCellBounds(hexCell: HexCell) {
        val vertices = hexCell.vertices
        val minX = vertices.minOf { it.x }
        val maxX = vertices.maxOf { it.x }
        val minY = vertices.minOf { it.y }
        val maxY = vertices.maxOf { it.y }
        hexCellBounds[hexCell] = Rect(minX, minY, maxX, maxY)
    }

    fun getMediaAtPosition(position: Offset): Media? = mediaBounds.entries.findLast { it.value.contains(position) }?.key

    fun getHexCellAtPosition(position: Offset): HexCell? = hexCellBounds.entries.firstOrNull { (hexCell, _) ->
        isPointInHexagon(position, hexCell.vertices)
    }?.key

    fun getMediaBounds(media: Media): Rect? = mediaBounds[media]

    fun getHexCellBounds(hexCell: HexCell): Rect? = hexCellBounds[hexCell]

    fun clear() {
        mediaBounds.clear()
        hexCellBounds.clear()
        visibleCells.clear()
    }

    fun updateVisibleArea(visibleRect: Rect) {
        visibleCells.clear()
        hexCellBounds.forEach { (cell, bounds) ->
            if (bounds.overlaps(visibleRect)) {
                visibleCells.add(cell)
            }
        }
    }

    private fun isPointInHexagon(point: Offset, vertices: List<Offset>): Boolean {
        var inside = false
        for (i in vertices.indices) {
            val j = (i + 1) % vertices.size
            if ((vertices[i].y > point.y) != (vertices[j].y > point.y) &&
                point.x < (vertices[j].x - vertices[i].x) * (point.y - vertices[i].y) /
                (vertices[j].y - vertices[i].y) + vertices[i].x
            ) {
                inside = !inside
            }
        }
        return inside
    }

    fun debugDrawBounds(drawScope: DrawScope, zoom: Float) {
        if (debugMode == DebugMode.NONE) return

        // Draw hex cell bounds
        hexCellBounds.forEach { (hexCell, bounds) ->
            if (debugMode == DebugMode.BOUNDS || visibleCells.contains(hexCell)) {
                drawScope.drawRect(
                    color = Color.Magenta,
                    topLeft = bounds.topLeft,
                    size = bounds.size,
                    style = Stroke(width = with(drawScope) { 2.dp.toPx() / zoom })
                )
            }
        }

        // Draw media bounds
        mediaBounds.forEach { (_, bounds) ->
            if (debugMode == DebugMode.BOUNDS) {
                drawScope.drawRect(
                    color = Color.Cyan,
                    topLeft = bounds.topLeft,
                    size = bounds.size,
                    style = Stroke(width = with(drawScope) { 2.dp.toPx() / zoom })
                )
            }
        }
    }
}
