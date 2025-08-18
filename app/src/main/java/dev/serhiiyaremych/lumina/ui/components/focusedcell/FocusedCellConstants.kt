package dev.serhiiyaremych.lumina.ui.components.focusedcell

import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * Polygon shapes for morphing animations in PhotoPreviewItem
 */
internal val START_SHAPE = RoundedPolygon(
    numVertices = 4,
    radius = 1f,
    rounding = CornerRounding(0.3f)
)

internal val END_SHAPE = RoundedPolygon(
    numVertices = 6,
    radius = 1f,
    rounding = CornerRounding(0.2f)
)

internal val SHAPE_MORPH = Morph(start = START_SHAPE, end = END_SHAPE)

/**
 * Responsive layout constants for FocusedCellPanel width behavior
 */
internal object ResponsiveLayoutConstants {
    /** Breakpoint for small to medium device transition (phones to small tablets) */
    const val SMALL_TO_MEDIUM_BREAKPOINT_DP = 600

    /** Breakpoint for medium to large device transition (small tablets to large tablets/desktop) */
    const val MEDIUM_TO_LARGE_BREAKPOINT_DP = 840

    /** Panel width percentage for small tablets (600-839dp width) */
    const val SMALL_TABLET_WIDTH_FRACTION = 0.7f

    /** Panel width percentage for large tablets/desktop (≥840dp width) */
    const val LARGE_TABLET_WIDTH_FRACTION = 0.5f
}
