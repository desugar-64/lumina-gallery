package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.HexGridLayout
import dev.serhiiyaremych.lumina.domain.usecase.GroupingPeriod
import dev.serhiiyaremych.lumina.domain.usecase.HexCellDateCalculator

/**
 * Configuration data class for date label rendering
 */
data class DateLabelRenderConfig(
    val textMeasurer: TextMeasurer,
    val hexCellDateCalculator: HexCellDateCalculator,
    val groupingPeriod: GroupingPeriod,
    val zoom: Float,
    val clampedZoom: Float,
    val offset: Offset,
    val canvasSize: Size,
    val dateLabelTextColor: Color,
    val placeholderColor: Color
)

/**
 * Stateless function to render date labels for hex cells.
 * Extracted from MediaHexVisualization for better modularity and testability.
 */
fun DrawScope.renderDateLabels(
    hexGridLayout: HexGridLayout,
    config: DateLabelRenderConfig,
    showDateLabels: Boolean = true
) {
    if (!showDateLabels || !config.hexCellDateCalculator.shouldShowDateAtZoom(config.zoom)) {
        return
    }

    // Calculate viewport in world coordinates
    val viewport = Rect(
        left = -config.offset.x / config.clampedZoom,
        top = -config.offset.y / config.clampedZoom,
        right = (-config.offset.x + config.canvasSize.width) / config.clampedZoom,
        bottom = (-config.offset.y + config.canvasSize.height) / config.clampedZoom
    )
    val visibleCells = hexGridLayout.getVisibleHexCells(viewport)

    for (hexCellWithMedia in visibleCells) {
        renderSingleDateLabel(
            hexCellWithMedia = hexCellWithMedia,
            config = config
        )
    }
}

/**
 * Renders a single date label for a hex cell.
 */
private fun DrawScope.renderSingleDateLabel(
    hexCellWithMedia: HexCellWithMedia,
    config: DateLabelRenderConfig
) {
    val dateText = config.hexCellDateCalculator.formatDateForZoomLevel(
        hexCellWithMedia,
        config.groupingPeriod,
        config.zoom
    ) ?: return

    val hexCenter = hexCellWithMedia.bounds.center

    // Zoom-independent font size for compact forms, compensate zoom factor
    val fontSize = when {
        config.zoom >= 1.0f -> (14 + (config.zoom * 4)).coerceIn(14f, 24f).sp
        else -> (14f / config.zoom).coerceAtLeast(14f).sp // Compensate zoom to maintain readable size
    }

    val alpha = when {
        config.zoom >= 1.0f -> 0.8f
        config.zoom >= 0.5f -> 0.6f
        else -> 0.4f
    }

    val textStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        color = config.dateLabelTextColor.copy(alpha = alpha),
        textAlign = TextAlign.Center
    )

    // Position label below the top edge of the hex cell
    val labelPosition = Offset(
        x = hexCenter.x,
        y = hexCellWithMedia.bounds.top + (16.dp.toPx() / config.zoom)
    )

    // Draw text with semi-transparent background for readability
    val textLayoutResult = config.textMeasurer.measure(dateText, textStyle)
    val textSize = Size(
        textLayoutResult.size.width.toFloat(),
        textLayoutResult.size.height.toFloat()
    )

    // Background rectangle for text readability
    drawRect(
        color = config.placeholderColor.copy(alpha = 0.85f),
        topLeft = Offset(
            labelPosition.x - textSize.width / 2 - 4.dp.toPx() / config.zoom,
            labelPosition.y - 2.dp.toPx() / config.zoom
        ),
        size = Size(
            textSize.width + 8.dp.toPx() / config.zoom,
            textSize.height + 4.dp.toPx() / config.zoom
        )
    )

    // Draw the text
    drawText(
        textMeasurer = config.textMeasurer,
        text = dateText,
        style = textStyle,
        topLeft = Offset(
            labelPosition.x - textSize.width / 2,
            labelPosition.y
        )
    )
}
