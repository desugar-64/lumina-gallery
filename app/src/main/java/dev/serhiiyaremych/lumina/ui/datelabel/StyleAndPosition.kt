package dev.serhiiyaremych.lumina.ui.datelabel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia

internal data class LabelStyle(
    val fontSize: Float,
    val alpha: Float
)

internal fun calculateLabelStyle(zoom: Float): LabelStyle {
    // Map zoom to [0,1] between medium and high thresholds; below medium -> MIN, above high -> MAX
    val t = ((zoom - MEDIUM_ZOOM_THRESHOLD) / (HIGH_ZOOM_THRESHOLD - MEDIUM_ZOOM_THRESHOLD)).coerceIn(0f, 1f)
    val fontSize = MIN_FONT_SIZE + (MAX_FONT_SIZE - MIN_FONT_SIZE) * t

    val alpha = when {
        zoom >= HIGH_ZOOM_THRESHOLD -> HIGH_ZOOM_ALPHA
        zoom >= MEDIUM_ZOOM_THRESHOLD -> MEDIUM_ZOOM_ALPHA
        else -> LOW_ZOOM_ALPHA
    }
    return LabelStyle(fontSize, alpha)
}

internal fun DrawScope.calculateLabelPosition(hexCellWithMedia: HexCellWithMedia, zoom: Float): Offset {
    val vOffset = LABEL_VERTICAL_OFFSET.toPx() / zoom
    return Offset(
        x = hexCellWithMedia.bounds.center.x,
        y = hexCellWithMedia.bounds.top + vOffset
    )
}

