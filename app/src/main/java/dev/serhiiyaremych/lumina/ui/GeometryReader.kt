package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.serhiiyaremych.lumina.domain.model.HexCell
import dev.serhiiyaremych.lumina.domain.model.Media

class GeometryReader {
    private val mediaBounds = mutableMapOf<Media, Rect>()
    public val hexCellBounds = mutableMapOf<HexCell, Rect>()
    var debugMode = false

    fun storeMediaBounds(media: Media, bounds: Rect, hexCell: HexCell) {
        mediaBounds[media] = bounds
        // Don't store media bounds in hexCellBounds!
    }

    fun storeHexCellBounds(hexCell: HexCell) {
        val vertices = hexCell.vertices
        val minX = vertices.minOf { it.x }
        val maxX = vertices.maxOf { it.x }
        val minY = vertices.minOf { it.y }
        val maxY = vertices.maxOf { it.y }
        hexCellBounds[hexCell] = Rect(minX, minY, maxX, maxY)
    }

    fun getMediaAtPosition(position: Offset): Media? {
        return mediaBounds.entries.find { it.value.contains(position) }?.key
    }

    fun getHexCellAtPosition(position: Offset): HexCell? {
        return hexCellBounds.entries.firstOrNull { (hexCell, _) ->
            isPointInHexagon(position, hexCell.vertices)
        }?.key
    }

    /**
     * Gets the bounds Rect for a media item
     */
    fun getMediaBounds(media: Media): Rect? = mediaBounds[media]

    /**
     * Gets the bounds Rect for a hex cell
     */
    fun getHexCellBounds(hexCell: HexCell): Rect? = hexCellBounds[hexCell]

    fun clear() {
        mediaBounds.clear()
        hexCellBounds.clear()
    }

    private fun isPointInHexagon(point: Offset, vertices: List<Offset>): Boolean {
        var inside = false
        for (i in vertices.indices) {
            val j = (i + 1) % vertices.size
            if ((vertices[i].y > point.y) != (vertices[j].y > point.y) &&
                point.x < (vertices[j].x - vertices[i].x) * (point.y - vertices[i].y) /
                (vertices[j].y - vertices[i].y) + vertices[i].x) {
                inside = !inside
            }
        }
        return inside
    }

    // Add debug drawing function
    fun debugDrawHexCellBounds(drawScope: DrawScope) {
        if (!debugMode) return

        hexCellBounds.forEach { (hexCell, bounds) ->
            drawScope.drawRect(
                color = Color.Magenta.copy(alpha = 0.3f),
                topLeft = bounds.topLeft,
                size = bounds.size,
                style = Stroke(width = 2f)
            )

            // Draw vertices
            hexCell.vertices.forEach { vertex ->
                drawScope.drawCircle(
                    color = Color.Red,
                    radius = 5f,
                    center = vertex
                )
            }
        }
    }
}
