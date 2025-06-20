package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset

object CoordinateTransformUtils {
    /**
     * Transforms a screen coordinate to content coordinates based on zoom and offset
     */
    fun transformScreenToContent(
        screenPos: Offset,
        zoom: Float,
        offset: Offset
    ): Offset {
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        return if (clampedZoom != 1f || offset != Offset.Zero) {
            Offset(
                (screenPos.x - offset.x) / clampedZoom,
                (screenPos.y - offset.y) / clampedZoom
            )
        } else {
            screenPos // Use raw coordinates when no transform applied
        }
    }

    /**
     * Transforms content coordinates to screen coordinates based on zoom and offset
     */
    fun transformContentToScreen(
        contentPos: Offset,
        zoom: Float,
        offset: Offset
    ): Offset {
        val clampedZoom = zoom.coerceIn(0.01f, 100f)
        return Offset(
            contentPos.x * clampedZoom + offset.x,
            contentPos.y * clampedZoom + offset.y
        )
    }
}
