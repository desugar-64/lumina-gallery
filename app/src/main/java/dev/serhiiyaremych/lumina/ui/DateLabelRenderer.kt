package dev.serhiiyaremych.lumina.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import dev.serhiiyaremych.lumina.domain.model.HexGridLayout
import dev.serhiiyaremych.lumina.domain.usecase.GroupingPeriod
import dev.serhiiyaremych.lumina.domain.usecase.HexCellDateCalculator
import dev.serhiiyaremych.lumina.ui.datelabel.DateLabelRenderConfig
import dev.serhiiyaremych.lumina.ui.datelabel.PathCache
import dev.serhiiyaremych.lumina.ui.datelabel.PathCacheKey
import dev.serhiiyaremych.lumina.ui.datelabel.calculateLabelPosition
import dev.serhiiyaremych.lumina.ui.datelabel.calculateLabelStyle
import dev.serhiiyaremych.lumina.ui.datelabel.drawLabelBackground
import dev.serhiiyaremych.lumina.ui.datelabel.drawLabelText
import dev.serhiiyaremych.lumina.ui.datelabel.textToPath

/**
 * Stateless function to render date labels for hex cells.
 * Extracted from MediaHexVisualization for better modularity and testability.
 */
fun DrawScope.renderDateLabels(
    hexGridLayout: HexGridLayout,
    hexCellDateCalculator: HexCellDateCalculator,
    groupingPeriod: GroupingPeriod,
    config: DateLabelRenderConfig,
    showDateLabels: Boolean = true
) {
if (!showDateLabels || !hexCellDateCalculator.shouldShowDateAtZoom(config.zoom)) {
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
            hexCellDateCalculator = hexCellDateCalculator,
            groupingPeriod = groupingPeriod,
            config = config
        )
    }
}

/**
 * Renders a single date label for a hex cell with morphing animation support.
 */
private fun DrawScope.renderSingleDateLabel(
    hexCellWithMedia: HexCellWithMedia,
    hexCellDateCalculator: HexCellDateCalculator,
    groupingPeriod: GroupingPeriod,
    config: DateLabelRenderConfig
) {
val dateText = hexCellDateCalculator.formatDateForZoomLevel(
        hexCellWithMedia,
        groupingPeriod,
        config.zoom
    ) ?: return

    val labelStyle = calculateLabelStyle(config.clampedZoom)
    val labelPosition = calculateLabelPosition(hexCellWithMedia, config.zoom)
    val cacheKey = PathCacheKey(dateText, labelStyle.fontSize)

    // Get text path (no animation)
    val cachedData = PathCache.getOrCreate(cacheKey) { key ->
        textToPath(key.text, key.fontSize)
    }

    // Draw background and text as-is
    drawLabelBackground(labelPosition, cachedData.bounds.width(), cachedData.bounds.height(), config)
    drawLabelText(labelPosition, cachedData, config, labelStyle.alpha)
}


